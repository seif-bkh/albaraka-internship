import { Component, OnInit, ChangeDetectorRef, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-admin-stats',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-stats.html',
  styleUrl: './admin-stats.css',
})
export class AdminStats implements OnInit {
  loading = true;
  errorMsg = '';

  // Données globales
  totalUsers = 0;
  activeUsers = 0;
  totalDocuments = 0;
  indexedDocuments = 0;
  totalConversations = 0;
  totalMessages = 0;
  totalClients = 0;

  // Répartitions
  parLangue: { langue: string; count: number; pct: number }[] = [];
  parIntention: { intention: string; count: number; pct: number }[] = [];
  parJour: { jour: string; count: number }[] = [];

  langueColors: Record<string, string> = {
    fr: '#1f4e8c', ar: '#d0021b', en: '#1a7f4b', default: '#5b6472'
  };

  intentionIcons: Record<string, string> = {
    theologique: '🕌', commercial: '🏦', reglementaire: '⚖️',
    salutation: '👋', hors_perimetre: '❌', cache_hit: '⚡', 'N/A': '❓'
  };

  private apiUrl = environment.apiUrl + '/stats';

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.loadStats();
  }

  loadStats() {
    this.loading = true;
    this.errorMsg = '';
    this.cdr.detectChanges();
    this.http.get<any>(this.apiUrl).subscribe({
      next: (data) => {
        this.totalUsers = data.totalUsers ?? 0;
        this.activeUsers = data.activeUsers ?? 0;
        this.totalDocuments = data.totalDocuments ?? 0;
        this.indexedDocuments = data.indexedDocuments ?? 0;
        this.totalConversations = data.totalConversations ?? 0;
        this.totalMessages = data.totalMessages ?? 0;
        this.totalClients = data.totalClients ?? 0;

        const totalL = (data.parLangue || []).reduce((s: number, x: any) => s + Number(x.count), 0) || 1;
        this.parLangue = (data.parLangue || []).map((x: any) => ({
          langue: x.langue || 'N/A',
          count: Number(x.count),
          pct: Math.round(Number(x.count) / totalL * 100)
        }));

        const totalI = (data.parIntention || []).reduce((s: number, x: any) => s + Number(x.count), 0) || 1;
        this.parIntention = (data.parIntention || []).map((x: any) => ({
          intention: x.intention || 'N/A',
          count: Number(x.count),
          pct: Math.round(Number(x.count) / totalI * 100)
        }));

        // Tri chronologique pour le graphe temporel
        this.parJour = (data.parJour || [])
          .map((x: any) => ({ jour: String(x.jour), count: Number(x.count) }))
          .reverse();

        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = 'Impossible de charger les statistiques.';
        console.error(err);
        this.cdr.detectChanges();
      }
    });
  }

  getLangueLabel(code: string): string {
    const map: Record<string, string> = { fr: 'Français', ar: 'Arabe', en: 'Anglais' };
    return map[code] || code?.toUpperCase() || '?';
  }

  getLangueColor(code: string): string {
    return this.langueColors[code] || this.langueColors['default'];
  }

  getIntentionLabel(key: string): string {
    const map: Record<string, string> = {
      theologique: 'Théologique', commercial: 'Commercial',
      reglementaire: 'Réglementaire', salutation: 'Salutation',
      hors_perimetre: 'Hors périmètre', cache_hit: 'Cache',
    };
    return map[key] || key || 'N/A';
  }

  getIntentionIcon(key: string): string {
    return this.intentionIcons[key] || '❓';
  }

  getBarColors(): string[] {
    return ['#1f4e8c', '#d0021b', '#1a7f4b', '#b3600a', '#5b6472', '#0f766e', '#7c3aed'];
  }

  maxJourCount(): number {
    return Math.max(...this.parJour.map(j => j.count), 1);
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });
  }
}
