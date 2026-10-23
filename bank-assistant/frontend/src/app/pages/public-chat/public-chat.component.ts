import { Component, OnInit, ViewChild, ElementRef, AfterViewChecked, ChangeDetectorRef } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../components/sidebar/sidebar';
import { AuthService, User } from '../../services/auth.service';
import { TranslatePipe, TranslationService } from '../../services/translation.service';
import { AgentStreamService } from '../../services/agent-stream.service';
import { environment } from '../../../environments/environment';

interface Message {
  role: 'user' | 'assistant' | 'system';
  content: string;
  time: string;
  tokens?: number;
  /** True while this assistant bubble is still receiving streamed tokens. */
  streaming?: boolean;
  /** false = the assistant refused / had nothing reliable to answer from. */
  grounded?: boolean;
}

@Component({
  selector: 'app-public-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, TranslatePipe],
  templateUrl: './public-chat.component.html',
  styleUrls: ['./public-chat.css']
})
export class PublicChatComponent implements OnInit, AfterViewChecked {
  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  currentLang: string = 'fr';
  newMessage: string = '';
  sessionId: string = '';

  messages: Message[] = [];

  tokensConsommes: number = 0;
  tokensLimite: number = 3000;
  nombreMessages: number = 0;
  messagesLimite: number = 20;

  isLoading: boolean = false;
  isStreaming: boolean = false;
  isLimitReached: boolean = false;

  private chatSubscription: Subscription | null = null;
  private streamController: AbortController | null = null;
  private streamingMsg: Message | null = null;
  private apiUrl = `${environment.apiUrl}/chat/message`;
  private apiUrlStream = `${environment.apiUrl}/chat/message/stream`;
  private apiUrlHistory = `${environment.apiUrl}/chat/history`;

  currentUser: User | null = null;
  showAuthModal: boolean = false;
  isLoginMode: boolean = true;
  authSuccess: boolean = false;
  authForm = { nom: '', prenom: '', email: '', motDePasse: '' };
  authError: string = '';

  constructor(
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private authService: AuthService,
    private i18n: TranslationService,
    private agentStream: AgentStreamService
  ) {}

  ngOnInit() {
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
      if (user) {
        this.tokensLimite = 10000;
        this.messagesLimite = 50;
        this.sessionId = this.generateUUID();
        this.messages = [];
        this.addWelcomeMessage();
      } else {
        this.messages = [];
        this.addWelcomeMessage();
      }
    });
    if (!this.sessionId) {
      this.sessionId = this.generateUUID();
    }
  }

  /**
   * The reply language follows the message, not the UI selector: a customer who
   * types Arabic into a French-labelled UI gets an Arabic answer. Only the
   * message language is sent; the agent detects it ('auto') and the UI chrome
   * flips to match so RTL/labels stay coherent.
   */
  replyLanguageFor(text: string): string {
    const arabic = (text.match(/[\u0600-\u06FF]/g) || []).length;
    const latin = (text.match(/[A-Za-z\u00C0-\u00FF]/g) || []).length;
    const detected = arabic > latin ? 'ar' : (latin > 0 ? 'auto' : this.currentLang);
    if (detected === 'ar' && this.currentLang !== 'ar') {
      this.onLangChange('ar');
    } else if (detected === 'auto' && this.currentLang === 'ar' && latin > 3) {
      this.onLangChange('fr');
    }
    return detected;
  }

  onLangChange(lang: string) {
    this.currentLang = lang;
    this.i18n.setLang(lang);
    this.cdr.markForCheck();
  }

  onSessionSelected(sessionId: string) {
    if (!sessionId) {
      this.sessionId = this.generateUUID();
      this.messages = [];
      this.addWelcomeMessage();
    } else {
      this.sessionId = sessionId;
      this.loadHistory(sessionId);
    }
  }

  loadHistory(sessionId?: string) {
    const token = this.authService.getAccessToken();
    if (!token) return;

    let headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
    let url = this.apiUrlHistory;
    if (sessionId) {
      url += `?sessionId=${sessionId}`;
    }

    this.http.get<any[]>(url, { headers }).subscribe({
      next: (history) => {
        if (history && history.length > 0) {
          this.messages = history;
        } else {
          this.messages = [];
          this.addWelcomeMessage();
        }
        this.cdr.detectChanges();
        setTimeout(() => this.scrollToBottom(), 100);
      },
      error: (err) => {
        console.error("Erreur lors du chargement de l'historique", err);
        this.messages = [];
        this.addWelcomeMessage();
      }
    });
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  generateUUID(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      const r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }

  addWelcomeMessage() {
    this.messages.push({
      role: 'assistant',
      content: this.i18n.t('chat.welcome'),
      time: this.getCurrentTime()
    });
  }

  getCurrentTime(): string {
    const now = new Date();
    return now.getHours().toString().padStart(2, '0') + ':' + now.getMinutes().toString().padStart(2, '0');
  }

  scrollToBottom(): void {
    try {
      if (this.messagesContainer) {
        this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
      }
    } catch (err) { }
  }

  askSuggestion(key: string) {
    this.newMessage = this.i18n.t(key);
    this.sendMessage();
  }

  sendMessage(event?: Event) {
    if (event) {
      // Shift+Enter inserts a newline; plain Enter sends.
      if ((event as KeyboardEvent).shiftKey) {
        return;
      }
      event.preventDefault();
    }

    if (!this.newMessage.trim() || this.isLoading || this.isStreaming || this.isLimitReached) {
      return;
    }

    const userText = this.newMessage.trim();
    this.messages.push({
      role: 'user',
      content: userText,
      time: this.getCurrentTime()
    });

    this.newMessage = '';
    this.isLimitReached = false;

    let headers = new HttpHeaders();
    const token = this.authService.getAccessToken();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }

    this.streamMessage(headers, userText);
  }

  /**
   * Streams the assistant's reply token by token from POST /chat/message/stream
   * (the endpoint is a POST, so native EventSource won't work; we read the SSE
   * body manually via fetch's ReadableStream). The reply accumulates in a single
   * assistant bubble and the terminal `done` frame updates counters/metering.
   *
   * <p>Resilience: if the stream endpoint is unavailable (404/405, e.g. an older
   * backend) or the network rejects before any SSE frame arrives, we transparently
   * fall back to the classic POST /chat/message reply.
   */
  private async streamMessage(headers: HttpHeaders, userText: string): Promise<void> {
    this.lastUserText = userText;
    this.preStreamTokens = this.tokensConsommes;
    this.streamingMsg = {
      role: 'assistant',
      content: '',
      time: this.getCurrentTime(),
      tokens: 0,
      streaming: true
    };
    this.messages.push(this.streamingMsg);
    this.isStreaming = true;
    this.cdr.detectChanges();
    this.scrollToBottom();

    const controller = new AbortController();
    this.streamController = controller;

    const plainHeaders: Record<string, string> = { 'Content-Type': 'application/json' };
    const auth = headers.get('Authorization');
    if (auth) {
      plainHeaders['Authorization'] = auth;
    }

    try {
      const response = await fetch(this.apiUrlStream, {
        method: 'POST',
        headers: plainHeaders,
        signal: controller.signal,
        body: JSON.stringify({
          sessionId: this.sessionId,
          message: userText,
          langue: this.replyLanguageFor(userText)
        })
      });

      if (!response.ok) {
        // Gate rejections (429 etc.) are JSON and should not retry via /message.
        const body = await this.tryReadJson(response);
        this.finishStreamWithHttpError(response.status, body);
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
          if (this.handleSseEvent(block)) {
            gotDone = true;
          }
        }
        if (gotDone) {
          break;
        }
      }
      if (!gotDone) {
        // Stream closed without a done frame: treat as an error.
        this.failStream("Le service d'assistance est momentanément indisponible. Réessayez dans un instant.");
      }
    } catch (err) {
      if (this.isStreamAbort(err)) {
        // User pressed stop; the partial bubble is kept as-is.
        this.finalizeInterruptedStream();
      } else {
        console.error('[chat] POST /chat/message/stream failed', err);
        // Network-level failure before any token arrived: transparent fallback.
        this.abandonStreamingBubble();
        this.sendNonStreaming(headers, userText);
      }
    } finally {
      this.streamController = null;
    }
  }

  /** Returns true once a terminal `done` frame has been consumed. */
  private handleSseEvent(block: string): boolean {
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
    } catch (e) {
      console.warn('[chat] Unparseable SSE frame', data);
      return false;
    }

    if (event.type === 'delta') {
      this.appendStreamToken(String(event.text ?? ''));
      return false;
    }
    if (event.type === 'done') {
      this.finalizeStreamDone(event);
      return true;
    }
    if (event.type === 'error') {
      this.failStream(String(event.message ?? "Erreur lors du traitement de la requête."));
      return true;
    }
    return false;
  }

  private appendStreamToken(text: string): void {
    if (!this.streamingMsg) {
      return;
    }
    this.streamingMsg.content += text;
    this.cdr.detectChanges();
    this.scrollToBottom();
  }

  private finalizeStreamDone(res: any): void {
    if (!this.streamingMsg) {
      return;
    }
    this.streamingMsg.streaming = false;

    if (res.sessionId && res.sessionId !== this.sessionId) {
      this.sessionId = res.sessionId;
    }

    this.tokensConsommes = res.tokensConsommes ?? this.tokensConsommes;
    this.nombreMessages = res.nombreMessages ?? this.nombreMessages;
    this.tokensLimite = res.tokensLimite || this.tokensLimite;
    this.messagesLimite = res.messagesLimite || this.messagesLimite;

    // Server echoes the full reply; prefer it over the accumulated deltas so the
    // bubble is exactly what was persisted.
    if (res.reponse) {
      this.streamingMsg.content = res.reponse;
    }
    this.streamingMsg.grounded = typeof res.grounded === 'boolean' ? res.grounded : undefined;
    this.streamingMsg.content = this.tidyAnswer(this.streamingMsg.content);

    const prevTokens = this.tokensConsommes;
    if (res.tokensConsommes !== undefined) {
      this.streamingMsg.tokens = Math.max(0, res.tokensConsommes - (this.preStreamTokens ?? 0));
    } else {
      this.streamingMsg.tokens = undefined;
    }

    if (this.tokensConsommes >= this.tokensLimite || this.nombreMessages >= this.messagesLimite) {
      this.isLimitReached = true;
    }

    this.isStreaming = false;
    this.streamingMsg = null;
    this.preStreamTokens = null;
    this.cdr.detectChanges();
    this.scrollToBottom();
  }

  private preStreamTokens: number | null = null;

  private finishStreamWithHttpError(status: number, body: any): void {
    // A JSON error frame-style body is appended to the bubble like /message.
    const serverMessage = body?.error || body?.message;
    if (status === 429) {
      this.isLimitReached = true;
      this.failStream(serverMessage || this.i18n.t('chat.limitReached'));
    } else if (status === 404 || status === 405) {
      // Endpoint not present on an older backend: transparent fallback.
      this.abandonStreamingBubble();
      let headers = new HttpHeaders();
      const token = this.authService.getAccessToken();
      if (token) {
        headers = headers.set('Authorization', `Bearer ${token}`);
      }
      this.sendNonStreaming(headers, this.lastUserText ?? '');
    } else {
      this.failStream(serverMessage || "Le service d'assistance est momentanément indisponible. Réessayez dans un instant.");
    }
  }

  private lastUserText: string | null = null;

  private async tryReadJson(response: Response): Promise<any> {
    try {
      return await response.json();
    } catch (e) {
      return null;
    }
  }

  private failStream(message: string): void {
    if (this.streamingMsg) {
      this.streamingMsg.content = message;
      this.streamingMsg.streaming = false;
    }
    this.isStreaming = false;
    this.streamingMsg = null;
    this.preStreamTokens = null;
    this.cdr.detectChanges();
    this.scrollToBottom();
  }

  /** Remove the empty placeholder bubble when falling back to POST /message. */
  private abandonStreamingBubble(): void {
    if (this.streamingMsg) {
      const idx = this.messages.indexOf(this.streamingMsg);
      if (idx >= 0) {
        this.messages.splice(idx, 1);
      }
      this.streamingMsg = null;
    }
    this.isStreaming = false;
  }

  private finalizeInterruptedStream(): void {
    if (this.streamingMsg) {
      this.streamingMsg.streaming = false;
    }
    this.isStreaming = false;
    this.streamingMsg = null;
    this.preStreamTokens = null;
    this.messages.push({
      role: 'system',
      content: this.i18n.t('chat.stop'),
      time: this.getCurrentTime()
    });
    this.cdr.detectChanges();
    this.scrollToBottom();
  }

  private isStreamAbort(err: any): boolean {
    return !!err && (err.name === 'AbortError' || err.type === 'aborted');
  }

  /**
   * Non-streaming reply over POST /chat/message — kept as the classic path and
   * used as the fallback when streaming is unavailable.
   */
  private sendNonStreaming(headers: HttpHeaders, userText: string): void {
    this.isLoading = true;
    const requestBody = { sessionId: this.sessionId, message: userText, langue: this.replyLanguageFor(userText) };

    this.chatSubscription = this.http.post<any>(this.apiUrl, requestBody, { headers }).subscribe({
      next: (res) => this.finalizeNonStreaming(res),
      error: (err) => {
        this.isLoading = false;
        this.chatSubscription = null;

        const serverMessage: string | undefined = err.error?.error || err.error?.message;
        console.error('[chat] POST /api/chat/message failed', err.status, err.error ?? err.message);

        let fallback: string;
        if (err.status === 429) {
          this.isLimitReached = true;
          fallback = serverMessage || this.i18n.t('chat.limitReached');
        } else if (err.status === 503) {
          fallback = "Le service d'assistance est momentanément indisponible. Réessayez dans un instant.";
        } else {
          fallback = 'Désolé, une erreur technique est survenue. Veuillez réessayer plus tard.';
        }
        this.pushAssistantBubble(serverMessage || fallback);
      }
    });
  }

  private finalizeNonStreaming(res: any): void {
    this.isLoading = false;
    this.chatSubscription = null;

    if (res.sessionId && res.sessionId !== this.sessionId) {
      this.sessionId = res.sessionId;
    }

    const receivedTokens = res.tokensConsommes - this.tokensConsommes;

    this.tokensConsommes = res.tokensConsommes;
    this.nombreMessages = res.nombreMessages;
    this.tokensLimite = res.tokensLimite || 3000;
    this.messagesLimite = res.messagesLimite || 20;

    this.pushAssistantBubble(res.reponse, receivedTokens, res.grounded);

    if (this.tokensConsommes >= this.tokensLimite || this.nombreMessages >= this.messagesLimite) {
      this.isLimitReached = true;
    }
  }

  /**
   * Display normalisation of an agent reply — one rule for every surface, held by
   * AgentStreamService: markdown is flattened and any trace of provenance goes
   * (customers are not shown which documents the answer was built from).
   */
  private tidyAnswer(text: string): string {
    return this.agentStream.tidyAnswer(text);
  }

  private pushAssistantBubble(content: string, tokens?: number, grounded?: boolean): void {
    this.messages.push({
      role: 'assistant',
      content: this.tidyAnswer(content),
      time: this.getCurrentTime(),
      tokens: tokens,
      grounded: typeof grounded === 'boolean' ? grounded : undefined
    });
    this.cdr.detectChanges();
    this.scrollToBottom();
  }

  stopGeneration() {
    if (this.streamController) {
      // In-flight token stream.
      this.streamController.abort();
      return;
    }
    if (this.chatSubscription) {
      this.chatSubscription.unsubscribe();
      this.isLoading = false;
      this.messages.push({
        role: 'system',
        content: this.i18n.t('chat.stop'),
        time: this.getCurrentTime()
      });
      this.scrollToBottom();
    }
  }

  // --- Auth Methods ---
  openAuthModal(isLogin: boolean) {
    this.isLoginMode = isLogin;
    this.showAuthModal = true;
    this.authError = '';
    this.authSuccess = false;
  }

  closeAuthModal() {
    this.showAuthModal = false;
  }

  submitAuth() {
    this.authError = '';
    if (this.isLoginMode) {
      this.authService.login(this.authForm.email, this.authForm.motDePasse).subscribe({
        next: () => {
          this.closeAuthModal();
        },
        error: () => {
          this.authError = 'Email ou mot de passe incorrect.';
        }
      });
    } else {
      this.authService.registerClient(this.authForm.nom, this.authForm.prenom, this.authForm.email, this.authForm.motDePasse).subscribe({
        next: () => {
          this.isLoginMode = true;
          this.authSuccess = true;
          this.authError = this.i18n.t('auth.created');
        },
        error: (err) => {
          this.authError = err.error?.error || "Erreur lors de l'inscription.";
        }
      });
    }
  }

  logout() {
    this.authService.logout();
    this.tokensLimite = 3000;
    this.messagesLimite = 20;
    this.messages = [];
    this.addWelcomeMessage();
  }
}
