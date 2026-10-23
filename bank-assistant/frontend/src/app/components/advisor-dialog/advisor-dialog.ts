import { Component, EventEmitter, HostListener, Input, OnChanges, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslationService } from '../../services/translation.service';
import { ClientService, ClientProfile } from '../../services/client.service';

/**
 * Advisor contact dialog (P0): shows the bank's governed contact facts
 * (empty until configured — never hardcoded) and takes a callback request.
 */
@Component({
  selector: 'app-advisor-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './advisor-dialog.html',
  styleUrl: './advisor-dialog.css'
})
export class AdvisorDialogComponent implements OnInit, OnChanges {
  private readonly api = inject(ClientService);
  readonly i18n = inject(TranslationService);

  @Input() open = false;
  @Output() readonly openChange = new EventEmitter<boolean>();
  @Input() profile: ClientProfile | null = null;

  contacts: string[] = [];
  phone = '';
  time = '';
  message = '';

  sending = false;
  error = false;
  submitted = false;

  ngOnInit(): void {
    this.timeSlots = AdvisorDialogComponent.buildSlots();
  }

  private timeSlots: string[] = [];

  ngOnChanges(): void {
    if (this.open) {
      this.reset();
      this.api.advisorContacts().subscribe({
        next: c => { this.contacts = c; },
        error: () => { this.contacts = []; }
      });
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
    this.phone = this.profile?.telephone ?? '';
    this.time = '';
    this.message = '';
    this.sending = false;
    this.error = false;
    this.submitted = false;
  }

  submit(): void {
    if (this.sending) {
      return;
    }
    this.sending = true;
    this.error = false;
    this.api.createAdvisorRequest({
      kind: 'CALLBACK',
      telephone: this.phone,
      preferredTime: this.time || undefined,
      message: this.message || undefined
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
}
