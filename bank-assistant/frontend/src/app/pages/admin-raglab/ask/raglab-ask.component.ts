import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RaglabService, RaglabAnswerResponse, parseRaglabError, ParsedRaglabError } from '../../../services/raglab.service';
import { PiiFormatPipe } from '../../../pipes/pii-format.pipe';

export interface ChatTurn {
  id: string;
  question: string;
  timestamp: Date;
  response?: RaglabAnswerResponse;
  loading?: boolean;
  loadingStep?: string;
  error?: ParsedRaglabError;
  showRawPreview?: boolean;
  claimsExpanded?: boolean;
  sourcesExpanded?: boolean;
}

@Component({
  selector: 'app-raglab-ask',
  standalone: true,
  imports: [CommonModule, FormsModule, PiiFormatPipe],
  templateUrl: './raglab-ask.component.html',
  styleUrl: './raglab-ask.component.css'
})
export class RaglabAskComponent implements OnInit {
  private cdr = inject(ChangeDetectorRef);
  public raglab = inject(RaglabService);

  questionInput = '';
  queryLang = ''; // empty = auto-detect
  k: number | null = null;
  mode = '';
  includeExcerpts = true;

  showAdvanced = false;
  copiedQuoteIndex: string | null = null;

  turns: ChatTurn[] = [];

  ngOnInit(): void {
    // Initial state
  }

  /**
   * A turn is append-only and identified by its id, so tracking by it lets Angular keep
   * the existing subtrees when a new answer arrives instead of rebuilding every bubble.
   */
  trackTurn = (_: number, turn: ChatTurn): string => turn.id;
  trackIndex = (index: number): number => index;
  trackSource = (_: number, src: { source_id: string }): string => src.source_id;

  toggleAdvanced(): void {
    this.showAdvanced = !this.showAdvanced;
  }

  onSubmit(): void {
    const q = this.questionInput.trim();
    if (!q || this.raglab.isSealed()) return;

    const turnId = 'turn_' + Date.now();
    const newTurn: ChatTurn = {
      id: turnId,
      question: q,
      timestamp: new Date(),
      loading: true,
      loadingStep: 'Recherche des fragments pertinents dans le corpus…',
      claimsExpanded: true,
      sourcesExpanded: true
    };

    this.turns.push(newTurn);
    this.questionInput = '';
    this.cdr.markForCheck();

    // Honest indeterminate loader steps
    const stepTimer = setTimeout(() => {
      if (newTurn.loading) {
        newTurn.loadingStep = 'Vérification mot à mot des citations et des montants…';
        this.cdr.markForCheck();
      }
    }, 1800);

    const payload = {
      question: q,
      k: this.k || null,
      mode: this.mode || null,
      query_lang: this.queryLang || null,
      include_excerpts: this.includeExcerpts
    };

    this.raglab.answer(payload).subscribe({
      next: (res) => {
        clearTimeout(stepTimer);
        newTurn.loading = false;
        newTurn.response = res;
        this.cdr.markForCheck();
      },
      error: (err) => {
        clearTimeout(stepTimer);
        newTurn.loading = false;
        newTurn.error = parseRaglabError(err);
        this.cdr.markForCheck();
      }
    });
  }

  clearChat(): void {
    this.turns = [];
    this.cdr.markForCheck();
  }

  copyQuote(quote: string, key: string): void {
    if (!quote) return;
    const cleaned = this.cleanQuote(quote);
    navigator.clipboard.writeText(cleaned).then(() => {
      this.copiedQuoteIndex = key;
      this.cdr.markForCheck();
      setTimeout(() => {
        if (this.copiedQuoteIndex === key) {
          this.copiedQuoteIndex = null;
          this.cdr.markForCheck();
        }
      }, 2000);
    });
  }

  scrollToSource(turnId: string, sourceId: string): void {
    const el = document.getElementById(`${turnId}_src_${sourceId}`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      el.classList.add('source-highlight');
      setTimeout(() => el.classList.remove('source-highlight'), 1800);
    }
  }

  onAnswerClick(event: MouseEvent, turnId: string): void {
    const target = event.target as HTMLElement;
    const badge = target.closest('.citation-badge, .citation-tag') as HTMLElement | null;
    if (badge) {
      const srcId = badge.getAttribute('data-source');
      if (srcId) {
        this.scrollToSource(turnId, srcId);
      }
    }
  }

  /**
   * Cleans quotes from raw extraction artifacts:
   * replaces sequences of whitespace and line breaks with single space
   * so sentences in Arabic and other languages flow horizontally.
   */
  cleanQuote(quote?: string): string {
    if (!quote) return '';
    return quote.trim().replace(/\s+/g, ' ');
  }

  /**
   * Normalizes document excerpt text for clean presentation:
   * removes raw markdown blockquote/header noise if present.
   */
  cleanExcerpt(text?: string): string {
    if (!text) return '';
    return text
      .replace(/^>\s*\*\*Context:\*\*\s*/gm, '')
      .replace(/^#{1,4}\s*/gm, '')
      .replace(/\r\n/g, '\n')
      .replace(/\n{2,}/g, '\n')
      .trim();
  }

  reasonLabel(reason?: string): string {
    switch (reason) {
      case 'insufficient_evidence':
        return 'Preuves documentaires insuffisantes';
      case 'no_context':
        return 'Aucun contexte trouvé dans le corpus';
      case 'invalid_output':
        return 'Format rejeté par le filtre de citation';
      case 'unsourced_number':
        return 'Nombre non attesté dans les citations';
      case 'private_or_live_request':
        return 'Données personnelles ou temps réel non accessibles';
      case 'smalltalk':
        return 'Échange de politesse';
      default:
        return reason || 'Non précisé';
    }
  }
}
