import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import {
  GovernanceService, BusinessFact, BusinessFactRequest, BusinessFactAudit,
} from '../../services/governance.service';

/**
 * Governed business facts (Phase 5.4): the single place staff maintain the
 * figures the assistant may quote. Every write goes through the audited
 * /api/admin/facts API; nothing here is ever hard-deleted.
 */
@Component({
  selector: 'app-admin-facts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-facts.html',
  styleUrl: './admin-facts.css',
})
export class AdminFacts implements OnInit {
  facts: BusinessFact[] = [];
  domains: string[] = [];
  factTypes: string[] = [];
  filterDomain = '';
  showInactive = false;
  loading = false;
  errorMessage = '';
  canWrite = false;

  showForm = false;
  editing: BusinessFact | null = null;
  form: BusinessFactRequest = this.emptyForm();
  saving = false;
  formError = '';

  historyFor: BusinessFact | null = null;
  history: BusinessFactAudit[] = [];

  constructor(
    private gov: GovernanceService,
    private auth: AuthService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.canWrite = this.auth.hasRole('SUPER_ADMIN') || this.auth.hasRole('ADMIN_DOCUMENTS');
    this.gov.factsVocabulary().subscribe({
      next: v => { this.domains = v.domains.sort(); this.factTypes = v.factTypes.sort(); this.cdr.detectChanges(); },
      error: () => { /* vocabulary is a convenience; the list still loads */ },
    });
    this.load();
  }

  get visibleFacts(): BusinessFact[] {
    return this.showInactive ? this.facts : this.facts.filter(f => f.active);
  }

  load() {
    this.loading = true;
    this.errorMessage = '';
    this.gov.listFacts(this.filterDomain || undefined).subscribe({
      next: data => { this.facts = data; this.loading = false; this.cdr.detectChanges(); },
      error: err => {
        this.loading = false;
        this.errorMessage = err?.error?.message || 'Impossible de charger les faits gouvernés.';
        this.cdr.detectChanges();
      },
    });
  }

  openCreate() {
    this.editing = null;
    this.form = this.emptyForm();
    this.formError = '';
    this.showForm = true;
  }

  openEdit(f: BusinessFact) {
    this.editing = f;
    this.form = {
      domain: f.domain, key: f.key, value: f.value, factType: f.factType,
      topic: f.topic, language: f.language, source: f.source, validFrom: f.validFrom, reason: '',
    };
    this.formError = '';
    this.showForm = true;
  }

  save() {
    this.formError = '';
    if (this.editing && !this.form.reason?.trim()) {
      this.formError = 'Un motif est obligatoire pour modifier un fait.';
      return;
    }
    this.saving = true;
    const call = this.editing
      ? this.gov.updateFact(this.editing.id, this.form)
      : this.gov.createFact(this.form);
    call.subscribe({
      next: () => { this.saving = false; this.showForm = false; this.load(); },
      error: err => {
        this.saving = false;
        this.formError = err?.error?.message
          || (err?.error?.fields ? Object.values(err.error.fields).join(' · ') : 'Enregistrement impossible.');
        this.cdr.detectChanges();
      },
    });
  }

  toggleActive(f: BusinessFact) {
    const verb = f.active ? 'désactiver' : 'réactiver';
    const reason = window.prompt(`Motif pour ${verb} « ${f.key} » (obligatoire, tracé dans l'audit) :`);
    if (!reason || !reason.trim()) return;
    const call = f.active ? this.gov.deactivateFact(f.id, reason) : this.gov.reactivateFact(f.id, reason);
    call.subscribe({
      next: () => this.load(),
      error: err => { this.errorMessage = err?.error?.message || 'Opération impossible.'; this.cdr.detectChanges(); },
    });
  }

  openHistory(f: BusinessFact) {
    this.historyFor = f;
    this.history = [];
    this.gov.factHistory(f.id).subscribe({
      next: h => { this.history = h; this.cdr.detectChanges(); },
      error: () => { this.history = []; this.cdr.detectChanges(); },
    });
  }

  private emptyForm(): BusinessFactRequest {
    return { domain: 'financement', key: '', value: '', factType: 'rate', topic: '', language: 'fr', source: '', validFrom: null, reason: '' };
  }
}
