import { ChangeDetectorRef, Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';

/** One step of the agent pipeline as returned in `trace.steps`. */
export interface TraceStep {
  name: string;   // language | classify | classify_llm_fallback | route | retrieve | gate | llm_prompt
  ms: number;
  data: any;
}

/** One document the assistant consulted for an answer (admin only). */
export interface VisitedDocument {
  document: string | null;
  title: string | null;
  heading: string | null;
  chunkId: string | null;
  similarity: number | null;
  /** false = retrieval found it and the relevance threshold dropped it. */
  kept: boolean | null;
  /** true = the answer actually rested on it. */
  cited: boolean;
  engine: string | null;
  label: string;
}

export interface RealtimeTurn {
  id: number;
  userId: number | null;
  clientId: number | null;
  sessionId: string;
  question: string;
  answer: string | null;
  /** Document identifiers (ingestion file names), never S1/S2 citation labels. */
  sources: string[];
  /** Everything retrieval visited, cited or not — what an operator audits. */
  documents: VisitedDocument[];
  model: string;
  tokensIn: number;
  tokensOut: number;
  latencyMs: number;
  status: string;
  askedAt: string;
  answeredAt: string | null;
  intention: string | null;
  langue: string | null;
  domaine: string | null;
  grounded: boolean | null;
  hasTrace: boolean;
}

export interface TurnSummary {
  id: number;
  dateCreation: string;
  sessionId: string;
  question: string;
  reponse: string;
  intention: string | null;
  domaine: string | null;
  langue: string | null;
  grounded: boolean | null;
  sources: string[];
  hasTrace: boolean;
  /** Which engine answered: derived server-side from the intention. */
  engine: 'LEGACY' | 'RAGLAB';
}

/** A RAGLab turn has no pipeline: it has claims, each backed by a verbatim quote. */
export interface RagLabEvidence {
  source_id: string | null;
  quote: string | null;
}

export interface RagLabClaim {
  text: string | null;
  cited: boolean;
  evidence: RagLabEvidence[];
}

export interface RagLabSource {
  source_id: string | null;
  document: string | null;
  chunk_id: string | null;
  heading: string | null;
}

export interface RagLabTrace {
  engine?: string;
  status?: string;
  reason?: string;
  seconds?: number;
  cached?: boolean;
  inference_performed?: boolean;
  claims?: RagLabClaim[];
  sources?: RagLabSource[];
  uncited_source_ids?: string[];
}

/** One retrieved chunk, as the retrieval probe reports it. */
export interface RetrievalHit {
  sourceId?: string | null;
  document?: string | null;
  chunkId?: string | null;
  heading?: string | null;
  score?: number | null;
  excerpt?: string | null;
}

export interface ReasonCount {
  reason: string;
  count: number;
}

export interface EngineKpis {
  engine: 'LEGACY' | 'RAGLAB';
  label: string;
  turns: number;
  grounded: number;
  refused: number;
  groundingRate: number | null;
  withSources: number;
  sourcesRate: number | null;
  refusalReasons: ReasonCount[];
}

export interface KpisResponse {
  window: number;
  turns: number;
  engines: EngineKpis[];
}

/**
 * Monitoring page: what the assistant did on every turn, step by step.
 *
 * Left: recent exchanges. Right: the selected exchange's trace — detected
 * language, the exemplars the question was compared with (and how close),
 * the route that won, every chunk pgvector returned (similarity, kept or
 * dropped by the relevance threshold), the gate verdicts and the exact prompt
 * sent to the LLM. "Tester une question" runs the same pipeline live without
 * touching quotas or history.
 */
/** Rows the live feed keeps client-side; the backend caps its own window at 200. */
const LIVE_FEED_ROWS = 60;

@Component({
  selector: 'app-admin-monitoring',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin-monitoring.html',
  styleUrl: './admin-monitoring.css',
})
export class AdminMonitoring implements OnInit, OnDestroy {
  private api = environment.apiUrl + '/admin/monitoring';

  viewMode: 'live' | 'traces' = 'live';

  // Live feed state
  liveTurns: RealtimeTurn[] = [];
  selectedLiveTurn: RealtimeTurn | null = null;
  autoRefresh = true;
  /** Cadence of the live poll; a turn takes seconds, so this reads as instant. */
  readonly livePollMs = 2500;
  private refreshTimer: any = null;
  /** Newest asked_at the console has already seen — the cursor of the next poll. */
  private liveCursor: string | null = null;
  /** Rows that arrived since the last paint, so they can be highlighted. */
  readonly freshIds = new Set<number>();

  loading = false;
  errorMsg = '';
  turns: TurnSummary[] = [];
  selected: TurnSummary | null = null;
  steps: TraceStep[] = [];
  answerText = '';

  // Live test box
  testMessage = '';
  testLanguage = 'auto';
  testing = false;

  filter: 'all' | 'grounded' | 'refused' = 'all';
  /** Which engine's traffic to observe — the whole point of a two-engine edge. */
  engineFilter: 'ALL' | 'LEGACY' | 'RAGLAB' = 'ALL';
  kpis: KpisResponse | null = null;
  /** Populated for RAGLab turns (claims + quotes) instead of pipeline steps. */
  raglabTrace: RagLabTrace | null = null;
  /** Populated by the retrieval probe when RAGLab is serving. */
  retrievalHits: RetrievalHit[] = [];
  retrievalQuestion = '';

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.load();
    this.loadLiveFeed();
    this.setupAutoRefresh();
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  setupAutoRefresh(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
    if (this.autoRefresh) {
      this.refreshTimer = setInterval(() => {
        if (this.autoRefresh) {
          this.loadLiveFeed(true);
        }
      }, this.livePollMs);
    }
  }

  toggleAutoRefresh(): void {
    this.autoRefresh = !this.autoRefresh;
    this.setupAutoRefresh();
  }

  loadLiveFeed(silent = false): void {
    if (!silent) { this.loading = true; this.liveCursor = null; }
    // Ask only for what is newer than what we hold: the table grows, it never
    // resets under the operator, and an in-flight row updates in place.
    const cursor = this.liveCursor ? `&since=${encodeURIComponent(this.liveCursor)}` : '';
    this.http.get<RealtimeTurn[]>(`${this.api}/live-feed?limit=50${cursor}`).subscribe({
      next: rows => {
        this.mergeLiveFeed(rows);
        if (!silent) { this.loading = false; }
        this.cdr.detectChanges();
      },
      error: () => {
        if (!silent) { this.loading = false; }
        this.cdr.detectChanges();
      }
    });
  }

  private mergeLiveFeed(rows: RealtimeTurn[]): void {
    if (!rows?.length) { return; }
    const byId = new Map<number, RealtimeTurn>(this.liveTurns.map(t => [t.id, t]));
    for (const turn of rows) {
      if (!byId.has(turn.id)) { this.freshIds.add(turn.id); }
      byId.set(turn.id, turn);
    }
    this.liveTurns = [...byId.values()]
      .sort((a, b) => (b.askedAt ?? '').localeCompare(a.askedAt ?? ''))
      .slice(0, LIVE_FEED_ROWS);
    const newest = this.liveTurns[0]?.askedAt ?? null;
    if (newest && (!this.liveCursor || newest > this.liveCursor)) { this.liveCursor = newest; }
    // A row stops being "new" one animation later than it appeared.
    if (this.freshIds.size) {
      const ids = [...this.freshIds];
      setTimeout(() => { ids.forEach(id => this.freshIds.delete(id)); }, 1600);
    }
  }

  selectLiveTurn(turn: RealtimeTurn): void {
    // Keep the drawer on the newest version of the row, not the snapshot clicked.
    this.selectedLiveTurn = this.liveTurns.find(t => t.id === turn.id) ?? turn;
    this.cdr.detectChanges();
  }

  /** Documents the answer used first, then the ones retrieval consulted and dropped. */
  documentsOf(turn: RealtimeTurn | null): VisitedDocument[] {
    if (!turn) { return []; }
    if (turn.documents?.length) { return turn.documents; }
    return (turn.sources ?? []).map(document => ({
      document, title: null, heading: null, chunkId: null,
      similarity: null, kept: null, cited: true, engine: null, label: document
    }));
  }

  pctScore(value: number | null): string {
    return value === null || value === undefined ? '—' : Math.round(value * 100) + ' %';
  }

  load(): void {
    this.loading = true; this.errorMsg = '';
    this.http.get<TurnSummary[]>(`${this.api}/turns?limit=100&engine=${this.engineFilter}`).subscribe({
      next: rows => { this.turns = rows; this.selected = null; this.steps = []; this.raglabTrace = null;
                      this.loading = false; this.cdr.detectChanges(); },
      error: err => { this.errorMsg = err?.error?.message || 'Impossible de charger les échanges.'; this.loading = false; this.cdr.detectChanges(); },
    });
    // Per-engine numbers over the recent window: the point of a two-engine edge is
    // comparing them, so the strip shows both even when one engine filters the list.
    this.http.get<KpisResponse>(`${this.api}/kpis?window=200`).subscribe({
      next: kpis => { this.kpis = kpis; this.cdr.detectChanges(); },
      error: () => { this.kpis = null; this.cdr.detectChanges(); },
    });
  }

  setEngineFilter(engine: 'ALL' | 'LEGACY' | 'RAGLAB'): void {
    this.engineFilter = engine;
    this.load();
  }

  kpisFor(engine: string): EngineKpis | null {
    return this.kpis?.engines?.find(k => k.engine === engine) ?? null;
  }

  /** Percentages are absent when an engine served nothing — show a dash, not 0%. */
  pctOrDash(rate: number | null | undefined): string {
    return rate === null || rate === undefined ? '—' : Math.round(rate * 100) + ' %';
  }

  get visibleTurns(): TurnSummary[] {
    if (this.filter === 'grounded') { return this.turns.filter(t => t.grounded === true); }
    if (this.filter === 'refused') { return this.turns.filter(t => t.grounded === false); }
    return this.turns;
  }

  select(t: TurnSummary): void {
    this.selected = t; this.steps = []; this.raglabTrace = null; this.answerText = t.reponse;
    if (!t.hasTrace) { this.cdr.detectChanges(); return; }
    this.http.get<any>(`${this.api}/turns/${t.id}`).subscribe({
      next: res => {
        const trace = res?.trace ?? null;
        if (t.engine === 'RAGLAB') {
          // No pipeline for this engine: the trace *is* the claims and their quotes.
          this.raglabTrace = trace;
          this.steps = [];
        } else {
          this.steps = trace?.steps ?? [];
        }
        this.cdr.detectChanges();
      },
      error: () => { this.steps = []; this.raglabTrace = null; this.cdr.detectChanges(); },
    });
  }

  /** True when the detail pane shows a live RAGLab probe rather than a stored turn. */
  isRagLabProbe(): boolean {
    return this.selected?.engine === 'RAGLAB' && this.selected?.id === 0;
  }

  /** Claims whose evidence the service's gate could not check. */
  uncitedClaims(): number {
    return (this.raglabTrace?.claims ?? []).filter(c => !c.cited).length;
  }

  /** Retrieved sources no claim used: a confident-looking answer with thin support. */
  uncitedSources(): string[] {
    return this.raglabTrace?.uncited_source_ids ?? [];
  }

  runTest(): void {
    const msg = this.testMessage.trim();
    if (!msg || this.testing) { return; }
    this.testing = true; this.errorMsg = '';
    this.http.post<any>(`${this.api}/explain`, { message: msg, language: this.testLanguage }).subscribe({
      next: res => {
        const isRagLab = res?.engine === 'RAGLAB';
        this.selected = {
          id: 0, dateCreation: new Date().toISOString(), sessionId: 'test', question: msg,
          reponse: res.text ?? '', intention: res.structured?.intent ?? null,
          domaine: res.structured?.domain ?? (isRagLab ? 'RAGLAB' : null),
          langue: null, grounded: !!res.grounded,
          sources: res.structured?.citation_titles ?? res.structured?.citations
            ?? (res.retrieval?.hits ?? []).map((h: RetrievalHit) => h.sourceId).filter((x: any) => !!x),
          hasTrace: true,
          engine: isRagLab ? 'RAGLAB' : 'LEGACY',
        };
        this.answerText = res.text ?? '';
        this.steps = isRagLab ? [] : (res.trace?.steps ?? []);
        // The probe for this engine is retrieval, not a pipeline: show what it found.
        this.retrievalQuestion = isRagLab ? msg : '';
        this.retrievalHits = isRagLab ? (res.retrieval?.hits ?? []) : [];
        this.raglabTrace = null;
        this.testing = false; this.cdr.detectChanges();
      },
      error: err => { this.errorMsg = err?.error?.message || 'Le test a échoué (agent indisponible ?).'; this.testing = false; this.cdr.detectChanges(); },
    });
  }

  // ---- presentation helpers -------------------------------------------------

  stepTitle(s: TraceStep): string {
    switch (s.name) {
      case 'language': return '1 · Langue détectée';
      case 'classify': return '2 · Classification (embedding de la question vs exemples)';
      case 'classify_llm_fallback': return '2b · Arbitrage LLM (zone d\'ambiguïté)';
      case 'route': return '3 · Route choisie';
      case 'retrieve': return '4 · Recherche vectorielle (pgvector)';
      case 'gate': return '5 · Contrôles (gates)';
      case 'llm_prompt': return '6 · Prompt envoyé au LLM';
      default: return s.name;
    }
  }

  stepIcon(s: TraceStep): string {
    return ({ language: '🌐', classify: '🧭', classify_llm_fallback: '🤖', route: '🔀', retrieve: '📚', gate: '🚧', llm_prompt: '✍️' } as any)[s.name] ?? '•';
  }

  gateClass(data: any): string {
    const v = data?.verdict;
    if (v === 'passed' || v === 'smalltalk') { return 'ok'; }
    if (v === 'knowledge_gap' || v === 'blocked' || v === 'rejected') { return 'bad'; }
    return '';
  }

  pct(x: number | undefined | null): string {
    return x === undefined || x === null ? '–' : (x * 100).toFixed(0) + ' %';
  }

  barWidth(x: number | undefined | null): string {
    return Math.max(0, Math.min(100, (x ?? 0) * 100)).toFixed(0) + '%';
  }

  retrieveSteps(): TraceStep[] { return this.steps.filter(s => s.name === 'retrieve'); }

  keptCount(): number {
    return this.retrieveSteps().reduce((n, s) => n + (s.data?.candidates ?? []).filter((c: any) => c.kept).length, 0);
  }

  outcomeLabel(t: TurnSummary | null): string {
    if (!t) { return ''; }
    if (t.grounded === true) { return 'Réponse fondée sur des sources'; }
    if (t.grounded === false) { return 'Refus / hors périmètre / lacune'; }
    return 'Inconnu (ancien échange)';
  }
}
