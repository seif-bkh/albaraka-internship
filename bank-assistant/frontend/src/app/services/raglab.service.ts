import { Injectable, signal, computed, inject, NgZone, OnDestroy } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, Subscription, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';

export interface RaglabProfile {
  version?: number;
  embedding: { provider: string; model: string };
  answer: { provider: string; model: string };
  chunking: {
    mode: 'restructure' | 'size' | 'manual';
    size?: number;
    overlap?: number;
  };
  retrieval: {
    top_k: number;
    mode: 'vector' | 'rrf' | 'blend';
    lang_filter?: 'ar' | 'fr' | 'en' | null;
    neighbor_radius?: number;
  };
  data_dirs?: string[];
  pipeline?: string;
  custom_models?: Record<string, unknown>;
}

export interface RaglabIndexInfo {
  collection: string;
  count: number;
  chunk_fp?: string;
  tokenizer_match?: boolean;
}

export interface RaglabIngestInfo {
  state: 'idle' | 'running' | 'done' | 'error';
  started_at?: string;
  finished_at?: string;
  collection?: string;
  stored?: number;
  error?: string | null;
}

export interface RaglabHealth {
  status: 'ok' | 'down' | string;
  version?: string;
  profile?: RaglabProfile;
  index?: RaglabIndexInfo;
  keys?: Record<string, string>;
  ingest?: RaglabIngestInfo;
  error?: string | null;
  error_reason?: string | null;
  latency_ms?: number;
}

export interface RaglabProviderInfo {
  provider: string;
  label: string;
  key_envs: string[];
  key_set: boolean;
  models: string[];
}

export interface RaglabModelsResponse {
  embedding: RaglabProviderInfo[];
  answer: RaglabProviderInfo[];
  note?: string;
}

export interface RaglabProfileResponse {
  profile: RaglabProfile;
  collection: string;
  pipeline?: string;
  switching?: string;
  index_note?: string;
  notes?: string[];
}

export interface RaglabKeyItem {
  env: string;
  description: string;
  status: 'set' | 'missing';
  masked: string | null;
}

export interface RaglabKeysResponse {
  keys: RaglabKeyItem[];
}

export interface RaglabClaimEvidence {
  source_id: string;
  quote: string;
}

export interface RaglabClaim {
  text: string;
  evidence: RaglabClaimEvidence[];
}

export interface RaglabSource {
  source_id: string;
  document: string;
  chunk_id?: string;
  heading?: string;
  text?: string;
}

export interface RaglabAnswerResponse {
  status: 'answered' | 'refused' | 'greeting' | string;
  reason?: string;
  answer: string;
  claims?: RaglabClaim[];
  sources?: RaglabSource[];
  model?: string;
  language?: string;
  validation_ok?: boolean;
  cached?: boolean;
  retrieved?: number;
  dropped_for_budget?: number;
  seconds?: number;
  inference_performed?: boolean;
  error?: string | null;
  raw_preview?: string | null;
}

export interface RaglabHit {
  rank: number;
  id: string;
  similarity: number;
  language: string;
  heading: string;
  source: string;
  chunk_index: number;
  text: string;
}

export interface RaglabSearchResponse {
  question: string;
  language: string;
  k: number;
  mode: string;
  embedder?: { provider: string; model: string };
  variants?: { label: string; text: string }[];
  hits: RaglabHit[];
}

export interface RaglabInspectDoc {
  name: string;
  language: string;
  chars: number;
}

export interface RaglabInspectHistogramBucket {
  bucket: string;
  chunks: number;
}

export interface RaglabInspectChunk {
  index: number;
  source: string;
  language: string;
  tokens: number;
  section: string;
  heading: string;
  text: string;
}

export interface RaglabInspectResponse {
  documents: RaglabInspectDoc[];
  chunking: { mode: string; size?: number; overlap?: number };
  chunks: {
    count: number;
    tokens_total: number;
    tokens_min: number;
    tokens_max: number;
    tokens_median: number;
    tokens_mean: number;
    histogram: RaglabInspectHistogramBucket[];
  };
  sample: RaglabInspectChunk[];
}

export interface RaglabChunkMatchRef {
  source: string;
  chunk_index: number;
  heading: string;
}

export interface RaglabChunksSearchResponse {
  description: string;
  needle: string;
  full: RaglabChunkMatchRef[];
  head: RaglabChunkMatchRef[];
  tail: RaglabChunkMatchRef[];
  first_full_text?: string;
}

export interface RaglabSanityCosine {
  pair: string;
  cosine: number;
}

export interface RaglabSanityResponse {
  provider: string;
  model: string;
  dimension: number;
  batch_size: number;
  cache_entries: number;
  api_calls: number;
  phrases: string[];
  cosines: RaglabSanityCosine[];
  interpretation: string;
}

export interface RaglabDocItem {
  id: string;
  filename: string;
  stored_as?: string;
  bytes: number;
  sha256_16?: string;
  version: number;
  received_at?: string;
  updated_at?: string;
  status: 'pending' | 'indexing' | 'indexed' | 'stale';
  chunks_in_index?: number;
}

export interface RaglabDocumentsResponse {
  documents: RaglabDocItem[];
  documents_dir: string;
  index?: { collection: string; indexed_at: string };
}

export interface RaglabEvaluateQuestionRow {
  id: string;
  language: string;
  category: string;
  hit_at_1?: boolean;
  hit_at_3?: boolean;
  hit_at_5?: boolean;
  is_out_of_scope?: boolean;
}

export interface RaglabEvaluateResponse {
  metrics: {
    overall: Record<string, number>;
    by_category?: Record<string, Record<string, number>>;
    by_language?: Record<string, Record<string, number>>;
    separation?: unknown;
    out_of_scope?: unknown;
  };
  config?: unknown;
  questions: RaglabEvaluateQuestionRow[];
  saved_to?: string;
  note?: string;
}

export interface RaglabHarnessResponse {
  exit_code: number;
  output_tail: string[];
  report: string | null;
}

export interface ParsedRaglabError {
  reason: string;
  message: string;
  hint?: string;
  slot?: string;
  keyEnv?: string;
}

export function parseRaglabError(err: unknown): ParsedRaglabError {
  if (err instanceof HttpErrorResponse) {
    const body = err.error;
    if (body) {
      if (body.detail) {
        if (typeof body.detail === 'object' && !Array.isArray(body.detail)) {
          return {
            reason: body.detail.reason || `http_${err.status}`,
            message: body.detail.message || body.detail.hint || body.detail.reason || err.message,
            hint: body.detail.hint,
            slot: body.detail.slot,
            keyEnv: body.detail.key_env,
          };
        }
        if (Array.isArray(body.detail)) {
          const first = body.detail[0];
          return {
            reason: 'validation_error',
            message: first?.msg ? `${first.loc?.join('.') || 'Champ'}: ${first.msg}` : 'Erreur de validation',
          };
        }
        if (typeof body.detail === 'string') {
          return { reason: 'error', message: body.detail };
        }
      }
      if (body.message) {
        return { reason: body.reason || 'error', message: body.message };
      }
    }
    return { reason: `http_${err.status}`, message: `Erreur HTTP ${err.status}: ${err.statusText || 'Échec de la requête'}` };
  }
  return { reason: 'unknown', message: err instanceof Error ? err.message : 'Une erreur inattendue est survenue' };
}

@Injectable({ providedIn: 'root' })
export class RaglabService implements OnDestroy {
  private readonly base = '/api/admin/raglab';

  // Live state signal derived from /health
  readonly health = signal<RaglabHealth | null>(null);
  readonly isLoadingHealth = signal<boolean>(false);

  // Derived state signals for the entire UI
  readonly isSealed = computed(() => this.health()?.ingest?.state === 'running');
  readonly isStale = computed(() => this.health()?.index?.tokenizer_match === false);
  readonly isEmptyIndex = computed(() => (this.health()?.index?.count ?? 0) === 0);
  readonly isConnected = computed(() => this.health()?.status === 'ok');

  readonly missingKeys = computed(() => {
    const keysObj = this.health()?.keys;
    if (!keysObj) return [];
    return Object.entries(keysObj)
      .filter(([_, status]) => status === 'missing')
      .map(([env]) => env);
  });

  readonly isPinnedPipeline = computed(() => {
    const p = this.health()?.profile?.pipeline || '';
    return p.includes('supported pipeline');
  });

  /** When /health last came back (success or failure) — shown as "vérifié à 12:04:31". */
  readonly lastCheckedAt = signal<Date | null>(null);

  private readonly zone = inject(NgZone);
  private pollTimer?: ReturnType<typeof setTimeout>;
  private pollSub?: Subscription;
  private polling = false;
  /** A slow backend must not queue requests: at most one /health is in flight. */
  private healthInFlight = false;

  constructor(private http: HttpClient) {
    // Nothing is polled here on purpose. This service is `providedIn: 'root'`, so a
    // timer started in the constructor would keep hitting /health on the landing page,
    // in the client portal, on every tab, forever — 28 800 requests a day per open
    // window, each one triggering app-wide change detection through zone.js. Only the
    // console asks for polling (see AdminRaglabComponent).
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  /** Begin polling. Idempotent; called by the console shell when it mounts. */
  startPolling(): void {
    if (this.polling) return;
    this.polling = true;
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    this.poll();
  }

  /** Stop polling and clear the pending timer. Called when the console unmounts. */
  stopPolling(): void {
    this.polling = false;
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
      this.pollTimer = undefined;
    }
    this.pollSub?.unsubscribe();
    this.pollSub = undefined;
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
  }

  /**
   * How long to wait before the next /health: an ingest in flight is the only reason to
   * watch closely (2 s), a healthy idle service needs a glance every 20 s, and a
   * service that is down is retried every 60 s — hammering a dead endpoint neither
   * recovers it nor helps the operator.
   */
  private nextDelayMs(): number {
    if (this.isSealed()) return 2_000;
    if (this.health()?.status === 'down' || this.health() === null) return 60_000;
    return 20_000;
  }

  private poll(): void {
    if (!this.polling) return;
    // The next delay depends on what this response says (sealed, down, idle), so it is
    // scheduled when the response settles — not when the request is sent.
    this.refreshHealth();
  }

  private scheduleNext(): void {
    if (!this.polling) return;
    if (this.pollTimer) clearTimeout(this.pollTimer);
    // Outside Angular: a timer tick that only moves a status pill must not run change
    // detection across the whole application.
    this.zone.runOutsideAngular(() => {
      this.pollTimer = setTimeout(() => {
        if (document.hidden) {
          // Hidden tab: do not spend the bank's requests on a screen nobody is looking at.
          this.scheduleNext();
          return;
        }
        this.poll();
      }, this.nextDelayMs());
    });
  }

  /** Coming back to the tab is exactly when the operator wants a fresh reading. */
  private readonly onVisibilityChange = (): void => {
    if (!document.hidden && this.polling) {
      this.poll();
    }
  };

  refreshHealth(): void {
    if (this.healthInFlight) return;
    this.healthInFlight = true;
    this.pollSub?.unsubscribe();
    this.pollSub = this.getHealth().subscribe({
      next: () => { this.healthInFlight = false; this.scheduleNext(); },
      error: () => { this.healthInFlight = false; this.scheduleNext(); },
    });
  }

  getHealth(): Observable<RaglabHealth> {
    this.isLoadingHealth.set(true);
    return this.http.get<RaglabHealth>(`${this.base}/health`).pipe(
      tap((h) => {
        this.health.set(h);
        this.isLoadingHealth.set(false);
        this.lastCheckedAt.set(new Date());
      }),
      catchError((err) => {
        this.isLoadingHealth.set(false);
        const fallback: RaglabHealth = {
          status: 'down',
          error: 'Impossible de joindre le service RAGLab.',
          error_reason: 'unreachable',
        };
        this.health.set(fallback);
        this.lastCheckedAt.set(new Date());
        return of(fallback);
      })
    );
  }

  getModels(): Observable<RaglabModelsResponse> {
    return this.http.get<RaglabModelsResponse>(`${this.base}/models`);
  }

  getProfile(): Observable<RaglabProfileResponse> {
    return this.http.get<RaglabProfileResponse>(`${this.base}/profile`);
  }

  updateProfile(body: Partial<RaglabProfile>): Observable<RaglabProfileResponse> {
    return this.http.post<RaglabProfileResponse>(`${this.base}/profile`, body).pipe(
      tap(() => this.refreshHealth())
    );
  }

  getKeys(): Observable<RaglabKeysResponse> {
    return this.http.get<RaglabKeysResponse>(`${this.base}/keys`);
  }

  setKey(keyEnv: string, value: string, persist = false): Observable<unknown> {
    return this.http.post(`${this.base}/keys`, { key_env: keyEnv, value, persist }).pipe(
      tap(() => this.refreshHealth())
    );
  }

  deleteKey(env: string, persist = false): Observable<unknown> {
    return this.http.delete(`${this.base}/keys/${encodeURIComponent(env)}?persist=${persist}`).pipe(
      tap(() => this.refreshHealth())
    );
  }

  inspect(limit = 5): Observable<RaglabInspectResponse> {
    return this.http.get<RaglabInspectResponse>(`${this.base}/inspect?limit=${limit}`);
  }

  searchChunks(text: string, limit?: number): Observable<RaglabChunksSearchResponse> {
    return this.http.post<RaglabChunksSearchResponse>(`${this.base}/chunks/search`, { text, limit });
  }

  search(query: {
    question: string;
    k?: number | null;
    mode?: string | null;
    lang_filter?: string | null;
    neighbor_radius?: number | null;
    query_lang?: string | null;
  }): Observable<RaglabSearchResponse> {
    return this.http.post<RaglabSearchResponse>(`${this.base}/search`, query);
  }

  answer(query: {
    question: string;
    k?: number | null;
    include_excerpts?: boolean;
    query_lang?: string | null;
    mode?: string | null;
    lang_filter?: string | null;
  }): Observable<RaglabAnswerResponse> {
    return this.http.post<RaglabAnswerResponse>(`${this.base}/answer`, query);
  }

  embeddingsSanity(): Observable<RaglabSanityResponse> {
    return this.http.post<RaglabSanityResponse>(`${this.base}/embeddings/sanity`, {});
  }

  ingest(reset = false): Observable<unknown> {
    return this.http.post(`${this.base}/ingest?reset=${reset}`, {}).pipe(
      tap(() => this.refreshHealth())
    );
  }

  getIngestStatus(): Observable<RaglabIngestInfo> {
    return this.http.get<RaglabIngestInfo>(`${this.base}/ingest/status`);
  }

  getDocuments(): Observable<RaglabDocumentsResponse> {
    return this.http.get<RaglabDocumentsResponse>(`${this.base}/documents`);
  }

  getDocument(id: string): Observable<RaglabDocItem> {
    return this.http.get<RaglabDocItem>(`${this.base}/documents/${encodeURIComponent(id)}`);
  }

  pushDocumentJson(doc: {
    id?: string;
    filename: string;
    content: string;
    content_encoding: 'text' | 'base64';
  }, index = false): Observable<unknown> {
    const q = doc.id ? `?id=${encodeURIComponent(doc.id)}&index=${index}` : `?index=${index}`;
    return this.http.post(`${this.base}/documents${q}`, doc).pipe(
      tap(() => this.refreshHealth())
    );
  }

  pushDocumentUpload(file: File, id?: string, index = false): Observable<unknown> {
    const formData = new FormData();
    formData.append('file', file);
    if (id) formData.append('id', id);
    formData.append('index', String(index));
    return this.http.post(`${this.base}/documents/upload`, formData).pipe(
      tap(() => this.refreshHealth())
    );
  }

  deleteDocument(id: string): Observable<unknown> {
    return this.http.delete(`${this.base}/documents/${encodeURIComponent(id)}`).pipe(
      tap(() => this.refreshHealth())
    );
  }

  evaluate(body: { questions: string; top_k?: number }): Observable<RaglabEvaluateResponse> {
    return this.http.post<RaglabEvaluateResponse>(`${this.base}/evaluate`, body);
  }

  diagnosticsHarness50(): Observable<RaglabHarnessResponse> {
    return this.http.post<RaglabHarnessResponse>(`${this.base}/diagnostics/harness50`, {});
  }

  diagnosticsCatalog(): Observable<unknown> {
    return this.http.post(`${this.base}/diagnostics/catalog`, {});
  }
}
