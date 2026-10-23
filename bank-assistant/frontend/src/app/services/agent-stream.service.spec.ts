import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AgentStreamService } from './agent-stream.service';

/** Builds an SSE-style Response body from text frames. */
function sseResponse(frames: object[]): Response {
  const body = frames.map(f => `data: ${JSON.stringify(f)}\n\n`).join('');
  return {
    ok: true,
    status: 200,
    body: new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(body));
        controller.close();
      }
    })
  } as unknown as Response;
}

describe('AgentStreamService', () => {
  let service: AgentStreamService;
  let http: HttpTestingController;
  const fetchOriginal = globalThis.fetch;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AgentStreamService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    globalThis.fetch = fetchOriginal;
  });

  it('parses delta frames and terminal done frame from the SSE stream', async () => {
    const deltas: string[] = [];
    let done: any;
    globalThis.fetch = (async () => sseResponse([
      { type: 'delta', text: 'Bonjour ' },
      { type: 'delta', text: '!' },
      { type: 'done', reponse: 'Bonjour !', tokensConsommes: 12, nombreMessages: 1, grounded: true }
    ])) as typeof fetch;

    await new Promise<void>(resolve => {
      service.send('s1', 'salut', 'fr', null, {
        onDelta: t => deltas.push(t),
        onDone: r => { done = r; resolve(); },
        onError: () => resolve(),
        onFallback: () => resolve(),
        onAborted: () => resolve()
      });
    });

    expect(deltas.join('')).toBe('Bonjour !');
    expect(done.reponse).toBe('Bonjour !');
    expect(done.tokensConsommes).toBe(12);
    expect(done.grounded).toBe(true);
  });

  it('falls back to POST /chat/message when the stream endpoint is missing (404)', async () => {
    let done: any;
    globalThis.fetch = (async () => ({ ok: false, status: 404, json: async () => ({}) })) as unknown as typeof fetch;

    const finished = new Promise<void>(resolve => {
      service.send('s1', 'salut', 'fr', null, {
        onDelta: () => {},
        onDone: r => { done = r; resolve(); },
        onError: () => resolve(),
        onFallback: () => { /* fallback in flight */ },
        onAborted: () => resolve()
      });
    });

    // Let the async stream continuation reach the fallback POST (microtasks + timer).
    await new Promise(r => setTimeout(r, 0));
    await new Promise(r => setTimeout(r, 0));
    http.expectOne(r => r.url.includes('/api/chat/message') && !r.url.includes('/stream'))
      .flush({ reponse: 'reply', tokensConsommes: 5 });
    await finished;

    expect(done?.reponse).toBe('reply');
  });

  it('surfaces a terminal error frame and 429 quota errors', async () => {
    let error: string | undefined;
    globalThis.fetch = (async () => sseResponse([{ type: 'error', message: 'limite' }])) as typeof fetch;
    await new Promise<void>(resolve => {
      service.send('s1', 'q', 'fr', null, {
        onDelta: () => {}, onDone: () => resolve(),
        onError: m => { error = m; resolve(); },
        onFallback: () => resolve(), onAborted: () => resolve()
      });
    });
    expect(error).toBe('limite');
  });

  describe('tidyAnswer', () => {
    it('strips markdown bold/italics and headings', () => {
      expect(service.tidyAnswer('**Gras** and *italique*')).toBe('Gras and italique');
      expect(service.tidyAnswer('## Titre\ntexte')).toBe('Titre\ntexte');
    });

    it('never shows provenance, whatever the model wrote', () => {
      // The backend strips citations before answering; this is the display belt,
      // so an older backend (or a cached answer) cannot leak them either.
      expect(service.tidyAnswer('Voici la réponse.\n(Source : Guide.pdf)')).toBe('Voici la réponse.');
      expect(service.tidyAnswer('Taux 4,75 % [S1] et [S2].')).toBe('Taux 4,75 % et.');
      expect(service.tidyAnswer('Coût 300 DT (المصدر: دورية)')).toBe('Coût 300 DT');
      expect(service.tidyAnswer('Réponse.\nSources: guide_interne.docx')).toBe('Réponse.');
    });

    it('drops a resource list an external service appends after a refusal', () => {
      // The wording a retrieval service writes when it found nothing to answer from,
      // and the list of what it read anyway. The backend strips the block before the
      // answer is sent; this is the belt for an older backend or a cached reply.
      expect(service.tidyAnswer('لا أستطيع الإجابة اعتمادا على المستندات المقدمة.\n\n'
        + 'الموارد:\n1. دليل التمويل\n2. دورية 2016-08'))
        .toBe('لا أستطيع الإجابة اعتمادا على المستندات المقدمة.');
      expect(service.tidyAnswer('Voici la réponse.\nResources:\n- Guide des financements\n- Circulaire'))
        .toBe('Voici la réponse.');
    });

    it('keeps an answer that only looks like a source block', () => {
      // Required paperwork is the answer to the question, not a citation — the labels
      // that strip are the ones naming what was read, never what the client must file.
      const fr = 'Documents requis : la CIN, trois bulletins de salaire.';
      expect(service.tidyAnswer(fr)).toBe(fr);
      const ar = 'الوثائق المطلوبة: بطاقة التعريف الوطنية ومطولات الأجر.';
      expect(service.tidyAnswer(ar)).toBe(ar);
      const conditions = 'Conditions du financement :\n- matériel neuf\n- apport de 20 %';
      expect(service.tidyAnswer(conditions)).toBe(conditions);
    });

    it('strips a numbered reference even when it carries no colon', () => {
      expect(service.tidyAnswer('Coût 300 DT (réf. 3) et 4,75 % [source 12].'))
        .toBe('Coût 300 DT et 4,75 %.');
    });

    it('keeps French punctuation spacing where it did not cut anything', () => {
      // Tidy closes the hole a citation left; it does not restyle the sentence.
      expect(service.tidyAnswer('Deux étapes : préparer la CIN (Source : Guide.pdf).'))
        .toBe('Deux étapes : préparer la CIN.');
    });

    it('normalises inline dash lists into separate lines', () => {
      const out = service.tidyAnswer('Conditions : - Le bien appartient à la banque - La vente est ferme');
      expect(out).toContain('\n- ');
    });

    it('returns empty text untouched', () => {
      expect(service.tidyAnswer('')).toBe('');
    });
  });
});
