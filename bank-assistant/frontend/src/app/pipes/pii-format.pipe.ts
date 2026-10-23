import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Pipe({
  name: 'piiFormat',
  standalone: true
})
export class PiiFormatPipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {}

  transform(value: string | null | undefined): SafeHtml {
    if (!value) return '';

    // 1. Escape basic HTML entities first so user/model content is safe
    let formatted = value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    // 2. Replace known PII tokens with styled chips
    formatted = formatted
      .replace(/\[EMAIL\]/g, '<span class="pii-chip pii-email" title="Adresse e-mail caviardée">[EMAIL]</span>')
      .replace(/\[PHONE\]/g, '<span class="pii-chip pii-phone" title="Numéro de téléphone caviardé">[PHONE]</span>')
      .replace(/\[RIB\]/g, '<span class="pii-chip pii-rib" title="RIB/IBAN caviardé">[RIB]</span>')
      .replace(/\[CIN\]/g, '<span class="pii-chip pii-cin" title="CIN caviardé">[CIN]</span>');

    // 3. Replace citation references like [S1], [S2] with interactive citation badges
    formatted = formatted.replace(
      /\[(S\d+)\]/g,
      '<button type="button" class="citation-badge" data-source="$1" title="Aller au fragment $1">[$1]</button>'
    );

    return this.sanitizer.bypassSecurityTrustHtml(formatted);
  }
}
