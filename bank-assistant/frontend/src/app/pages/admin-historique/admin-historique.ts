import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConversationService, ConversationSummary, ConversationDetail, MessageDetail } from '../../services/conversation.service';

type ViewMode = 'list' | 'detail';

/** Cadence of the live refresh: an exchange settles in seconds, this reads as instant. */
const LIVE_POLL_MS = 10000;


@Component({
  selector: 'app-admin-historique',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './admin-historique.html',
  styleUrl: './admin-historique.css',
})
export class AdminHistorique implements OnInit, OnDestroy {
  Math = Math;

  /** Direct mode: the list (and an open conversation) keep re-reading themselves. */
  autoRefresh = true;
  readonly livePollMs = LIVE_POLL_MS;
  private timer: ReturnType<typeof setInterval> | null = null;
  private visible = true;

  viewMode: ViewMode = 'list';

  conversations: ConversationSummary[] = [];
  loadingList = false;
  listError = '';
  totalConversations = 0;
  currentPage = 0;
  pageSize = 20;

  filtreLangue = '';
  filtreDebut = '';
  filtreFin = '';

  selectedConversation: ConversationDetail | null = null;
  loadingDetail = false;
  detailError = '';

  constructor(private convService: ConversationService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.loadConversations();
    this.setupPolling();
    // A backgrounded tab has no reason to keep hammering the endpoint.
    document.addEventListener('visibilitychange', this.onVisibility);
  }

  ngOnDestroy() {
    this.stopPolling();
    document.removeEventListener('visibilitychange', this.onVisibility);
  }

  private readonly onVisibility = () => {
    this.visible = document.visibilityState === 'visible';
    if (!this.visible) { this.stopPolling(); } else { this.setupPolling(); }
  };

  toggleAutoRefresh() {
    this.autoRefresh = !this.autoRefresh;
    if (this.autoRefresh) { this.refresh(); }
    this.setupPolling();
  }

  private setupPolling() {
    this.stopPolling();
    if (!this.autoRefresh || !this.visible) { return; }
    this.timer = setInterval(() => this.refresh(true), LIVE_POLL_MS);
  }

  private stopPolling() {
    if (this.timer) { clearInterval(this.timer); this.timer = null; }
  }

  /** Silent re-read of whatever the admin is looking at — no spinner, no lost scroll. */
  private refresh(silent = false) {
    if (this.viewMode === 'detail' && this.selectedConversation) {
      this.reloadMessages(this.selectedConversation.conversationId, silent);
    } else {
      this.loadConversations(silent);
    }
  }

  loadConversations(silent = false) {
    if (!silent) {
      this.loadingList = true;
      this.listError = '';
    }
    this.cdr.detectChanges();
    this.convService.getConversations({
      langue: this.filtreLangue || undefined,
      debut: this.filtreDebut ? new Date(this.filtreDebut).toISOString() : undefined,
      fin: this.filtreFin ? new Date(this.filtreFin).toISOString() : undefined,
      page: this.currentPage,
      size: this.pageSize,
    }).subscribe({
      next: (data) => {
        this.conversations = data.content || [];
        this.totalConversations = data.totalElements || 0;
        this.loadingList = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Erreur chargement conversations:', err);
        this.loadingList = false;
        if (!silent) {
          this.listError = 'Impossible de charger l\'historique. Vérifiez votre connexion.';
        }
        this.cdr.detectChanges();
      }
    });
  }

  applyFilters() {
    this.currentPage = 0;
    this.loadConversations();
  }

  resetFilters() {
    this.filtreLangue = '';
    this.filtreDebut = '';
    this.filtreFin = '';
    this.currentPage = 0;
    this.loadConversations();
  }

  openDetail(conv: ConversationSummary) {
    this.viewMode = 'detail';
    this.loadingDetail = true;
    this.detailError = '';
    this.selectedConversation = null;
    this.cdr.detectChanges();
    this.convService.getConversationMessages(conv.id).subscribe({
      next: (data) => {
        this.selectedConversation = data;
        this.loadingDetail = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Erreur chargement messages:', err);
        this.loadingDetail = false;
        this.detailError = 'Impossible de charger le fil de discussion.';
        this.cdr.detectChanges();
      }
    });
  }

  /** Live update of an open thread: a row marked "en cours" becomes the answer here. */
  private reloadMessages(conversationId: number, silent = false) {
    this.convService.getConversationMessages(conversationId).subscribe({
      next: (data) => {
        this.selectedConversation = data;
        this.cdr.detectChanges();
      },
      error: () => {
        if (!silent) {
          this.detailError = 'Impossible de recharger le fil de discussion.';
          this.cdr.detectChanges();
        }
      }
    });
  }

  /** True while an exchange of this conversation is still being generated. */
  hasInProgress(): boolean {
    return !!this.selectedConversation?.messages.some(m => m.observability?.status === 'IN_PROGRESS');
  }

  latencyOf(msg: MessageDetail): string {
    const ms = msg.observability?.latencyMs;
    if (ms === null || ms === undefined) { return '—'; }
    return ms >= 1000 ? (ms / 1000).toFixed(1) + ' s' : ms + ' ms';
  }

  scoreOf(value: number | null | undefined): string {
    return value === null || value === undefined ? '—' : Math.round(value * 100) + ' %';
  }

  backToList() {
    this.viewMode = 'list';
    this.selectedConversation = null;
    this.detailError = '';
    this.cdr.detectChanges();
  }

  prevPage() {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadConversations();
    }
  }

  nextPage() {
    const maxPage = Math.ceil(this.totalConversations / this.pageSize) - 1;
    if (this.currentPage < maxPage) {
      this.currentPage++;
      this.loadConversations();
    }
  }

  getLangueBadgeClass(langue: string): string {
    switch (langue?.toLowerCase()) {
      case 'fr': return 'badge-fr';
      case 'ar': return 'badge-ar';
      case 'en': return 'badge-en';
      default: return 'badge-other';
    }
  }

  getIntentionLabel(intention: string): string {
    const map: Record<string, string> = {
      'theologique': '🕌 Théologique',
      'commercial': '🏦 Commercial',
      'reglementaire': '⚖️ Réglementaire',
      'salutation': '👋 Salutation',
      'hors_perimetre': '❌ Hors périmètre',
      'cache_hit': '⚡ Cache',
    };
    return map[intention] || intention || 'N/A';
  }

  getDureeSession(debut: string, fin: string): string {
    if (!debut || !fin) return 'N/A';
    const diff = new Date(fin).getTime() - new Date(debut).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return '< 1 min';
    return `${mins} min`;
  }

  trackByMessage(index: number, msg: MessageDetail): number {
    return msg.id;
  }
}
