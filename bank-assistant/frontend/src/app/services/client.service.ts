import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Profile of the logged-in bank client (GET/PUT /api/client/me). */
export interface ClientProfile {
  id: number;
  email: string;
  nom: string;
  prenom: string;
  telephone?: string;
  adresse?: string;
  ville?: string;
  dateNaissance?: string;
  languePreferee?: string;
  segment?: 'PARTICULIER' | 'PROFESSIONNEL' | 'ENTREPRISE';
  referenceClient?: string;
  dateCreation?: string;
}

export interface ClientProfilePatch {
  telephone?: string;
  adresse?: string;
  ville?: string;
  dateNaissance?: string;
  languePreferee?: string;
}

export interface Compte {
  id: number;
  libelle: string;
  type: 'COMPTE_COURANT' | 'EPARGNE' | 'DEPOT';
  iban: string;
  devise: string;
  solde: number;
  dateOuverture: string;
}

export interface Carte {
  id: number;
  type: string;
  reseau: string;
  porteur: string;
  expiration: string;
  plafondJournalier: number;
  bloque: boolean;
  compteId?: number;
}

export interface Transaction {
  id: number;
  compteId: number;
  dateOperation: string;
  libelle: string;
  categorie?: string;
  montant: number;
  devise: string;
}

export interface ClientConversation {
  id: number;
  sessionId: string;
  nombreMessages: number;
  dateDebut: string;
  titre?: string;
}

export interface ClientConversationMessage {
  id: number;
  question: string;
  reponse: string;
  dateCreation: string;
}

/** Financing-simulator input (portal). Product comes from simulationProducts(). */
export interface FinancingSimulationRequest {
  product: string;
  amount: number;
  months: number;
}

/**
 * Server-computed simulation. When `rateAvailable` is false, no governed rate
 * exists for the product: the monetary fields are null and the UI must show
 * an honest "not available" state (never guess a number).
 */
export interface FinancingSimulationResult {
  product: string;
  rateAvailable: boolean;
  ratePercent: number | null;
  amount: number;
  months: number;
  monthlyPayment: number | null;
  totalProfit: number | null;
  totalRepayment: number | null;
  currency: string;
}

/** Portal advisor flows: APPOINTMENT (rendez-vous) or CALLBACK (rappel). */
export interface AdvisorRequestPayload {
  kind: 'APPOINTMENT' | 'CALLBACK';
  motif?: string;
  meetingMethod?: 'AGENCE' | 'TELEPHONE';
  preferredBranch?: string;
  preferredDate?: string; // ISO yyyy-MM-dd
  preferredTime?: string; // HH:mm
  telephone?: string;
  message?: string;
}

export interface AdvisorRequestConfirmation {
  id: number;
  kind: string;
  status: string;
  motif?: string;
  meetingMethod?: string;
  preferredBranch?: string;
  preferredDate?: string;
  preferredTime?: string;
  telephone?: string;
  message?: string;
  createdAt: string;
}

/**
 * Portal API of the logged-in bank client (`/api/client/me/**`, JWT required).
 * Everything is scoped server-side to the authenticated identity.
 */
@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly base = `${environment.apiUrl}/client/me`;

  constructor(private http: HttpClient) {}

  me(): Observable<ClientProfile> {
    return this.http.get<ClientProfile>(`${this.base}`);
  }

  updateMe(patch: ClientProfilePatch): Observable<ClientProfile> {
    return this.http.put<ClientProfile>(`${this.base}`, patch);
  }

  comptes(): Observable<Compte[]> {
    return this.http.get<Compte[]>(`${this.base}/comptes`);
  }

  cartes(): Observable<Carte[]> {
    return this.http.get<Carte[]>(`${this.base}/cartes`);
  }

  transactions(compteId?: number, limit = 12): Observable<Transaction[]> {
    const params: Record<string, string> = { limit: String(limit) };
    if (compteId) {
      params['compteId'] = String(compteId);
    }
    return this.http.get<Transaction[]>(`${this.base}/transactions`, { params });
  }

  conversations(): Observable<ClientConversation[]> {
    return this.http.get<ClientConversation[]>(`${this.base}/conversations`);
  }

  conversationMessages(id: number): Observable<ClientConversationMessage[]> {
    return this.http.get<ClientConversationMessage[]>(`${this.base}/conversations/${id}/messages`);
  }

  // ── Simulator & advisor (P0) ──────────────────────────────────────────

  /** Products that currently have a governed rate — the simulatable ones. */
  simulationProducts(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/simulation/products`);
  }

  simulateFinancement(req: FinancingSimulationRequest): Observable<FinancingSimulationResult> {
    return this.http.post<FinancingSimulationResult>(`${this.base}/simulation/financement`, req);
  }

  /** Active contact facts (phone/e-mail); empty until the bank configures them. */
  advisorContacts(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/advisor/contacts`);
  }

  createAdvisorRequest(req: AdvisorRequestPayload): Observable<AdvisorRequestConfirmation> {
    return this.http.post<AdvisorRequestConfirmation>(`${this.base}/advisor/requests`, req);
  }
}
