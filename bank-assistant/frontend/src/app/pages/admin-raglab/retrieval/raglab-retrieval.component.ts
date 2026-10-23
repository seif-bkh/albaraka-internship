import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  RaglabService,
  RaglabSearchResponse,
  RaglabInspectResponse,
  RaglabChunksSearchResponse,
  parseRaglabError,
  ParsedRaglabError
} from '../../../services/raglab.service';
import { PiiFormatPipe } from '../../../pipes/pii-format.pipe';
import { AuthService } from '../../../services/auth.service';

type RetrievalTab = 'search' | 'corpus' | 'quote';

@Component({
  selector: 'app-raglab-retrieval',
  standalone: true,
  imports: [CommonModule, FormsModule, PiiFormatPipe],
  templateUrl: './raglab-retrieval.component.html',
  styleUrl: './raglab-retrieval.component.css'
})
export class RaglabRetrievalComponent implements OnInit {
  activeTab: RetrievalTab = 'search';

  // 1. Search tab
  question = '';
  k = 5;
  mode = 'vector';
  langFilter = '';
  neighborRadius = 0;
  searchLoading = false;
  /** Search hits and corpus rows keep their identity across re-runs, so only changed
   *  rows are re-rendered. */
  trackHit = (index: number, hit: { chunk_index?: number; source?: string }): string =>
    `${hit.source ?? ''}#${hit.chunk_index ?? index}`;
  trackRow = (index: number, row: { source?: string; id?: string }): string =>
    row.id ?? row.source ?? String(index);
  trackIndex = (index: number): number => index;

  searchResult: RaglabSearchResponse | null = null;
  searchError: ParsedRaglabError | null = null;

  // 2. Corpus tab
  inspectLimit = 10;
  inspectLoading = false;
  inspectResult: RaglabInspectResponse | null = null;
  inspectError: ParsedRaglabError | null = null;

  // 3. Quote check tab
  quoteInput = '';
  quoteLoading = false;
  quoteResult: RaglabChunksSearchResponse | null = null;
  quoteError: ParsedRaglabError | null = null;

  constructor(public raglab: RaglabService, public auth: AuthService) {}

  get canRun(): boolean { return this.auth.hasPermission('RAGLAB_CONSOLE_RUN'); }
  get canView(): boolean { return this.auth.hasPermission('RAGLAB_CONSOLE_VIEW'); }

  ngOnInit(): void {
    // nothing auto-fetched until tab clicked
  }

  setTab(tab: RetrievalTab): void {
    this.activeTab = tab;
    if (tab === 'corpus' && !this.inspectResult && !this.inspectLoading) {
      this.loadInspect();
    }
  }

  onSearch(): void {
    if (!this.canRun) return;
    const q = this.question.trim();
    if (!q || this.raglab.isSealed()) return;

    this.searchLoading = true;
    this.searchError = null;
    this.searchResult = null;

    this.raglab.search({
      question: q,
      k: this.k,
      mode: this.mode || null,
      lang_filter: this.langFilter || null,
      neighbor_radius: this.neighborRadius
    }).subscribe({
      next: (res) => {
        this.searchLoading = false;
        this.searchResult = res;
      },
      error: (err) => {
        this.searchLoading = false;
        this.searchError = parseRaglabError(err);
      }
    });
  }

  loadInspect(): void {
    this.inspectLoading = true;
    this.inspectError = null;

    this.raglab.inspect(this.inspectLimit).subscribe({
      next: (res) => {
        this.inspectLoading = false;
        this.inspectResult = res;
      },
      error: (err) => {
        this.inspectLoading = false;
        this.inspectError = parseRaglabError(err);
      }
    });
  }

  onQuoteCheck(): void {
    if (!this.canView) return;
    const text = this.quoteInput.trim();
    if (!text) return;

    this.quoteLoading = true;
    this.quoteError = null;
    this.quoteResult = null;

    this.raglab.searchChunks(text).subscribe({
      next: (res) => {
        this.quoteLoading = false;
        this.quoteResult = res;
      },
      error: (err) => {
        this.quoteLoading = false;
        this.quoteError = parseRaglabError(err);
      }
    });
  }

  maxHistogramBucket(buckets: { bucket: string; chunks: number }[]): number {
    return Math.max(1, ...buckets.map(b => b.chunks));
  }
}
