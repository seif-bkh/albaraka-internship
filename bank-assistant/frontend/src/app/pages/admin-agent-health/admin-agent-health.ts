import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../services/auth.service';
import { AgentAdminService, AgentAuditEntry, AgentDiagnostics, AgentEndpoint }
  from '../../services/agent-admin.service';

/**
 * Assistant IA › Santé & diagnostic (slice 0).
 *
 * Answers two questions and nothing else: where is the RAG service, and is it
 * answering? The address is shown with its provenance — set from here, taken
 * from the deployment, or missing entirely — because "why is it calling that?"
 * is the first thing an operator asks during an incident.
 */
@Component({
  selector: 'app-admin-agent-health',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-agent-health.html',
  styleUrl: './admin-agent-health.css',
})
export class AdminAgentHealth implements OnInit {

  endpoint: AgentEndpoint | null = null;
  diagnostics: AgentDiagnostics | null = null;
  audit: AgentAuditEntry[] = [];

  loading = false;
  probing = false;
  saving = false;
  errorMessage = '';
  formError = '';

  canEdit = false;
  editing = false;
  form = { url: '', reason: '' };
  showRaw = false;

  constructor(
    private agent: AgentAdminService,
    private auth: AuthService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.canEdit = this.auth.hasPermission('RAGLAB_CONSOLE_MANAGE');
    this.load();
  }

  /** Address + journal (cheap, always safe). */
  load() {
    this.loading = true;
    this.errorMessage = '';
    this.agent.endpoint().subscribe({
      next: (endpoint) => {
        this.endpoint = endpoint;
        this.canEdit = endpoint.editable;
        this.form.url = endpoint.url ?? endpoint.deployedUrl ?? '';
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.errorMessage = this.describeError(err);
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
    this.agent.audit().subscribe({
      next: (entries) => { this.audit = entries; this.cdr.detectChanges(); },
      error: () => { this.audit = []; this.cdr.detectChanges(); },
    });
  }

  /** Calls the RAG service and shows whatever comes back, including failures. */
  probe() {
    this.probing = true;
    this.errorMessage = '';
    this.agent.diagnostics().subscribe({
      next: (diagnostics) => {
        this.diagnostics = diagnostics;
        this.endpoint = diagnostics.endpoint;
        this.probing = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.errorMessage = this.describeError(err);
        this.probing = false;
        this.cdr.detectChanges();
      },
    });
  }

  startEdit() {
    this.editing = true;
    this.formError = '';
    this.form.url = this.endpoint?.url ?? this.endpoint?.deployedUrl ?? '';
    this.form.reason = '';
  }

  cancelEdit() {
    this.editing = false;
    this.formError = '';
  }

  save() {
    if (!this.canEdit) { return; }
    if (!this.form.url.trim()) {
      this.formError = 'L\'adresse est obligatoire (exemple : http://10.20.30.40:8000).';
      return;
    }
    if (!this.form.reason.trim()) {
      this.formError = 'Un motif est obligatoire : il est journalisé avec le changement.';
      return;
    }
    this.saving = true;
    this.formError = '';
    this.agent.setEndpoint(this.form.url.trim(), this.form.reason.trim()).subscribe({
      next: (endpoint) => {
        this.endpoint = endpoint;
        this.saving = false;
        this.editing = false;
        this.probe();
        this.load();
      },
      error: (err) => {
        this.formError = this.describeError(err);
        this.saving = false;
        this.cdr.detectChanges();
      },
    });
  }

  clear() {
    if (!this.canEdit) { return; }
    const reason = window.prompt('Motif du retour à l\'adresse déployée ?') ?? '';
    if (!reason.trim()) {
      return;
    }
    this.agent.clearEndpoint(reason.trim()).subscribe({
      next: () => { this.editing = false; this.probe(); this.load(); },
      error: (err) => { this.errorMessage = this.describeError(err); this.cdr.detectChanges(); },
    });
  }

  /** Provenance label, in the operator's words. */
  sourceLabel(source: string | null | undefined): string {
    switch (source) {
      case 'DATABASE': return 'définie depuis le back-office';
      case 'ENV': return 'valeur déployée (RAGLAB_URL)';
      case 'NONE': return 'non configurée';
      default: return 'inconnue';
    }
  }

  sourceBadge(source: string | null | undefined): string {
    switch (source) {
      case 'DATABASE': return 'info';
      case 'ENV': return 'neutral';
      default: return 'warning';
    }
  }

  /** One-line verdict: what the operator should do next. */
  verdict(): string {
    if (!this.endpoint?.configured) {
      return 'Aucune adresse : le service RAG n\'est pas câblé sur cet environnement.';
    }
    if (!this.diagnostics) {
      return 'Adresse connue — lancez le test de connexion pour vérifier que le service répond.';
    }
    if (this.diagnostics.reachable) {
      return 'Le service répond. La suite (profil, index, clés) arrive avec les prochains écrans.';
    }
    return this.diagnostics.errorReason === 'raglab_not_configured'
      ? 'Aucune adresse configurée.'
      : 'Le service est injoignable : vérifiez l\'adresse, le port et que le service tourne.';
  }

  statusBadge(): string {
    if (!this.endpoint?.configured) { return 'warning'; }
    if (!this.diagnostics) { return 'neutral'; }
    return this.diagnostics.reachable ? 'success' : 'error';
  }

  statusLabel(): string {
    if (!this.endpoint?.configured) { return 'non configurée'; }
    if (!this.diagnostics) { return 'non testée'; }
    return this.diagnostics.reachable ? 'joignable' : 'injoignable';
  }

  rawHealth(): string {
    return this.diagnostics?.health ? JSON.stringify(this.diagnostics.health, null, 2) : '';
  }

  describeError(err: unknown): string {
    const e = err as { error?: { reason?: string; message?: string; error?: string }; message?: string };
    const reason = e?.error?.reason ?? e?.error?.error;
    const message = e?.error?.message ?? e?.message ?? 'Erreur inconnue';
    return reason ? `[${reason}] ${message}` : message;
  }

  trackById(_index: number, entry: AgentAuditEntry): number {
    return entry.id;
  }
}
