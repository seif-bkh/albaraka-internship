import { Component, ElementRef, ViewChild, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslationService } from '../../services/translation.service';
import { ChatWidgetService } from '../../services/chat-widget.service';
import { AgentStreamService, StreamHandle } from '../../services/agent-stream.service';
import { AuthService } from '../../services/auth.service';

interface WidgetMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  time: string;
  streaming?: boolean;
  /** False = the assistant refused or had nothing reliable to answer from. */
  grounded?: boolean;
  tokens?: number;
}

/**
 * Floating "talk to the agent" widget for the public landing page.
 * Streams answers from the same public endpoint as /chat (AgentStreamService),
 * keeps its session in localStorage and follows the FR/AR/EN switcher.
 *
 * <p>The panel is a fixed-size box (see chat-widget.css): the answer is expected
 * to fit it, the thread scrolls when it does not, and a bubble never grows its own
 * scrollbar. No sources, no citations, no link list: what the assistant read is
 * recorded for the bank's reviewers, not shown to the customer.
 */
@Component({
  selector: 'app-chat-widget',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './chat-widget.html',
  styleUrl: './chat-widget.css'
})
export class ChatWidgetComponent {
  readonly widget = inject(ChatWidgetService);
  private readonly i18n = inject(TranslationService);
  private readonly agent = inject(AgentStreamService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild('scroller') private scroller?: ElementRef;

  messages: WidgetMessage[] = [];
  draft = '';
  streaming = false;
  limitReached = false;

  tokensConsommes = 0;
  tokensLimite = 3000;
  nombreMessages = 0;
  messagesLimite = 20;

  private sessionId = '';
  private handle: StreamHandle | null = null;
  private current: WidgetMessage | null = null;

  get dir(): 'rtl' | 'ltr' {
    return this.i18n.lang() === 'ar' ? 'rtl' : 'ltr';
  }

  /** True while we wait for the first streamed token (typing dots). */
  get currentEmpty(): boolean {
    return !!this.current && this.current.content.length === 0;
  }

  get lang(): string {
    return this.i18n.lang();
  }

  setLang(lang: string) {
    this.i18n.setLang(lang);
  }

  toggle() {
    this.widget.toggle();
    if (this.widget.isOpen()) {
      if (!this.sessionId) {
        this.sessionId = localStorage.getItem('ab_widget_session') || this.uuid();
        localStorage.setItem('ab_widget_session', this.sessionId);
      }
      if (this.messages.length === 0) {
        this.messages.push({ role: 'assistant', content: this.i18n.t('chat.welcome'), time: this.now() });
      }
      setTimeout(() => this.scrollBottom(), 60);
    }
  }

  close() {
    this.widget.closeWidget();
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  send() {
    const text = this.draft.trim();
    if (!text || this.streaming || this.limitReached) {
      return;
    }
    this.draft = '';
    this.messages.push({ role: 'user', content: text, time: this.now() });
    this.current = { role: 'assistant', content: '', time: this.now(), streaming: true };
    this.messages.push(this.current);
    this.streaming = true;
    setTimeout(() => this.scrollBottom(), 30);

    this.handle = this.agent.send(
      this.sessionId,
      text,
      this.replyLanguageFor(text),
      this.auth.getAccessToken(),
      {
        onDelta: (t) => {
          if (this.current) {
            this.current.content += t;
            this.cdr.detectChanges();
            this.scrollBottom();
          }
        },
        onDone: (res) => this.finish(res),
        onError: (message) => this.fail(message),
        onFallback: () => this.dropCurrent(),
        onAborted: () => this.finishPartial()
      }
    );
  }

  stop() {
    this.handle?.abort();
  }

  private finish(res: any) {
    if (!this.current) { return; }
    this.current.streaming = false;
    if (res.sessionId && res.sessionId !== this.sessionId) {
      this.sessionId = res.sessionId;
      localStorage.setItem('ab_widget_session', this.sessionId);
    }
    this.tokensConsommes = res.tokensConsommes ?? this.tokensConsommes;
    this.nombreMessages = res.nombreMessages ?? this.nombreMessages;
    this.tokensLimite = res.tokensLimite || this.tokensLimite;
    this.messagesLimite = res.messagesLimite || this.messagesLimite;
    if (res.reponse) {
      this.current.content = res.reponse;
    }
    this.current.grounded = typeof res.grounded === 'boolean' ? res.grounded : undefined;
    // Provenance is not shown to customers by design; tidyAnswer only normalises
    // the text itself (the backend already stripped citations before sending it).
    this.current.content = this.agent.tidyAnswer(this.current.content);
    if (this.tokensConsommes >= this.tokensLimite || this.nombreMessages >= this.messagesLimite) {
      this.limitReached = true;
    }
    this.current = null;
    this.streaming = false;
    this.cdr.detectChanges();
    this.scrollBottom();
  }

  private fail(message: string) {
    if (this.current) {
      this.current.content = message;
      this.current.streaming = false;
    }
    this.current = null;
    this.streaming = false;
    this.cdr.detectChanges();
    this.scrollBottom();
  }

  private dropCurrent() {
    if (this.current) {
      const idx = this.messages.indexOf(this.current);
      if (idx >= 0) { this.messages.splice(idx, 1); }
      this.current = null;
    }
    this.streaming = false;
  }

  private finishPartial() {
    if (this.current) {
      this.current.streaming = false;
    }
    this.current = null;
    this.streaming = false;
    this.messages.push({ role: 'system', content: this.i18n.t('chat.stop'), time: this.now() });
    this.cdr.detectChanges();
    this.scrollBottom();
  }

  /** The reply language follows the typed message, not the UI selector. */
  private replyLanguageFor(text: string): string {
    const arabic = (text.match(/[\u0600-\u06FF]/g) || []).length;
    const latin = (text.match(/[A-Za-z\u00C0-\u00FF]/g) || []).length;
    return arabic > latin ? 'ar' : (latin > 0 ? 'auto' : this.i18n.lang());
  }

  private scrollBottom() {
    try {
      if (this.scroller) {
        this.scroller.nativeElement.scrollTop = this.scroller.nativeElement.scrollHeight;
      }
    } catch { /* noop */ }
  }

  private now(): string {
    const d = new Date();
    return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0');
  }

  private uuid(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
      const r = Math.random() * 16 | 0;
      return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
  }
}
