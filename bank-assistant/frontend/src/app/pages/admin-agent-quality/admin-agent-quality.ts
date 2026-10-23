import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../services/auth.service';
import {
  AgentAdminService, AnswerResult, ChunkLookupResult, RetrievalResult,
} from '../../services/agent-admin.service';

type Panel = 'answer' | 'retrieval' | 'chunks';

/**
 * Assistant IA › Qualité des réponses (slice 1).
 *
 * Three questions, in the order an operator asks them when something looks wrong:
 *   - what did it answer, and which verbatim passage backs each claim? (spends)
 *   - was the right passage even retrieved? (one embedding call)
 *   - does this quote exist in the corpus at all? (searches indexed source text)
 *
 * The claim → quote table is the centrepiece: it is the only part the service
 * gate-checks. The free-text answer is shown as prose, never badged as verified.
 */
@Component({
  selector: 'app-admin-agent-quality',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-agent-quality.html',
  styleUrl: './admin-agent-quality.css',
})
export class AdminAgentQuality implements OnInit {

  panel: Panel = 'answer';

  // --- shared question + knobs
  question = '';
  k: number | null = null;
  mode = '';
  langFilter = '';
  neighborRadius: number | null = null;
  includeExcerpts = true;

  // --- quote lookup (searches indexed corpus text)
  quoteText = '';
  quoteLimit: number | null = null;

  // --- results
  answerResult: AnswerResult | null = null;
  retrievalResult: RetrievalResult | null = null;
  chunkResult: ChunkLookupResult | null = null;

  showRawAnswer = false;
  showRawRetrieval = false;
  showRawChunks = false;

  loading = false;
  errorMessage = '';

  canSpend = false;
  canRead = false;

  readonly modes = [
    { value: '', label: 'Défaut du service' },
    { value: 'vector', label: 'vector' },
    { value: 'rrf', label: 'rrf' },
    { value: 'blend', label: 'blend' },
  ];
  readonly langs = [
    { value: '', label: 'Toutes' },
    { value: 'fr', label: 'fr' },
    { value: 'ar', label: 'ar' },
    { value: 'en', label: 'en' },
  ];

  constructor(
    private agent: AgentAdminService,
    private auth: AuthService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.canSpend = this.auth.hasPermission('RAGLAB_CONSOLE_RUN');
    // Exact quote lookup is a no-model, read-only operation; its historical
    // Viewer access is retained while query/answer operations require Run.
    this.canRead = this.auth.hasPermission('RAGLAB_CONSOLE_VIEW');
  }

  select(panel: Panel) {
    this.panel = panel;
    this.errorMessage = '';
  }

  // ------------------------------------------------------------------ actions

  /** Grounded answer — costs an embedding and a chat call. */
  ask() {
    if (!this.canSpend) { return; }
    if (!this.question.trim()) {
      this.errorMessage = 'Posez une question.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.answerResult = null;
    this.agent.answer({
      question: this.question.trim(),
      k: this.k,
      mode: this.mode || null,
      langFilter: this.langFilter || null,
      queryLang: this.langFilter || null,
      includeExcerpts: this.includeExcerpts,
    }).subscribe({
      next: res => {
        this.answerResult = res;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.errorMessage = this.describeError(err);
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  /** Retrieval only — separates "the passage was not there" from "the model refused". */
  retrieve() {
    if (!this.canSpend) { return; }
    if (!this.question.trim()) {
      this.errorMessage = 'Posez une question.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.retrievalResult = null;
    this.agent.retrieval({
      question: this.question.trim(),
      k: this.k,
      mode: this.mode || null,
      langFilter: this.langFilter || null,
      neighborRadius: this.neighborRadius,
    }).subscribe({
      next: res => {
        this.retrievalResult = res;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.errorMessage = this.describeError(err);
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  /** Exact quote lookup — no model call, retained under RAGLab View. */
  lookup() {
    if (!this.canRead) { return; }
    if (!this.quoteText.trim()) {
      this.errorMessage = 'Saisissez le texte ou l\'extrait à chercher.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.chunkResult = null;
    this.agent.searchChunks(this.quoteText.trim(), this.quoteLimit).subscribe({
      next: res => {
        this.chunkResult = res;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.errorMessage = this.describeError(err);
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  /** Jump from a refusal straight to the retrieval probe, same question. */
  probeRetrieval() {
    if (!this.canSpend) { return; }
    this.select('retrieval');
    this.retrieve();
  }

  // ------------------------------------------------------------- presentation

  statusBadge(): string {
    const status = this.answerResult?.view?.status;
    if (status === 'answered') { return 'success'; }
    if (status === 'refused') { return 'error'; }
    if (status === 'greeting') { return 'info'; }
    return 'warning';
  }

  statusLabel(): string {
    const status = this.answerResult?.view?.status;
    if (status === 'answered') { return 'répondu'; }
    if (status === 'refused') { return 'refusé'; }
    if (status === 'greeting') { return 'salutation'; }
    return status ? status : 'inconnu';
  }

  /** What an operator should do about a refusal, keyed on the machine reason. */
  refusalHint(reason: string | null | undefined): string {
    switch (reason) {
      case 'insufficient_evidence':
        return 'Les extraits trouvés ne soutenaient pas la réponse. Sondez la récupération (onglet Récupération) : le bon passage est-il seulement retourné ?';
      case 'no_context':
        return 'Rien n\'a été récupéré : l\'index est peut-être vide ou construit pour un autre profil. Vérifiez la santé du service.';
      case 'private_or_live_request':
        return 'La demande a été jugée privée ou dépendante d\'une donnée personnelle : le service refuse par conception.';
      case 'invalid_output':
        return 'La sortie du modèle n\'a pas passé le contrôle des citations. Reformulez la question ou inspectez le corpus.';
      case 'empty_index':
        return 'L\'index est vide : il faut lancer une ingestion.';
      case 'missing_api_key':
        return 'Une clé de fournisseur manque sur le service.';
      case 'retrieval_refused':
        return 'La récupération a été refusée avant toute génération.';
      default:
        return 'Raison non répertoriée : consultez la réponse brute ci-dessous.';
    }
  }

  rawAnswer(): string {
    return this.answerResult?.raw ? JSON.stringify(this.answerResult.raw, null, 2) : '';
  }

  rawRetrieval(): string {
    return this.retrievalResult?.raw ? JSON.stringify(this.retrievalResult.raw, null, 2) : '';
  }

  rawChunks(): string {
    return this.chunkResult?.raw ? JSON.stringify(this.chunkResult.raw, null, 2) : '';
  }

  describeError(err: unknown): string {
    const e = err as {
      error?: { reason?: string; message?: string; error?: string; fields?: Record<string, string> };
      message?: string;
    };
    const fields = e?.error?.fields ? Object.values(e.error.fields).join(' ') : '';
    const reason = e?.error?.reason ?? e?.error?.error;
    const message = e?.error?.message ?? e?.message ?? 'Erreur inconnue';
    const text = fields ? `${message} (${fields})` : message;
    return reason ? `[${reason}] ${text}` : text;
  }

  trackClaim(index: number): number {
    return index;
  }
}
