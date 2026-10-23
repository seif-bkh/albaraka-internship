import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Client for the assistant administration APIs (/api/admin/agent/**).
 *
 * Slice 0 is the connection itself: which address the assistant calls, whether
 * it answers, and the journal of who changed it.
 */

/** Where the effective address comes from. */
export type EndpointSource = 'DATABASE' | 'ENV' | 'NONE';

export interface AgentEndpoint {
  url: string | null;
  /** host:port, ready to display (e.g. 10.20.30.40:8000). */
  hostPort: string | null;
  source: EndpointSource;
  /** The value the deployment shipped (RAGLAB_URL), even when overridden. */
  deployedUrl: string | null;
  configured: boolean;
  updatedBy: string | null;
  updatedAt: string | null;
  /** True for SUPER_ADMIN: the only role allowed to change the address. */
  editable: boolean;
}

export interface AgentDiagnostics {
  endpoint: AgentEndpoint;
  reachable: boolean;
  httpStatus: number | null;
  latencyMs: number;
  error: string | null;
  errorReason: string | null;
  /** Raw /health payload, shown as-is so an unknown shape is still readable. */
  health: unknown | null;
}

export interface AgentAuditEntry {
  id: number;
  action: string;
  target: string | null;
  detail: string | null;
  reason: string | null;
  actor: string | null;
  outcome: string;
  createdAt: string;
}

export interface AuditActionCount {
  action: string;
  count: number;
}

/* ------------------------------------------------------------ engine choice */

export type EngineProvider = 'LEGACY' | 'RAGLAB';

export interface AgentEngineState {
  provider: EngineProvider;
  /** ENV = the deployed default; DATABASE = an admin chose it here. */
  source: 'ENV' | 'DATABASE';
  defaultProvider: string;
  updatedBy: string | null;
  updatedAt: string | null;
  /** False = RAGLab has no address yet, so it cannot serve. */
  raglabConfigured: boolean;
}

export interface AgentEngineStatus {
  provider: string;
  label: string;
  url: string | null;
  configured: boolean;
  reachable: boolean;
  httpStatus: number | null;
  latencyMs: number;
  detail: string | null;
  serving: boolean;
}

/* ---------------------------------------------------------------- workbench */

/** One verbatim quote behind a claim. Only this part is gate-checked. */
export interface ClaimEvidence {
  sourceId: string | null;
  quote: string | null;
}

export interface AnswerClaim {
  text: string | null;
  evidence: ClaimEvidence[];
}

export interface AnswerSource {
  sourceId: string | null;
  document: string | null;
  chunkId: string | null;
  heading: string | null;
}

/** The service's own status vocabulary. */
export type AnswerStatus = 'answered' | 'refused' | 'greeting' | string;

export interface AnswerView {
  status: AnswerStatus | null;
  reason: string | null;
  answerText: string | null;
  claims: AnswerClaim[];
  sources: AnswerSource[];
  cached: boolean | null;
  inferencePerformed: boolean | null;
  seconds: number | null;
  citedClaims: number;
  uncitedClaims: number;
  /** Retrieved sources no claim used — the "why does this feel thin?" signal. */
  uncitedSourceIds: string[];
}

export interface AnswerResult {
  view: AnswerView | null;
  latencyMs: number;
  raw: unknown;
}

export interface RetrievalHit {
  sourceId: string | null;
  document: string | null;
  chunkId: string | null;
  heading: string | null;
  score: number | null;
  excerpt: string | null;
}

export interface RetrievalView {
  question: string | null;
  hits: RetrievalHit[];
  reason: string | null;
}

export interface RetrievalResult {
  view: RetrievalView | null;
  latencyMs: number;
  raw: unknown;
}

export interface ChunkMatch {
  document: string | null;
  chunkId: string | null;
  heading: string | null;
  text: string | null;
  offset: number | null;
  position: number | null;
}

export interface ChunkLookupResult {
  view: { query: string | null; matches: ChunkMatch[]; total: number | null } | null;
  latencyMs: number;
  raw: unknown;
}

/** Only the fields the operator set are sent; the service keeps its defaults. */
export interface RetrievalQuery {
  question: string;
  k?: number | null;
  mode?: string | null;
  langFilter?: string | null;
  neighborRadius?: number | null;
}

export interface AnswerQuery extends RetrievalQuery {
  includeExcerpts?: boolean | null;
  queryLang?: string | null;
}

/* ------------------------------------------------------------------ corpus & index */

/** One published file: the shared world both engines read. */
export interface CorpusFile {
  id: number;
  name: string;
  type: string | null;
  sizeBytes: number | null;
  status: string | null;          // VALIDE | ARCHIVE
  indexStatus: string | null;     // EN_ATTENTE | EN_COURS | INDEXE | ERREUR
  indexError: string | null;
  version: string | null;
  language: string | null;
  depositedAt: string | null;
}

export interface SharedCorpus {
  total: number;
  published: number;
  archived: number;
  indexed: number;
  indexing: number;
  failed: number;
  files: CorpusFile[];
}

/** One ingestion run, straight from the worker's journal. */
export interface IngestionRun {
  journalId: number;
  documentId: number | null;
  nomFichier: string | null;
  statutIndexation: string | null;
  statutJournal: string | null;
  nombreChunks: number | null;
  dureeMs: number | null;
  messageErreur: string | null;
  dateDebut: string | null;
  dateFin: string | null;
}

/** The in-house engine's world: CURRENT | STALE | RUNNING | DEGRADED | EMPTY. */
export interface LegacyIndex {
  state: string;
  detail: string;
  documents: number;
  indexed: number;
  indexing: number;
  failed: number;
  lastEventAt: string | null;
  recentRuns: IngestionRun[];
}

/** The external engine's world, as that service reports it. Facts are optional. */
export interface RagLabIndex {
  configured: boolean;
  reachable: boolean;
  detail: string;
  reportedBy: string | null;
  documents: number | null;
  chunks: number | null;
  embeddings: number | null;
  embeddingModel: string | null;
  indexedAt: string | null;
  rebuiltManually: boolean;
  declaredBy: string | null;
  declaredAt: string | null;
  declaredReason: string | null;
  raw: Record<string, unknown>;
}

export interface AgentCorpus {
  shared: SharedCorpus;
  legacy: LegacyIndex;
  raglab: RagLabIndex;
}

export interface RebuildResult {
  engine: string;
  accepted: number;
  acceptedIds: number[];
  skipped: { id: number; name: string; why: string }[];
  message: string;
  journalled: string;
}

/* ------------------------------------------------------- index coherence (entry 6) */

/** Is the index we search the one we built? `verdict` is the machine-readable answer. */
export interface IndexSanity {
  verdict: 'ok' | 'empty' | 'dimension_mismatch' | 'missing' | 'unknown';
  detail: string;
  collection: string | null;
  rows: number | null;
  storedDimension: number | null;
  configuredModel: string | null;
  configuredDimension: number | null;
  matches: boolean | null;
  probed: boolean;
  probeDimension: number | null;
  probeOk: boolean | null;
  probeDetail: string | null;
}

/* ------------------------------------------------------------------ models & keys */

/** One setting as the agent reports it: value, provenance, and what applies when. */
export interface ConfigField {
  value?: string | null;
  configured?: boolean;
  fingerprint?: string | null;
  source: 'override' | 'environment' | 'default';
  appliesOnRestart: boolean;
}

/** The in-house agent's own view of its configuration (never carries a key). */
export interface AgentConfigView {
  editable: Record<string, ConfigField>;
  readOnly: Record<string, { value: string | number | null }>;
  configFile: string;
  storedOverrides: string[];
  updatedAt: string | null;
  updatedBy: string | null;
  startedWith: string | null;
  /** True when an embedding-bound change is stored but not yet in effect. */
  restartPending: boolean;
  editableNames: string[];
}

export interface EngineConfigLegacy {
  url: string;
  reachable: boolean;
  error: string | null;
  errorReason: string | null;
  restartPending: boolean;
  view: AgentConfigView | null;
}

/** RAGLab's settings are changed out of band; this is only what it reports. */
export interface EngineConfigRagLab {
  configured: boolean;
  reachable: boolean;
  url: string | null;
  detail: string;
  chatModel: string | null;
  embeddingModel: string | null;
  embeddingDim: string | null;
  raw: unknown | null;
}

export interface AgentConfig {
  legacy: EngineConfigLegacy;
  raglab: EngineConfigRagLab;
}

/** What an admin may send: absent = unchanged, empty string = clear the override. */
export interface ConfigUpdate {
  llmModel?: string;
  googleApiKey?: string;
  groqApiKey?: string;
}

@Injectable({ providedIn: 'root' })
export class AgentAdminService {

  private readonly base = '/api/admin/agent';

  constructor(private http: HttpClient) {}

  endpoint(): Observable<AgentEndpoint> {
    return this.http.get<AgentEndpoint>(`${this.base}/endpoint`);
  }

  /** Points the assistant at the RAG service; the reason is required server-side. */
  setEndpoint(url: string, reason: string): Observable<AgentEndpoint> {
    return this.http.put<AgentEndpoint>(`${this.base}/endpoint`, { url, reason });
  }

  /** Forgets the admin-set address; the deployed default applies again. */
  clearEndpoint(reason: string): Observable<AgentEndpoint> {
    return this.http.request<AgentEndpoint>('delete', `${this.base}/endpoint`, { body: { reason } });
  }

  /** Probe + raw reply; resolves even when the service is unreachable. */
  diagnostics(): Observable<AgentDiagnostics> {
    return this.http.get<AgentDiagnostics>(`${this.base}/diagnostics`);
  }

  /**
   * The journal, newest first. Filtering happens server-side: "who rebuilt an index"
   * has to be answerable even when the last hundred entries are something else.
   */
  audit(limit?: number, action?: string): Observable<AgentAuditEntry[]> {
    const params: Record<string, string> = {};
    if (limit) { params['limit'] = String(limit); }
    if (action && action !== 'ALL') { params['action'] = action; }
    return this.http.get<AgentAuditEntry[]>(`${this.base}/audit`, { params });
  }

  /** What the journal actually contains — the filter's own data, not a hardcoded list. */
  auditActions(): Observable<AuditActionCount[]> {
    return this.http.get<AuditActionCount[]>(`${this.base}/audit/actions`);
  }

  /* ------------------------------------------------------------ engine choice */

  /** Which engine serves customer chat right now, and where that choice came from. */

  /**
   * Hands customer chat to another engine. Effective on the next turn: the backend
   * resolves the engine per call, so there is no restart and no cache to clear.
   */

  /** Both engines with their liveness, as the console lists them before a switch. */

  /* ---------------------------------------------------------- models & keys */

  /** Both engines' models and keys: the in-house one editable, RAGLab read-only. */

  /**
   * Replaces the in-house agent's chat model and/or provider keys (SUPER_ADMIN,
   * reason mandatory). A key travels in only — it is never read back.
   */

  /* ------------------------------------------------------- index coherence */

  /** The in-house index checked against the running embedder. Free (no model call). */

  /** The same, plus one live embedding: proves the key and model still work now. */

  /* ------------------------------------------------------------ corpus & index */

  /** The files both engines read, and the state of the two indexes built from them. */
  corpus(): Observable<AgentCorpus> {
    return this.http.get<AgentCorpus>(`${this.base}/corpus`);
  }

  /**
   * Rebuilds the in-house index from the published files (SUPER_ADMIN, reason
   * mandatory). Re-queues every published document through the ingestion queue.
   */
  rebuildLegacy(reason: string): Observable<RebuildResult> {
    return this.http.post<RebuildResult>(`${this.base}/corpus/rebuild/legacy`, { reason });
  }

  /**
   * Records that the RAGLab index was rebuilt out of band. This does not trigger
   * anything remotely — it keeps the record of who rebuilt it, when and why.
   */
  declareRagLabRebuild(reason: string): Observable<RebuildResult> {
    return this.http.post<RebuildResult>(`${this.base}/corpus/rebuild/raglab`, { reason });
  }

  /* -------------------------------------------------------------- workbench */

  /**
   * "Could this quote ever validate?" — substring lookup in the corpus, free and
   * open to every staff role.
   */
  searchChunks(text: string, limit?: number | null): Observable<ChunkLookupResult> {
    const body: { text: string; limit?: number } = { text };
    if (limit) {
      body.limit = limit;
    }
    return this.http.post<ChunkLookupResult>(`${this.base}/chunks/search`, body);
  }

  /** Ranked chunks for a question — one embedding call, no chat model. */
  retrieval(query: RetrievalQuery): Observable<RetrievalResult> {
    return this.http.post<RetrievalResult>(`${this.base}/search`, this.retrievalBody(query));
  }

  /** One grounded answer: claims, verbatim quotes, sources. Costs embed + chat. */
  answer(query: AnswerQuery): Observable<AnswerResult> {
    const body: Record<string, unknown> = this.retrievalBody(query);
    if (query.queryLang) {
      body['queryLang'] = query.queryLang;
    }
    if (query.includeExcerpts !== null && query.includeExcerpts !== undefined) {
      body['includeExcerpts'] = query.includeExcerpts;
    }
    return this.http.post<AnswerResult>(`${this.base}/answer`, body);
  }

  /**
   * The admin API is camelCase like the rest of /api/admin (the snake_case names
   * belong to the RAG service and are applied server-side). Empty controls are
   * left out so the service keeps its own defaults.
   */
  private retrievalBody(query: RetrievalQuery): Record<string, unknown> {
    const body: Record<string, unknown> = { question: query.question };
    if (query.k) {
      body['k'] = query.k;
    }
    if (query.mode) {
      body['mode'] = query.mode;
    }
    if (query.langFilter) {
      body['langFilter'] = query.langFilter;
    }
    if (query.neighborRadius !== null && query.neighborRadius !== undefined) {
      body['neighborRadius'] = query.neighborRadius;
    }
    return body;
  }
}
