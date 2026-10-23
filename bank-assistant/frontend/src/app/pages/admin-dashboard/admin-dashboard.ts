import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { StatService, DashboardStats } from '../../services/stat.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, DatePipe, RouterLink],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.css',
})
export class AdminDashboard implements OnInit {
  stats: DashboardStats | null = null;
  loading = true;

  constructor(
    private statService: StatService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.statService.getStats().subscribe({
      next: (data) => {
        this.stats = data;
        
        // Remplir les 30 derniers jours pour le graphique
        const last30Days: {jour: string, count: number}[] = [];
        for (let i = 29; i >= 0; i--) {
          const d = new Date();
          d.setDate(d.getDate() - i);
          const dateStr = d.toISOString().split('T')[0];
          const existing = this.stats.parJour?.find(j => j.jour === dateStr);
          last30Days.push({
            jour: dateStr,
            count: existing ? existing.count : 0
          });
        }
        this.stats.parJour = last30Days;
        
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Erreur stats:', err);
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  getBarHeight(count: number): string {
    if (!this.stats?.parJour || this.stats.parJour.length === 0) return '0%';
    const max = Math.max(...this.stats.parJour.map(j => j.count), 1);
    const pct = Math.round((count / max) * 100);
    return `${pct}%`;
  }
}
