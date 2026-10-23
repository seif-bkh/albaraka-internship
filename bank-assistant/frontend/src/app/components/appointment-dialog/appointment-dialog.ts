import { Component, EventEmitter, HostListener, Input, OnChanges, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslationService } from '../../services/translation.service';
import { ClientService } from '../../services/client.service';

/**
 * Appointment dialog (P0). Persists an EN_ATTENTE request — the advisor
 * confirms by phone (docs/UIUX_RESEARCH.md §2). Motif is stored as a machine
 * key (translated on display, language-independent in the DB).
 */
@Component({
  selector: 'app-appointment-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './appointment-dialog.html',
  styleUrl: './appointment-dialog.css'
})
export class AppointmentDialogComponent implements OnChanges {
  private readonly api = inject(ClientService);
  readonly i18n = inject(TranslationService);

  @Input() open = false;
  @Output() readonly openChange = new EventEmitter<boolean>();

  readonly motifs = ['FINANCEMENT', 'ACCOUNT', 'CARDS', 'OTHER'] as const;
  motif = 'FINANCEMENT';
  method = 'AGENCE';
  branch = '';
  date = '';
  time = '10:00';
  notes = '';

  sending = false;
  error = false;
  submitted = false;

  ngOnInit(): void {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const max = new Date();
    max.setDate(max.getDate() + 60);
    this.minDate = this.iso(tomorrow);
    this.maxDate = this.iso(max);
    this.timeSlots = AppointmentDialogComponent.buildSlots();
  }

  private minDate = '';
  private maxDate = '';
  timeSlots: string[] = [];

  ngOnChanges(): void {
    if (this.open) {
      this.reset();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open && !this.sending) {
      this.close();
    }
  }

  close(): void {
    if (this.open) {
      this.open = false;
      this.openChange.emit(false);
    }
  }

  private reset(): void {
    this.motif = 'FINANCEMENT';
    this.method = 'AGENCE';
    this.branch = '';
    this.date = '';
    this.time = '10:00';
    this.notes = '';
    this.sending = false;
    this.error = false;
    this.submitted = false;
  }

  motifLabel(key: string): string {
    const i18nKey =
      key === 'FINANCEMENT' ? 'appt.motif.financement'
        : key === 'ACCOUNT' ? 'appt.motif.account'
          : key === 'CARDS' ? 'appt.motif.cards'
            : 'appt.motif.other';
    return this.i18n.t(i18nKey);
  }

  submit(): void {
    if (this.sending || !this.date) {
      return;
    }
    this.sending = true;
    this.error = false;
    this.api.createAdvisorRequest({
      kind: 'APPOINTMENT',
      motif: this.motif,
      meetingMethod: this.method as 'AGENCE' | 'TELEPHONE',
      preferredBranch: this.branch || undefined,
      preferredDate: this.date,
      preferredTime: this.time,
      message: this.notes || undefined
    }).subscribe({
      next: () => {
        this.sending = false;
        this.submitted = true;
      },
      error: () => {
        this.sending = false;
        this.error = true;
      }
    });
  }

  private static buildSlots(): string[] {
    const slots: string[] = [];
    for (let h = 9; h <= 16; h++) {
      slots.push(`${String(h).padStart(2, '0')}:00`);
      if (h < 16) {
        slots.push(`${String(h).padStart(2, '0')}:30`);
      }
    }
    return slots;
  }

  private iso(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
