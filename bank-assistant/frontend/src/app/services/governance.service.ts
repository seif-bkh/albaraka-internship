import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Client for the Phase 3.7 admin governance APIs (/api/admin/**). */

export interface BusinessFact {
  id: number;
  domain: string;
  key: string;
  value: string;
  factType: string;
  topic: string | null;
  language: string;
  source: string | null;
  active: boolean;
  validFrom: string | null;
  updatedBy: string | null;
  updatedAt: string;
}

export interface BusinessFactRequest {
  domain: string;
  key: string;
  value: string;
  factType: string;
  topic?: string | null;
  language?: string;
  source?: string | null;
  validFrom?: string | null;
  reason?: string | null;
}

export interface BusinessFactAudit {
  id: number;
  action: string;
  oldValue: string | null;
  newValue: string | null;
  reason: string | null;
  changedBy: string | null;
  changedAt: string;
}

export interface CatalogExemplar {
  id: number;
  domain: string;
  intent: string;
  text: string;
  language: string;
  topic: string | null;
  slots: string | null;
  active: boolean;
  updatedBy: string | null;
  updatedAt: string;
}

export interface CatalogExemplarRequest {
  domain: string;
  intent: string;
  text: string;
  language?: string;
  topic?: string | null;
  slots?: string | null;
}

export interface CatalogRelease {
  id: number;
  version: string;
  sealedBy: string | null;
  sealedAt: string;
  exemplarCount: number;
  note: string | null;
  published: boolean;
}

@Injectable({ providedIn: 'root' })
export class GovernanceService {
  private readonly base = environment.apiUrl + '/admin';

  constructor(private http: HttpClient) {}

  // ---- governed facts -------------------------------------------------------

  factsVocabulary(): Observable<{ domains: string[]; factTypes: string[] }> {
    return this.http.get<{ domains: string[]; factTypes: string[] }>(`${this.base}/facts/vocabulary`);
  }

  listFacts(domain?: string): Observable<BusinessFact[]> {
    const params = domain ? new HttpParams().set('domain', domain) : undefined;
    return this.http.get<BusinessFact[]>(`${this.base}/facts`, { params });
  }

  factHistory(id: number): Observable<BusinessFactAudit[]> {
    return this.http.get<BusinessFactAudit[]>(`${this.base}/facts/${id}/history`);
  }

  createFact(req: BusinessFactRequest): Observable<BusinessFact> {
    return this.http.post<BusinessFact>(`${this.base}/facts`, req);
  }

  updateFact(id: number, req: BusinessFactRequest): Observable<BusinessFact> {
    return this.http.put<BusinessFact>(`${this.base}/facts/${id}`, req);
  }

  deactivateFact(id: number, reason: string): Observable<BusinessFact> {
    return this.http.delete<BusinessFact>(`${this.base}/facts/${id}`, { body: { reason } });
  }

  reactivateFact(id: number, reason: string): Observable<BusinessFact> {
    return this.http.post<BusinessFact>(`${this.base}/facts/${id}/reactivate`, { reason });
  }

  // ---- taxonomy -------------------------------------------------------------

  taxonomyVocabulary(): Observable<{ domains: string[]; intents: string[] }> {
    return this.http.get<{ domains: string[]; intents: string[] }>(`${this.base}/taxonomy/vocabulary`);
  }

  listExemplars(domain?: string): Observable<CatalogExemplar[]> {
    const params = domain ? new HttpParams().set('domain', domain) : undefined;
    return this.http.get<CatalogExemplar[]>(`${this.base}/taxonomy/exemplars`, { params });
  }

  createExemplar(req: CatalogExemplarRequest): Observable<CatalogExemplar> {
    return this.http.post<CatalogExemplar>(`${this.base}/taxonomy/exemplars`, req);
  }

  updateExemplar(id: number, req: CatalogExemplarRequest): Observable<CatalogExemplar> {
    return this.http.put<CatalogExemplar>(`${this.base}/taxonomy/exemplars/${id}`, req);
  }

  deactivateExemplar(id: number): Observable<CatalogExemplar> {
    return this.http.delete<CatalogExemplar>(`${this.base}/taxonomy/exemplars/${id}`);
  }

  reactivateExemplar(id: number): Observable<CatalogExemplar> {
    return this.http.post<CatalogExemplar>(`${this.base}/taxonomy/exemplars/${id}/reactivate`, {});
  }

  listReleases(): Observable<CatalogRelease[]> {
    return this.http.get<CatalogRelease[]>(`${this.base}/taxonomy/releases`);
  }

  sealRelease(note: string): Observable<CatalogRelease> {
    return this.http.post<CatalogRelease>(`${this.base}/taxonomy/releases`, { note });
  }
}
