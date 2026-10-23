import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ConversationSummary {
  id: number;
  sessionIdTronque: string;
  langue: string;
  nombreMessages: number;
  dateDebut: string;
  dateFin: string;
  localisation: string;
}

export interface ConversationDetail {
  conversationId: number;
  sessionId: string;
  langue: string;
  adresseIp: string;
  clientId?: number | null;
  dateDebut: string;
  dateFin: string;
  messages: MessageDetail[];
}

/** One document the assistant consulted for an answer (never shown to clients). */
export interface VisitedDocument {
  document: string | null;
  title: string | null;
  heading: string | null;
  chunkId: string | null;
  similarity: number | null;
  kept: boolean | null;
  cited: boolean;
  engine: string | null;
  label: string;
}

/** The audit half of an exchange: cost, latency, real timestamps, who asked. */
export interface MessageObservability {
  askedAt: string | null;
  answeredAt: string | null;
  model: string | null;
  tokensIn: number | null;
  tokensOut: number | null;
  latencyMs: number | null;
  status: string | null;
  grounded: boolean | null;
  langue: string | null;
  domaine: string | null;
  userId: number | null;
  hasTrace: boolean | null;
}

export interface MessageDetail {
  id: number;
  question: string;
  questionReformulee: string;
  intention: string;
  reponse: string;
  sourcesUtilisees: string[];
  documents?: VisitedDocument[];
  ragUtilise: boolean;
  dateCreation: string;
  observability?: MessageObservability;
}

export interface ConversationResume {
  conversationId: number;
  langue: string;
  nombreMessages: number;
  dateDebut: string;
  dateFin: string;
  dureeTotaleMinutes: number;
  sourcesUniques: string[];
  adresseIpMasquee: string;
}

export interface DocumentUtilisation {
  messageId: number;
  conversationId: number;
  sessionIdTronque: string;
  question: string;
  intention: string;
  dateCreation: string;
}

@Injectable({ providedIn: 'root' })
export class ConversationService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getConversations(params: {
    langue?: string;
    debut?: string;
    fin?: string;
    page?: number;
    size?: number;
  }): Observable<any> {
    let httpParams = new HttpParams();
    if (params.langue) httpParams = httpParams.set('langue', params.langue);
    if (params.debut) httpParams = httpParams.set('debut', params.debut);
    if (params.fin) httpParams = httpParams.set('fin', params.fin);
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());
    return this.http.get<any>(`${this.apiUrl}/clients/conversations`, { params: httpParams });
  }

  getConversationMessages(id: number): Observable<ConversationDetail> {
    return this.http.get<ConversationDetail>(`${this.apiUrl}/clients/conversations/${id}/messages`);
  }

  getConversationResume(id: number): Observable<ConversationResume> {
    return this.http.get<ConversationResume>(`${this.apiUrl}/clients/conversations/${id}/resume`);
  }

  getDocumentUtilisations(documentId: number, page = 0, size = 20): Observable<any> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<any>(`${this.apiUrl}/documents/${documentId}/utilisations`, { params });
  }
}
