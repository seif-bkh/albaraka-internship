import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { RaglabService } from '../../../services/raglab.service';

/**
 * The instrument strip: six readings (service, liaison, modèles, corpus, clés, ingestion)
 * that together answer "can it answer, and is what it answers current?". Each reading is a label, a value and — when it matters — a lamp.
 *
 * <p>It is `OnPush` and driven only by signals, so the 20-second poll repaints these
 * nodes and nothing else in the application.
 */
@Component({
  selector: 'app-raglab-status-bar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './raglab-status-bar.component.html',
  styleUrl: './raglab-status-bar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RaglabStatusBarComponent {
  public readonly raglab = inject(RaglabService);

  readonly health = this.raglab.health;
  readonly isSealed = this.raglab.isSealed;
  readonly isStale = this.raglab.isStale;
  readonly isEmptyIndex = this.raglab.isEmptyIndex;
  readonly missingKeys = this.raglab.missingKeys;
  readonly isPinned = this.raglab.isPinnedPipeline;
  readonly isLoading = this.raglab.isLoadingHealth;
  readonly lastCheckedAt = this.raglab.lastCheckedAt;

  readonly isDown = computed(() => this.health()?.status === 'down');
  /** A service nobody configured is not "down": it has never been wired. */
  readonly isConfigured = computed(() => this.health() !== null && this.health()?.error_reason !== 'no_endpoint');
  readonly connectionLabel = computed(() => {
    if (this.health() === null) return 'Connexion…';
    return this.isDown() ? 'Injoignable' : 'Connecté';
  });

  readonly profileSummary = computed(() => {
    const p = this.health()?.profile;
    if (!p) return 'Aucun profil';
    const emb = p.embedding?.model || 'sans embedding';
    const ans = p.answer?.model || 'sans modèle';
    return `${emb} · ${ans}`;
  });

  readonly keysSummary = computed(() => {
    const keysObj = this.health()?.keys;
    if (!keysObj) return { total: 0, set: 0, ok: true };
    const entries = Object.entries(keysObj);
    const total = entries.length;
    const set = entries.filter(([_, v]) => v === 'set').length;
    return { total, set, ok: total > 0 && set === total };
  });

  readonly indexLabel = computed(() => {
    if (this.isEmptyIndex()) return 'vide';
    if (this.isStale()) return 'obsolète';
    return 'à jour';
  });

  readonly indexTone = computed<'ok' | 'warn' | 'err'>(() => {
    if (this.isEmptyIndex()) return 'err';
    if (this.isStale()) return 'warn';
    return 'ok';
  });

  readonly ingestLabel = computed(() => {
    const state = this.health()?.ingest?.state;
    if (state === 'running') return 'en cours';
    if (state === 'done') return 'prêt';
    if (state === 'error') return 'erreur';
    return 'inactif';
  });

  onRefresh(): void {
    this.raglab.refreshHealth();
  }
}
