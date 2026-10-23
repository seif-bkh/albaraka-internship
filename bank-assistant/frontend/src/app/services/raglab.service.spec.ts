import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RaglabService, RaglabHealth, parseRaglabError } from './raglab.service';
import { HttpErrorResponse } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

describe('RaglabService', () => {
  let service: RaglabService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [RaglabService],
    });
    service = TestBed.inject(RaglabService);
    httpMock = TestBed.inject(HttpTestingController);

    // Nothing is polled until the console asks for it: the service is root-scoped and
    // must not hit /health from the landing page, the portal or a hidden tab.
  });

  afterEach(() => {
    service.stopPolling();
    httpMock.verify();
  });

  it('correctly tracks health and derived signals', () => {
    const mockHealth: RaglabHealth = {
      status: 'ok',
      version: '1.2.3',
      profile: {
        embedding: { provider: 'nvidia', model: 'nvidia/nemotron-3-embed-1b' },
        answer: { provider: 'xkiro', model: 'qwen/qwen3.8-max:free' },
        chunking: { mode: 'restructure', size: 220, overlap: 40 },
        retrieval: { top_k: 5, mode: 'vector', lang_filter: null, neighbor_radius: 0 },
        pipeline: 'supported pipeline — the same model pair main.py answer uses'
      },
      index: {
        collection: 'raglab_app_nvidia_nemotron_3_embed_1b_restructure',
        count: 346,
        tokenizer_match: true
      },
      keys: {
        NVIDIA_API_KEY: 'set',
        XKIRO_API_KEY: 'missing'
      },
      ingest: {
        state: 'idle',
        stored: 346,
        error: null
      }
    };

    service.health.set(mockHealth);

    expect(service.isConnected()).toBe(true);
    expect(service.isSealed()).toBe(false);
    expect(service.isStale()).toBe(false);
    expect(service.isEmptyIndex()).toBe(false);
    expect(service.isPinnedPipeline()).toBe(true);
    expect(service.missingKeys()).toEqual(['XKIRO_API_KEY']);
  });

  it('detects sealed mode when ingest is running', () => {
    service.health.set({
      status: 'ok',
      ingest: {
        state: 'running',
        started_at: '2026-09-16T10:00:00Z'
      }
    });

    expect(service.isSealed()).toBe(true);
  });

  it('detects stale index when tokenizer_match is false', () => {
    service.health.set({
      status: 'ok',
      index: {
        collection: 'test_coll',
        count: 100,
        tokenizer_match: false
      }
    });

    expect(service.isStale()).toBe(true);
  });

  it('calls getModels and returns providers registry', () => {
    service.getModels().subscribe(res => {
      expect(res.embedding.length).toBe(1);
      expect(res.embedding[0].provider).toBe('nvidia');
    });

    const req = httpMock.expectOne('/api/admin/raglab/models');
    expect(req.request.method).toBe('GET');
    req.flush({
      embedding: [{ provider: 'nvidia', label: 'NVIDIA', key_envs: ['NVIDIA_API_KEY'], key_set: true, models: ['nemotron'] }],
      answer: []
    });
  });

  it('calls updateProfile and triggers health refresh', () => {
    service.updateProfile({ chunking: { mode: 'size', size: 300, overlap: 50 } }).subscribe(res => {
      expect(res.collection).toBe('new_coll');
    });

    const req = httpMock.expectOne('/api/admin/raglab/profile');
    expect(req.request.method).toBe('POST');
    req.flush({
      profile: {
        embedding: { provider: 'nvidia', model: 'nemotron' },
        answer: { provider: 'xkiro', model: 'qwen' },
        chunking: { mode: 'size', size: 300, overlap: 50 },
        retrieval: { top_k: 5, mode: 'vector' }
      },
      collection: 'new_coll'
    });

    // health refresh triggered
    const healthReq = httpMock.expectOne('/api/admin/raglab/health');
    healthReq.flush({ status: 'ok' });
  });

  it('calls searchChunks for quote validation', () => {
    service.searchChunks('atlas card costs 10 dinars', 3).subscribe(res => {
      expect(res.full.length).toBe(1);
    });

    const req = httpMock.expectOne('/api/admin/raglab/chunks/search');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ text: 'atlas card costs 10 dinars', limit: 3 });
    req.flush({
      description: 'Found',
      needle: 'atlas card costs 10 dinars',
      full: [{ source: 'note.md', chunk_index: 1, heading: 'Fees' }],
      head: [],
      tail: []
    });
  });

  it('calls answer endpoint with query parameters', () => {
    service.answer({ question: 'Combien coûte la carte ?', k: 5, include_excerpts: true }).subscribe(res => {
      expect(res.status).toBe('answered');
      expect(res.claims?.length).toBe(1);
    });

    const req = httpMock.expectOne('/api/admin/raglab/answer');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.question).toBe('Combien coûte la carte ?');
    req.flush({
      status: 'answered',
      answer: 'La carte coûte 10 dinars. [S1]',
      claims: [{ text: 'La carte coûte 10 dinars.', evidence: [{ source_id: 'S1', quote: '10 dinars' }] }],
      sources: [{ source_id: 'S1', document: 'tarifs.pdf', heading: 'Cartes' }]
    });
  });

  it('parses structured RAGLab error envelopes', () => {
    const errorResponse = new HttpErrorResponse({
      error: {
        detail: {
          reason: 'missing_api_key',
          slot: 'embedding',
          key_env: 'NVIDIA_API_KEY',
          hint: 'Veuillez configurer la clé NVIDIA_API_KEY'
        }
      },
      status: 503,
      statusText: 'Service Unavailable'
    });

    const parsed = parseRaglabError(errorResponse);
    expect(parsed.reason).toBe('missing_api_key');
    expect(parsed.hint).toBe('Veuillez configurer la clé NVIDIA_API_KEY');
    expect(parsed.keyEnv).toBe('NVIDIA_API_KEY');
  });

  it('parses FastAPI validation error array', () => {
    const errorResponse = new HttpErrorResponse({
      error: {
        detail: [
          { loc: ['body', 'question'], msg: 'String should have at least 1 character' }
        ]
      },
      status: 422,
      statusText: 'Unprocessable Entity'
    });

    const parsed = parseRaglabError(errorResponse);
    expect(parsed.reason).toBe('validation_error');
    expect(parsed.message).toContain('question');
  });
});

describe('RaglabService polling', () => {
  let service: RaglabService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [RaglabService],
    });
    service = TestBed.inject(RaglabService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.stopPolling();
    vi.useRealTimers();
    httpMock.verify();
  });

  it('stays silent until the console starts it', () => {
    // The whole point of the rewrite: a root-scoped service must not poll by itself.
    httpMock.expectNone('/api/admin/raglab/health');
    expect(service.health()).toBeNull();
  });

  it('polls every 20 s while healthy and stops on stopPolling', () => {
    vi.useFakeTimers();
    service.startPolling();
    httpMock.expectOne('/api/admin/raglab/health').flush({ status: 'ok', version: '1.2.3' });

    vi.advanceTimersByTime(19_000);
    httpMock.expectNone('/api/admin/raglab/health');

    vi.advanceTimersByTime(2_000);
    httpMock.expectOne('/api/admin/raglab/health').flush({ status: 'ok' });

    service.stopPolling();
    vi.advanceTimersByTime(120_000);
    httpMock.expectNone('/api/admin/raglab/health');
  });

  it('watches closely while an ingest is running, and slow when it is down', () => {
    vi.useFakeTimers();
    service.startPolling();
    httpMock.expectOne('/api/admin/raglab/health')
      .flush({ status: 'ok', ingest: { state: 'running' } });

    // 2 s, not 20 s: the operator is watching a job progress.
    vi.advanceTimersByTime(2_500);
    httpMock.expectOne('/api/admin/raglab/health').flush({ status: 'down' });

    // A dead endpoint is retried every 60 s, not hammered.
    vi.advanceTimersByTime(30_000);
    httpMock.expectNone('/api/admin/raglab/health');
    vi.advanceTimersByTime(31_000);
    httpMock.expectOne('/api/admin/raglab/health').flush({ status: 'down' });
  });

  it('does not spend requests on a hidden tab, and refreshes when it returns', () => {
    vi.useFakeTimers();
    const hidden = vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);

    service.startPolling();
    httpMock.expectOne('/api/admin/raglab/health').flush({ status: 'ok' });
    vi.advanceTimersByTime(120_000);
    httpMock.expectNone('/api/admin/raglab/health');

    // Coming back to the tab is exactly when a fresh reading is wanted.
    hidden.mockReturnValue(false);
    document.dispatchEvent(new Event('visibilitychange'));
    httpMock.expectOne('/api/admin/raglab/health').flush({ status: 'ok' });

    hidden.mockRestore();
  });

  it('never queues a second request while one is in flight', () => {
    service.startPolling();
    const slow = httpMock.expectOne('/api/admin/raglab/health');
    service.refreshHealth();
    service.refreshHealth();
    httpMock.expectNone('/api/admin/raglab/health');
    slow.flush({ status: 'ok' });
  });

  it('records when the reading was taken', () => {
    expect(service.lastCheckedAt()).toBeNull();
    service.refreshHealth();
    httpMock.expectOne('/api/admin/raglab/health').flush({ status: 'ok' });
    expect(service.lastCheckedAt()).toBeInstanceOf(Date);
  });
});
