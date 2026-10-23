import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** News item as served to clients and managed in the back-office. */
export interface NewsItem {
  id: number;
  title: string;
  body: string;
  externalLink?: string;
  publishedAt: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface NewsItemPayload {
  title: string;
  body: string;
  externalLink?: string;
  publishedAt: string; // ISO yyyy-MM-dd
  active?: boolean;
}

/**
 * News (P2): public feed for the landing/portal (`/api/news/active`,
 * anonymous) and back-office CRUD (`/api/admin/news/**`, staff roles).
 */
@Injectable({ providedIn: 'root' })
export class NewsService {
  constructor(private http: HttpClient) {}

  /** Active, already-published items, newest first (capped server-side). */
  active(limit = 3): Observable<NewsItem[]> {
    return this.http.get<NewsItem[]>(`${environment.apiUrl}/news/active`, { params: { limit: String(limit) } });
  }

  // ── Back-office ───────────────────────────────────────────────────────

  list(): Observable<NewsItem[]> {
    return this.http.get<NewsItem[]>(`${environment.apiUrl}/admin/news`);
  }

  create(item: NewsItemPayload): Observable<NewsItem> {
    return this.http.post<NewsItem>(`${environment.apiUrl}/admin/news`, item);
  }

  update(id: number, item: NewsItemPayload): Observable<NewsItem> {
    return this.http.put<NewsItem>(`${environment.apiUrl}/admin/news/${id}`, item);
  }

  toggleActive(id: number): Observable<NewsItem> {
    return this.http.post<NewsItem>(`${environment.apiUrl}/admin/news/${id}/toggle-active`, null);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/admin/news/${id}`);
  }
}
