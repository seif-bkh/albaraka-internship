import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslationService } from '../../../services/translation.service';
import { ClientService, ClientProfile } from '../../../services/client.service';

/** Client profile: read-only identity + editable contact preferences. */
@Component({
  selector: 'app-portal-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, TranslatePipe],
  templateUrl: './portal-profile.html',
  styleUrl: './portal-profile.css'
})
export class PortalProfileComponent implements OnInit {
  private readonly api = inject(ClientService);
  private readonly cdr = inject(ChangeDetectorRef);
  readonly i18n = inject(TranslationService);

  profile: ClientProfile | null = null;
  editing = false;
  saving = false;
  saved = false;
  error = '';

  form = { telephone: '', adresse: '', ville: '', dateNaissance: '', languePreferee: 'fr' };

  ngOnInit() {
    this.load();
  }

  load() {
    this.api.me().subscribe({
      next: p => {
        this.profile = p;
        this.form = {
          telephone: p.telephone ?? '',
          adresse: p.adresse ?? '',
          ville: p.ville ?? '',
          dateNaissance: p.dateNaissance ?? '',
          languePreferee: (p.languePreferee || 'fr').toLowerCase()
        };
        this.cdr.detectChanges();
      },
      error: () => { this.error = 'load'; this.cdr.detectChanges(); }
    });
  }

  startEdit() {
    this.editing = true;
    this.saved = false;
    this.error = '';
  }

  cancelEdit() {
    this.editing = false;
    this.error = '';
    if (this.profile) {
      this.form = {
        telephone: this.profile.telephone ?? '',
        adresse: this.profile.adresse ?? '',
        ville: this.profile.ville ?? '',
        dateNaissance: this.profile.dateNaissance ?? '',
        languePreferee: (this.profile.languePreferee || 'fr').toLowerCase()
      };
    }
  }

  save() {
    this.saving = true;
    this.error = '';
    this.api.updateMe({
      telephone: this.form.telephone,
      adresse: this.form.adresse,
      ville: this.form.ville,
      dateNaissance: this.form.dateNaissance || undefined,
      languePreferee: this.form.languePreferee
    }).subscribe({
      next: p => {
        this.profile = p;
        this.editing = false;
        this.saving = false;
        this.saved = true;
        this.cdr.detectChanges();
      },
      error: () => {
        this.saving = false;
        this.error = 'save';
        this.cdr.detectChanges();
      }
    });
  }

  get dir(): 'rtl' | 'ltr' {
    return this.i18n.lang() === 'ar' ? 'rtl' : 'ltr';
  }
}
