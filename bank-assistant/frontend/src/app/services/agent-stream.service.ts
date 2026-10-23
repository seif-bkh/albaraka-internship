import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../environments/environment';

/**
 * Shared client for the agent chat endpoints (POST /api/chat/message/stream
 * with a POST /api/chat/message fallback).
 *
 * <p>Extracted from PublicChatComponent so the landing-page floating widget and
 * the portal assistant can stream answers with the exact same resilience rules:
 * SSE frames (delta / done / error), transparent fallback to the classic POST
 * when the stream endpoint is missing, 429 quota handling and abort support.
 *
 * <p>The caller owns the UI: it receives plain callbacks and renders the
 * bubbles itself. The service holds no state between calls.
 */
export interface AgentStreamCallbacks {
  /** A delta of text arrived for the current reply. */
  onDelta(text: string): void;
  /** Terminal frame: the reply is complete and metered. */
  onDone(res: StreamDone): void;
  /** Terminal failure: display `message` in the bubble. */
  onError(message: string, status?: number): void;
  /** The stream path was abandoned; the caller should drop its empty bubble. */
  onFallback(): void;
  /** The user aborted: keep the partial bubble, no system message here. */
  onAborted(): void;
}

export interface StreamDone {
  sessionId?: string;
  reponse?: string;
  tokensConsommes?: number;
  tokensLimite?: number;
  nombreMessages?: number;
  messagesLimite?: number;
  grounded?: boolean;
}

export interface StreamHandle {
  abort(): void;
}

@Injectable({ providedIn: 'root' })
export class AgentStreamService {
  private readonly apiUrl = `${environment.apiUrl}/chat/message`;
  private readonly apiUrlStream = `${environment.apiUrl}/chat/message/stream`;

  constructor(private http: HttpClient) {}

  /**
   * Sends one user message and streams the reply.
   *
   * @param token optional JWT — when present the backend links the exchange to
   *              the authenticated client's conversations.
   */
  send(sessionId: string, message: string, langue: string, token: string | null,
       callbacks: AgentStreamCallbacks): StreamHandle {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const controller = new AbortController();
    void this.stream(controller, sessionId, message, langue, headers, callbacks);
    return { abort: () => controller.abort() };
  }

  /** Classic (non-streaming) reply — used by the abort path of the fallback. */
  sendBlocking(sessionId: string, message: string, langue: string, token: string | null,
               onDone: (res: StreamDone) => void, onError: (message: string, status?: number) => void): void {
    let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    this.http.post<StreamDone>(this.apiUrl, { sessionId, message, langue }, { headers })
      .subscribe({ next: onDone, error: (err) => onError(err?.error?.error || err?.error?.message, err?.status) });
  }

  private async stream(controller: AbortController, sessionId: string, message: string,
                       langue: string, headers: Record<string, string>,
                       cb: AgentStreamCallbacks): Promise<void> {
    try {
      const response = await fetch(this.apiUrlStream, {
        method: 'POST',
        headers,
        signal: controller.signal,
        body: JSON.stringify({ sessionId, message, langue })
      });

      if (!response.ok) {
        const body = await this.tryReadJson(response);
        this.handleHttpError(response.status, body, cb, () => {
          cb.onFallback();
          this.sendBlocking(sessionId, message, langue, headers['Authorization'] ?? null,
            (res) => cb.onDone(res), (msg, status) => cb.onError(msg || 'Erreur', status));
        });
        return;
      }
      if (!response.body) {
        throw new Error('No response body for streaming endpoint');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let gotDone = false;

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        let sep: number;
        while ((sep = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, sep);
          buffer = buffer.slice(sep + 2);
          if (this.handleSseEvent(block, cb)) {
            gotDone = true;
          }
        }
        if (gotDone) {
          break;
        }
      }
      if (!gotDone) {
        cb.onError("Le service d'assistance est momentanément indisponible. Réessayez dans un instant.");
      }
    } catch (err: any) {
      if (err && (err.name === 'AbortError' || err.type === 'aborted')) {
        cb.onAborted();
      } else {
        console.error('[agent-stream] POST /chat/message/stream failed', err);
        cb.onFallback();
        this.sendBlocking(sessionId, message, langue, headers['Authorization'] ?? null,
          (res) => cb.onDone(res), (msg, status) => cb.onError(msg || 'Erreur', status));
      }
    }
  }

  /** Returns true once a terminal `done` or `error` frame has been consumed. */
  private handleSseEvent(block: string, cb: AgentStreamCallbacks): boolean {
    let data = '';
    for (const line of block.split('\n')) {
      const trimmed = line.trim();
      if (trimmed.startsWith('data:')) {
        data += trimmed.substring(5).trim();
      }
    }
    if (!data) {
      return false;
    }
    let event: any;
    try {
      event = JSON.parse(data);
    } catch {
      return false;
    }
    if (event.type === 'delta') {
      cb.onDelta(String(event.text ?? ''));
      return false;
    }
    if (event.type === 'done') {
      cb.onDone(event as StreamDone);
      return true;
    }
    if (event.type === 'error') {
      cb.onError(String(event.message ?? 'Erreur lors du traitement de la requête.'));
      return true;
    }
    return false;
  }

  private handleHttpError(status: number, body: any, cb: AgentStreamCallbacks, fallback: () => void): void {
    const serverMessage: string | undefined = body?.error || body?.message;
    if (status === 404 || status === 405) {
      // Endpoint not present on an older backend: transparent fallback.
      fallback();
    } else {
      cb.onError(serverMessage || "Le service d'assistance est momentanément indisponible. Réessayez dans un instant.", status);
    }
  }

  private async tryReadJson(response: Response): Promise<any> {
    try {
      return await response.json();
    } catch {
      return null;
    }
  }

  /**
   * Normalises an agent reply for display: markdown a model may slip in, and any
   * trace of provenance.
   *
   * <p>Citations are stripped server-side before the answer is sent at all (see
   * {@code AnswerSanitizer} in the Spring Boot edge); this is the belt, not the
   * braces. It keeps working against an older backend, and it is why no surface
   * here has a sources chip, a "source:" line or a link list.
   */
  tidyAnswer(text: string): string {
    if (!text) { return text; }
    text = text
      .replace(/\[\s*(?:(?:S|SRC|DOC)[\s_-]?\d+(?:\s*[,;]\s*(?:S|SRC|DOC)?\d+)*|doc_\d+|(?:réf|ref|src|source|citation|المصدر|الموارد)[\s.]*\d+|(?:sources?|références?|refs?|citations?|resources?|المصدر|المصادر|الموارد|الوثائق|مراجع)[ \t]*[:：][^\]]*)\s*]/gi, '')
      .replace(/([(\[])[ \t]*(?:(?:sources?|références?|refs?|citations?|resources?|المصدر|المصادر|الموارد|الوثائق|مراجع)[ \t]*[:：][^)\]]*|(?:réf|ref|src|source|citation|المصدر|الموارد)[\s.]*\d+)[)\]]/giu, '')
      // A provenance heading and the short list directly under it — the shape an
      // external service appends to its own answer. Bounded to six list lines: the
      // backend owns the guarantee (AnswerSanitizer strips the block before sending),
      // this is the belt for an older backend, and a belt must not eat an answer.
      .replace(/^[ \t>\-*]*(?:sources?(?:\s+(?:utilis[ée]s?|consult[ée]s?))?|références?|references?|citations?|resources?(?:\s+(?:used|consulted))?|bibliographie|المصدر|المصادر|الوثائق\s*(?:المستخدمة|المرجعية|المقترحة)|المستندات\s*(?:المقدمة|المستخدمة)|الموارد(?:\s*المستخدمة)?|مراجع)[ \t]*(?:[:：][^\n]*)?(?:\n(?:[-\u2022*]|\d+[.)])[ \t]*[^\n]{0,120}){0,6}/gimu, '')
      .replace(/\[([^\]]{1,200})\]\((?:https?:\/\/|www\.|\/|mailto:)[^)\s]*\)/gi, '$1')
      .replace(/(^|\s)(?:https?:\/\/|www\.)\S*\.(?:pdf|docx?|xlsx?|pptx?|odt)\b/gi, ' ')
      .replace(/(?:https?:\/\/|ftp:\/\/)(?:localhost|127\.|0\.0\.0\.0|10\.|192\.168\.|172\.\d+\.|[\w.-]*\.(?:internal|local|corp|intranet)|minio|rustfs|backend|agent|postgres|redis)[^\s)\]]*/gi, '')
      .replace(/[ \t]+([,.\u060C])/g, '$1'); // jamais avant « : ; ! ? » : la typo FR/AR y met une espace

    return text
      .replace(/\*\*(.+?)\*\*/g, '$1')
      .replace(/(^|\s)\*(?!\s)(.+?)(?<!\s)\*(?=\s|$|[.,;:])/g, '$1$2')
      .replace(/^\s{0,3}#{1,6}\s+/gm, '')
      .replace(/([.:;!?])\s+-\s+(?=\S)/g, '$1\n- ')
      .replace(/\s+-\s+(?=[a-zà-ÿ\u0600-\u06FF][^\n]{12,})/g, '\n- ')
      .replace(/[ \t]{2,}/g, ' ')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
  }
}
