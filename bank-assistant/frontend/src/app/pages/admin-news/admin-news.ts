import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { NewsService, NewsItem, NewsItemPayload } from '../../services/news.service';

/**
 * Actualités (P2) : gestion des items affichés sur la page d'accueil publique
 * et l'accueil du portail. Un item n'est visible des clients que s'il est
 * actif ET dont la date de publication est passée.
 */
@Component({
  selector: 'app-admin-news',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-news.html',
  styleUrl: './admin-news.css',
})
export class AdminNews implements OnInit {
  items: NewsItem[] = [];
  loading = false;
  errorMessage = '';
  canWrite = false;

  showForm = false;
  editing: NewsItem | null = null;
  form: NewsItemPayload = this.emptyForm();
  saving = false;
  formError = '';

  constructor(
    private news: NewsService,
    private auth: AuthService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.canWrite = this.auth.hasRole('SUPER_ADMIN') || this.auth.hasRole('ADMIN_DOCUMENTS');
    this.load();
  }

  load() {
    this.loading = true;
    this.errorMessage = '';
    this.news.list().subscribe({
      next: data => { this.items = data; this.loading = false; this.cdr.detectChanges(); },
      error: err => {
        this.loading = false;
        this.errorMessage = err?.error?.message || 'Impossible de charger les actualités.';
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

  openEdit(item: NewsItem) {
    this.editing = item;
    this.form = {
      title: item.title,
      body: item.body,
      externalLink: item.externalLink ?? '',
      publishedAt: item.publishedAt,
      active: item.active,
    };
    this.formError = '';
    this.showForm = true;
  }

  save() {
    if (!this.form.title || !this.form.body || !this.form.publishedAt) {
      this.formError = 'Le titre, le corps et la date de publication sont requis.';
      return;
    }
    this.saving = true;
    this.formError = '';
    const payload: NewsItemPayload = {
      title: this.form.title,
      body: this.form.body,
      externalLink: this.form.externalLink || undefined,
      publishedAt: this.form.publishedAt,
      active: this.form.active,
    };
    const op = this.editing
      ? this.news.update(this.editing.id, payload)
      : this.news.create(payload);
    op.subscribe({
      next: () => {
        this.saving = false;
        this.showForm = false;
        this.load();
      },
      error: err => {
        this.saving = false;
        this.formError = err?.error?.message || 'Enregistrement impossible.';
        this.cdr.detectChanges();
      },
    });
  }

  toggleActive(item: NewsItem) {
    this.news.toggleActive(item.id).subscribe({
      next: () => this.load(),
      error: err => {
        this.errorMessage = err?.error?.message || 'Action impossible.';
        this.cdr.detectChanges();
      },
    });
  }

  remove(item: NewsItem) {
    if (!confirm(`Supprimer « ${item.title} » ? Cette action est définitive.`)) {
      return;
    }
    this.news.remove(item.id).subscribe({
      next: () => this.load(),
      error: err => {
        this.errorMessage = err?.error?.message || 'Suppression impossible.';
        this.cdr.detectChanges();
      },
    });
  }

  private emptyForm(): NewsItemPayload {
    const today = new Date();
    const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    return { title: '', body: '', externalLink: '', publishedAt: iso, active: true };
  }
}
