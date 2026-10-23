import { Component, OnInit, effect, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RaglabService, RaglabDocItem, parseRaglabError, ParsedRaglabError } from '../../../services/raglab.service';
import { AuthService } from '../../../services/auth.service';

export interface UploadQueueItem {
  id: string;
  file: File;
  sizeBytes: number;
  uploadedBytes: number;
  status: 'queued' | 'uploading' | 'done' | 'failed';
  resultNote?: string;
  error?: string;
  indexImmediately: boolean;
}

@Component({
  selector: 'app-raglab-documents',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './raglab-documents.component.html',
  styleUrl: './raglab-documents.component.css'
})
export class RaglabDocumentsComponent implements OnInit {
  private cdr = inject(ChangeDetectorRef);
  public raglab = inject(RaglabService);
  public auth = inject(AuthService);

  get canManageRaglab(): boolean { return this.auth.hasPermission('RAGLAB_CONSOLE_MANAGE'); }
  /** Seeded ADMIN_DOCUMENTS keeps its historical feed access; new roles need RAGLab Manage. */
  private get isLegacyDocumentManager(): boolean {
    return this.auth.hasRole('ADMIN_DOCUMENTS') && this.auth.hasPermission('RAGLAB_CONSOLE_VIEW');
  }
  get canCreateDocuments(): boolean {
    return this.canManageRaglab || (this.isLegacyDocumentManager && this.auth.hasPermission('DOCUMENTS_CREATE'));
  }
  get canDeleteDocuments(): boolean {
    return this.canManageRaglab || (this.isLegacyDocumentManager && this.auth.hasPermission('DOCUMENTS_DELETE'));
  }
  get canProcessDocuments(): boolean {
    return this.canManageRaglab || (this.isLegacyDocumentManager && this.auth.hasPermission('DOCUMENTS_PROCESS'));
  }
  get hasDocumentActions(): boolean { return this.canCreateDocuments || this.canDeleteDocuments; }

  /** Track by identity: the list refreshes after every upload and would otherwise be
   *  destroyed and rebuilt, losing scroll position and focus. */
  trackDoc = (_: number, doc: RaglabDocItem): string => doc.id;
  trackUpload = (_: number, item: UploadQueueItem): string => item.id;

  documents: RaglabDocItem[] = [];
  documentsDir = '';
  indexedAt: string | null = null;
  loadingDocs = false;
  listError: ParsedRaglabError | null = null;

  // Upload queue
  uploadQueue: UploadQueueItem[] = [];
  isUploading = false;
  indexAfterUpload = false;

  // Delete modal
  deletingDocId: string | null = null;
  isDeleting = false;
  deleteError: string | null = null;

  // Ingest actions
  isStartingIngest = false;
  ingestMessage: string | null = null;
  ingestError: string | null = null;

  // Drag and drop
  isDragOver = false;

  private lastIngestState?: string;

  constructor() {
    // Automatically adjust UI when background ingestion completes without requiring manual refresh
    effect(() => {
      const h = this.raglab.health();
      const currentState = h?.ingest?.state;
      if (this.lastIngestState === 'running' && (currentState === 'idle' || currentState === 'done')) {
        this.loadDocuments();
      }
      this.lastIngestState = currentState;
      this.cdr.markForCheck();
    });
  }

  ngOnInit(): void {
    this.loadDocuments();
  }

  loadDocuments(): void {
    this.loadingDocs = true;
    this.listError = null;
    this.cdr.markForCheck();
    this.raglab.getDocuments().subscribe({
      next: (res) => {
        this.loadingDocs = false;
        this.documents = res.documents || [];
        this.documentsDir = res.documents_dir || '';
        this.indexedAt = res.index?.indexed_at || null;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.loadingDocs = false;
        this.listError = parseRaglabError(err);
        this.cdr.markForCheck();
      }
    });
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.enqueueFiles(Array.from(input.files));
      input.value = '';
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
    if (event.dataTransfer?.files) {
      this.enqueueFiles(Array.from(event.dataTransfer.files));
    }
  }

  private sanitizeId(filename: string): string {
    const base = filename.replace(/\.[^/.]+$/, '');
    const clean = base.replace(/[^A-Za-z0-9._-]/g, '_');
    return clean.slice(0, 80) || 'doc_' + Date.now();
  }

  enqueueFiles(files: File[]): void {
    if (!this.canCreateDocuments) return;
    const validExts = ['.txt', '.md', '.pdf', '.docx'];
    for (const f of files) {
      const lower = f.name.toLowerCase();
      if (!validExts.some(ext => lower.endsWith(ext))) {
        continue;
      }
      this.uploadQueue.push({
        id: this.sanitizeId(f.name),
        file: f,
        sizeBytes: f.size,
        uploadedBytes: 0,
        status: 'queued',
        indexImmediately: this.indexAfterUpload && this.canProcessDocuments
      });
    }
    this.processQueue();
  }

  retryFailed(item: UploadQueueItem): void {
    if (!this.canCreateDocuments) return;
    item.status = 'queued';
    item.error = undefined;
    this.processQueue();
  }

  clearCompleted(): void {
    this.uploadQueue = this.uploadQueue.filter(i => i.status === 'uploading' || i.status === 'queued');
  }

  get aggregateProgress(): { total: number; done: number; percent: number } {
    const total = this.uploadQueue.reduce((acc, cur) => acc + cur.sizeBytes, 0);
    const done = this.uploadQueue.reduce((acc, cur) => {
      if (cur.status === 'done') return acc + cur.sizeBytes;
      if (cur.status === 'uploading') return acc + cur.uploadedBytes;
      return acc;
    }, 0);
    const percent = total > 0 ? Math.round((done / total) * 100) : 0;
    return { total, done, percent };
  }

  private processQueue(): void {
    const concurrency = 3;
    const active = this.uploadQueue.filter(i => i.status === 'uploading').length;
    const nextSlot = concurrency - active;

    if (nextSlot <= 0) return;

    const pending = this.uploadQueue.filter(i => i.status === 'queued').slice(0, nextSlot);
    if (pending.length === 0) {
      if (active === 0 && this.uploadQueue.some(i => i.status === 'done')) {
        this.loadDocuments();
      }
      return;
    }

    for (const item of pending) {
      item.status = 'uploading';
      item.uploadedBytes = Math.round(item.sizeBytes * 0.2); // initial visual hint

      this.raglab.pushDocumentUpload(item.file, item.id, item.indexImmediately).subscribe({
        next: (res: any) => {
          item.uploadedBytes = item.sizeBytes;
          item.status = 'done';
          const r = res?.result || 'envoyé';
          const v = res?.document?.version ? `v${res.document.version}` : '';
          item.resultNote = `${r} ${v}`.trim();
          this.processQueue();
        },
        error: (err) => {
          item.status = 'failed';
          const parsed = parseRaglabError(err);
          item.error = parsed.message;
          this.processQueue();
        }
      });
    }
  }

  onDeletePrompt(doc: RaglabDocItem): void {
    if (!this.canDeleteDocuments) return;
    this.deletingDocId = doc.id;
    this.deleteError = null;
  }

  confirmDelete(): void {
    if (!this.deletingDocId || !this.canDeleteDocuments) return;
    this.isDeleting = true;
    this.deleteError = null;

    this.raglab.deleteDocument(this.deletingDocId).subscribe({
      next: () => {
        this.isDeleting = false;
        this.deletingDocId = null;
        this.loadDocuments();
      },
      error: (err) => {
        this.isDeleting = false;
        this.deleteError = parseRaglabError(err).message;
      }
    });
  }

  cancelDelete(): void {
    this.deletingDocId = null;
    this.deleteError = null;
  }

  startIngest(reset = false): void {
    if (!this.canProcessDocuments || this.raglab.isSealed()) return;
    this.isStartingIngest = true;
    this.ingestMessage = null;
    this.ingestError = null;

    this.raglab.ingest(reset).subscribe({
      next: () => {
        this.isStartingIngest = false;
        this.ingestMessage = reset
          ? 'Réinitialisation et reconstruction de l\'index lancée.'
          : 'Reconstruction incrémentale de l\'index lancée.';
      },
      error: (err) => {
        this.isStartingIngest = false;
        this.ingestError = parseRaglabError(err).message;
      }
    });
  }

  formatBytes(bytes?: number): string {
    if (!bytes && bytes !== 0) return '—';
    if (bytes < 1024) return bytes + ' o';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' Ko';
    return (bytes / (1024 * 1024)).toFixed(2) + ' Mo';
  }
}
