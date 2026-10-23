import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AgentAdminService, AgentAuditEntry, AuditActionCount } from '../../services/agent-admin.service';

/**
 * Le journal (C8) — "qui a changé quoi, et pourquoi".
 *
 * The journal is the answer to the question every incident ends with. Two rules give
 * it its shape: the reason column is the *point* of the screen (a change without a
 * motive is what an audit is trying to find), and the filter values come from the
 * journal itself — an action code added tomorrow appears in the filter without the
 * page being edited.
 */
@Component({
  selector: 'app-admin-agent-journal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-agent-journal.html',
  styleUrl: './admin-agent-journal.css',
})
export class AdminAgentJournalComponent implements OnInit {
  entries: AgentAuditEntry[] = [];
  actions: AuditActionCount[] = [];
  loading = false;
  errorMsg = '';

  action = 'ALL';
  window = 200;
  /** Client-side search over the loaded window (actor, target, detail, reason). */
  query = '';

  constructor(private agent: AgentAdminService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void { this.load(); this.loadActions(); }

  load(): void {
    this.loading = true; this.errorMsg = '';
    this.agent.audit(this.window, this.action).subscribe({
      next: rows => { this.entries = rows; this.loading = false; this.cdr.detectChanges(); },
      error: err => {
        this.errorMsg = err?.error?.message || 'Impossible de lire le journal.';
        this.loading = false; this.cdr.detectChanges();
      },
    });
  }

  loadActions(): void {
    this.agent.auditActions().subscribe({
      next: rows => { this.actions = rows; this.cdr.detectChanges(); },
      error: () => { this.actions = []; this.cdr.detectChanges(); },
    });
  }

  setAction(action: string): void {
    this.action = action;
    this.load();
  }

  get visible(): AgentAuditEntry[] {
    const needle = this.query.trim().toLowerCase();
    if (!needle) { return this.entries; }
    return this.entries.filter(e =>
      [e.actor, e.target, e.detail, e.reason, e.action]
        .some(value => (value || '').toLowerCase().includes(needle)));
  }

  get totalShown(): number {
    return this.entries.length;
  }

  /** The code as a person reads it, falling back to the code itself. */
  actionLabel(action: string): string {
    switch (action) {
      case 'ENGINE_SET': return "Changement de moteur";
      case 'CONFIG_SET': return 'Modèles & clés';
      case 'CORPUS_REBUILD': return 'Reconstruction (agent interne)';
      case 'CORPUS_REBUILD_RAGLAB_DECLARED': return 'Reconstruction consignée (RAGLab)';
      case 'ENDPOINT_SET': return 'Adresse modifiée';
      case 'ENDPOINT_CLEARED': return 'Adresse oubliée';
      case 'CONSOLE_ANSWER': return 'Question testée';
      case 'CONSOLE_SEARCH': return 'Récupération testée';
      case 'CONSOLE_INDEX_PROBE': return 'Embedder vérifié en direct';
      default: return action;
    }
  }

  trackById(_index: number, entry: AgentAuditEntry): number {
    return entry.id;
  }

  /** Writes change something; reads only spend — the difference matters at audit time. */
  isWrite(action: string): boolean {
    return !action.startsWith('CONSOLE_');
  }

  actionClass(action: string): string {
    if (action === 'ENGINE_SET') { return 'info'; }
    return this.isWrite(action) ? 'warning' : 'neutral';
  }
}
