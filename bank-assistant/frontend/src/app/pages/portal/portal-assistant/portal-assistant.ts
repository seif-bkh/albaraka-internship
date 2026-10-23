import { Component, ElementRef, ViewChild, AfterViewChecked, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslationService } from '../../../services/translation.service';
import { AuthService } from '../../../services/auth.service';
import { ClientService, ClientConversation, ClientConversationMessage } from '../../../services/client.service';
import { AgentStreamService, StreamHandle } from '../../../services/agent-stream.service';

interface AssistMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  time: string;
  streaming?: boolean;
  /** false = the assistant refused or had nothing reliable to answer from. */
  grounded?: boolean;
}

/**
 * Portal assistant with a ChatGPT/DeepSeek-style layout: a conversations
 * sidebar fed by the client's REAL conversation history (conversations linked
 * to their account via client_id) and a central streaming thread.
 */
@Component({
  selector: 'app-portal-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './portal-assistant.html',
  styleUrl: './portal-assistant.css'
})
export class PortalAssistantComponent implements OnInit, AfterViewChecked {
  @ViewChild('thread') private thread?: ElementRef;

  private readonly i18n = inject(TranslationService);
  private readonly auth = inject(AuthService);
  private readonly api = inject(ClientService);
  private readonly agent = inject(AgentStreamService);
  private readonly cdr = inject(ChangeDetectorRef);

  conversations: ClientConversation[] = [];
  activeConvId: number | null = null;
  sidebarOpen = false;

  messages: AssistMessage[] = [];
  draft = '';
  streaming = false;
  loadingHistory = false;
  limitReached = false;

  tokensConsommes = 0;
  tokensLimite = 3000;
  nombreMessages = 0;
  messagesLimite = 20;

  private sessionId = '';
  private handle: StreamHandle | null = null;
  private current: AssistMessage | null = null;

  readonly suggestions = ['chat.s1', 'chat.s2', 'chat.s3'];

  ngOnInit() {
    this.newConversation(false);
    this.loadConversations();
  }

  ngAfterViewChecked() {
    this.scrollBottom();
  }

  get firstName(): string {
    const email = this.auth.currentUserValue?.email ?? '';
    return email ? email.split('@')[0] : '';
  }

  get dir(): 'rtl' | 'ltr' {
    return this.i18n.lang() === 'ar' ? 'rtl' : 'ltr';
  }

  get currentEmpty(): boolean {
    return !!this.current && this.current.content.length === 0;
  }

  loadConversations() {
    this.api.conversations().subscribe({
      next: list => { this.conversations = list ?? []; this.cdr.detectChanges(); },
      error: () => { this.conversations = []; this.cdr.detectChanges(); }
    });
  }

  newConversation(openSidebarToggle: boolean = true) {
    this.sessionId = this.uuid();
    this.activeConvId = null;
    this.limitReached = false;
    this.messages = [{
      role: 'assistant',
      content: this.i18n.t('chat.welcome'),
      time: this.now()
    }];
    if (openSidebarToggle) {
      this.sidebarOpen = false;
    }
  }

  openConversation(conv: ClientConversation) {
    this.sidebarOpen = false;
    this.activeConvId = conv.id;
    this.sessionId = conv.sessionId;
    this.loadingHistory = true;
    this.api.conversationMessages(conv.id).subscribe({
      next: (rows: ClientConversationMessage[]) => {
        this.messages = [];
        for (const r of rows ?? []) {
          if (r.question) {
            this.messages.push({ role: 'user', content: r.question, time: this.fmt(r.dateCreation) });
          }
          if (r.reponse) {
            this.messages.push({
              role: 'assistant',
              content: this.agent.tidyAnswer(r.reponse),
              time: this.fmt(r.dateCreation)
            });
          }
        }
        if (this.messages.length === 0) {
          this.messages.push({ role: 'assistant', content: this.i18n.t('chat.welcome'), time: this.now() });
        }
        this.loadingHistory = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loadingHistory = false;
        this.cdr.detectChanges();
        this.messages = [{ role: 'assistant', content: this.i18n.t('chat.welcome'), time: this.now() }];
      }
    });
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  askSuggestion(key: string) {
    this.draft = this.i18n.t(key);
    this.send();
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

    this.handle = this.agent.send(this.sessionId, text, this.replyLanguageFor(text), this.auth.getAccessToken(), {
      onDelta: t => {
        if (this.current) { this.current.content += t; this.cdr.detectChanges(); }
      },
      onDone: res => {
        if (!this.current) { return; }
        this.current.streaming = false;
        if (res.sessionId && res.sessionId !== this.sessionId) {
          this.sessionId = res.sessionId;
        }
        this.tokensConsommes = res.tokensConsommes ?? this.tokensConsommes;
        this.nombreMessages = res.nombreMessages ?? this.nombreMessages;
        this.tokensLimite = res.tokensLimite || this.tokensLimite;
        this.messagesLimite = res.messagesLimite || this.messagesLimite;
        if (res.reponse) { this.current.content = res.reponse; }
        this.current.grounded = typeof res.grounded === 'boolean' ? res.grounded : undefined;
        this.current.content = this.agent.tidyAnswer(this.current.content);
        if (this.tokensConsommes >= this.tokensLimite || this.nombreMessages >= this.messagesLimite) {
          this.limitReached = true;
        }
        this.current = null;
        this.streaming = false;
        this.cdr.detectChanges();
        // A new conversation may have just been persisted server-side.
        if (this.activeConvId === null) {
          this.loadConversations();
        }
      },
      onError: message => {
        if (this.current) {
          this.current.content = message;
          this.current.streaming = false;
        }
        this.current = null;
        this.streaming = false;
        this.cdr.detectChanges();
      },
      onFallback: () => {
        if (this.current) {
          const idx = this.messages.indexOf(this.current);
          if (idx >= 0) { this.messages.splice(idx, 1); }
          this.current = null;
        }
        this.streaming = false;
        this.cdr.detectChanges();
      },
      onAborted: () => {
        if (this.current) { this.current.streaming = false; }
        this.current = null;
        this.streaming = false;
        this.messages.push({ role: 'system', content: this.i18n.t('chat.stop'), time: this.now() });
        this.cdr.detectChanges();
      }
    });
  }

  stop() {
    this.handle?.abort();
  }

  /** The reply language follows the typed message, not the UI selector. */
  private replyLanguageFor(text: string): string {
    const arabic = (text.match(/[\u0600-\u06FF]/g) || []).length;
    const latin = (text.match(/[A-Za-z\u00C0-\u00FF]/g) || []).length;
    return arabic > latin ? 'ar' : (latin > 0 ? 'auto' : this.i18n.lang());
  }

  private scrollBottom() {
    try {
      if (this.thread) {
        this.thread.nativeElement.scrollTop = this.thread.nativeElement.scrollHeight;
      }
    } catch { /* noop */ }
  }

  private fmt(iso?: string): string {
    if (!iso) { return this.now(); }
    const d = new Date(iso);
    return isNaN(d.getTime()) ? this.now()
      : `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;
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
