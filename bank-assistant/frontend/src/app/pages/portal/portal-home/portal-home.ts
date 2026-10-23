import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslationService } from '../../../services/translation.service';
import { ClientService, ClientProfile, Compte, Carte, Transaction } from '../../../services/client.service';
import { SimulatorDialogComponent } from '../../../components/simulator-dialog/simulator-dialog';
import { AppointmentDialogComponent } from '../../../components/appointment-dialog/appointment-dialog';
import { AdvisorDialogComponent } from '../../../components/advisor-dialog/advisor-dialog';
import { NewsSectionComponent } from '../../../components/news-section/news-section';
import { PrayerTimesComponent } from '../../../components/prayer-times/prayer-times';

/**
 * Client portal home: accounts, cards, latest transactions and quick actions.
 * All data comes from /api/client/me/** (server-scoped to the logged-in client).
 *
 * Quick actions (P0): simulator, appointment and advisor dialogs.
 * P2: news feed and prayer times.
 */
@Component({
  selector: 'app-portal-home',
  standalone: true,
  imports: [
    CommonModule, RouterLink, FormsModule, DecimalPipe, DatePipe, TranslatePipe,
    SimulatorDialogComponent, AppointmentDialogComponent, AdvisorDialogComponent,
    NewsSectionComponent, PrayerTimesComponent
  ],
  templateUrl: './portal-home.html',
  styleUrl: './portal-home.css'
})
export class PortalHomeComponent implements OnInit {
  private readonly api = inject(ClientService);
  private readonly cdr = inject(ChangeDetectorRef);
  readonly i18n = inject(TranslationService);

  profile: ClientProfile | null = null;
  comptes: Compte[] = [];
  cartes: Carte[] = [];
  transactions: Transaction[] = [];
  compteFilter: number | null = null;

  loading = true;
  error = '';
  copiedIban: string | null = null;

  // Quick-action dialogs (P0). Tiles stay visible; the dialogs carry the state.
  simOpen = false;
  apptOpen = false;
  advOpen = false;

  openSimulator(): void { this.simOpen = true; }
  openAppointment(): void { this.apptOpen = true; }
  openAdvisor(): void { this.advOpen = true; }

  ngOnInit() {
    this.i18n.setLang(localStorage.getItem('ab_portal_lang') || 'fr');
    this.load();
  }

  load() {
    this.loading = true;
    this.error = '';
    this.api.me().subscribe({
      next: p => { this.profile = p; this.applyLang(p); this.cdr.detectChanges(); },
      error: () => { /* profile is not blocking: the rest can still load */ }
    });
    this.api.comptes().subscribe({
      next: c => { this.comptes = c; this.loading = false; this.cdr.detectChanges(); },
      error: () => { this.error = 'error'; this.loading = false; this.cdr.detectChanges(); }
    });
    this.api.cartes().subscribe({
      next: c => { this.cartes = c; this.cdr.detectChanges(); },
      error: () => { this.cartes = []; this.cdr.detectChanges(); }
    });
    this.loadTransactions();
  }

  loadTransactions() {
    this.api.transactions(this.compteFilter ?? undefined, 10).subscribe({
      next: t => { this.transactions = t; this.cdr.detectChanges(); },
      error: () => { this.transactions = []; this.cdr.detectChanges(); }
    });
  }

  setFilter(id: number | null) {
    this.compteFilter = id;
    this.loadTransactions();
  }

  totalBalance(): number {
    return this.comptes.reduce((sum, c) => sum + (c.solde ?? 0), 0);
  }

  compteTypeLabel(type: string): string {
    if (type === 'EPARGNE') return 'portal.account.savings';
    if (type === 'DEPOT') return 'portal.account.deposit';
    return 'portal.account.current';
  }

  maskedIban(iban: string): string {
    if (!iban || iban.length < 8) return iban;
    return `**** **** ${iban.slice(-4)}`;
  }

  async copyIban(iban: string) {
    try {
      await navigator.clipboard.writeText(iban);
      this.copiedIban = iban;
      this.cdr.detectChanges();
      setTimeout(() => { this.copiedIban = null; this.cdr.detectChanges(); }, 1800);
    } catch { /* clipboard unavailable */ }
  }

  cardLabel(type: string): string {
    return type.replaceAll('_', ' ').toLowerCase()
      .replace(/\b\w/g, ch => ch.toUpperCase());
  }

  private applyLang(p: ClientProfile) {
    const pref = (p.languePreferee || '').toLowerCase();
    if (pref === 'ar' || pref === 'fr' || pref === 'en') {
      localStorage.setItem('ab_portal_lang', pref);
      this.i18n.setLang(pref);
    }
  }

  get dir(): 'rtl' | 'ltr' {
    return this.i18n.lang() === 'ar' ? 'rtl' : 'ltr';
  }
}
