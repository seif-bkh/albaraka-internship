import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import {
  GovernanceService, CatalogExemplar, CatalogExemplarRequest, CatalogRelease,
} from '../../services/governance.service';

/**
 * Classifier taxonomy (Phase 5.4): curate exemplar phrasings per domain/intent
 * in a working copy, then seal them as a versioned release the agent loads.
 * Edits are inert until sealed; the newest seal is the version in force.
 */
@Component({
  selector: 'app-admin-taxonomy',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-taxonomy.html',
  styleUrl: './admin-taxonomy.css',
})
export class AdminTaxonomy implements OnInit {
  exemplars: CatalogExemplar[] = [];
  releases: CatalogRelease[] = [];
  domains: string[] = [];
  intents: string[] = [];
  filterDomain = '';
  showInactive = false;
  loading = false;
  errorMessage = '';
  notice = '';
  canWrite = false;

  showForm = false;
  editing: CatalogExemplar | null = null;
  form: CatalogExemplarRequest = this.emptyForm();
  saving = false;
  formError = '';

  sealNote = '';
  sealing = false;

  constructor(private gov: GovernanceService, private auth: AuthService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.canWrite = this.auth.hasRole('SUPER_ADMIN') || this.auth.hasRole('ADMIN_DOCUMENTS');
    this.gov.taxonomyVocabulary().subscribe({
      next: v => { this.domains = v.domains.sort(); this.intents = v.intents.sort(); this.cdr.detectChanges(); },
      error: () => { /* non-blocking */ },
    });
    this.load();
  }

  get visible(): CatalogExemplar[] {
    return this.showInactive ? this.exemplars : this.exemplars.filter(e => e.active);
  }

  get activeCount(): number { return this.exemplars.filter(e => e.active).length; }

  get publishedRelease(): CatalogRelease | undefined { return this.releases.find(r => r.published); }

  /** True when the working copy changed after the latest seal (an unsealed edit exists). */
  get hasUnsealedChanges(): boolean {
    const latest = this.releases[0];
    if (!latest) return this.exemplars.length > 0;
    return this.exemplars.some(e => e.updatedAt > latest.sealedAt);
  }

  load() {
    this.loading = true;
    this.errorMessage = '';
    this.gov.listExemplars(this.filterDomain || undefined).subscribe({
      next: data => { this.exemplars = data; this.loading = false; this.cdr.detectChanges(); },
      error: err => { this.loading = false; this.errorMessage = err?.error?.message || 'Impossible de charger la taxonomie.'; this.cdr.detectChanges(); },
    });
    this.gov.listReleases().subscribe({
      next: r => { this.releases = r; this.cdr.detectChanges(); },
      error: () => { this.releases = []; this.cdr.detectChanges(); },
    });
  }

  openCreate() { this.editing = null; this.form = this.emptyForm(); this.formError = ''; this.showForm = true; }

  openEdit(e: CatalogExemplar) {
    this.editing = e;
    this.form = { domain: e.domain, intent: e.intent, text: e.text, language: e.language, topic: e.topic, slots: e.slots };
    this.formError = '';
    this.showForm = true;
  }

  save() {
    this.saving = true;
    this.formError = '';
    const call = this.editing ? this.gov.updateExemplar(this.editing.id, this.form) : this.gov.createExemplar(this.form);
    call.subscribe({
      next: () => { this.saving = false; this.showForm = false; this.load(); },
      error: err => {
        this.saving = false;
        this.formError = err?.error?.message || (err?.error?.fields ? Object.values(err.error.fields).join(' · ') : 'Enregistrement impossible.');
        this.cdr.detectChanges();
      },
    });
  }

  toggleActive(e: CatalogExemplar) {
    const call = e.active ? this.gov.deactivateExemplar(e.id) : this.gov.reactivateExemplar(e.id);
    call.subscribe({ next: () => this.load(), error: err => { this.errorMessage = err?.error?.message || 'Opération impossible.'; this.cdr.detectChanges(); } });
  }

  seal() {
    if (!window.confirm(`Sceller ${this.activeCount} exemplaires actifs dans une nouvelle version du catalogue ?`)) return;
    this.sealing = true;
    this.notice = '';
    this.gov.sealRelease(this.sealNote).subscribe({
      next: r => {
        this.sealing = false;
        this.sealNote = '';
        this.notice = `Version ${r.version} scellée : c'est désormais la version en vigueur.`;
        this.load();
      },
      error: err => { this.sealing = false; this.errorMessage = err?.error?.message || 'Scellement impossible.'; this.cdr.detectChanges(); },
    });
  }

  private emptyForm(): CatalogExemplarRequest {
    return { domain: this.filterDomain || 'financement', intent: 'describe', text: '', language: 'fr', topic: '', slots: '' };
  }
}
