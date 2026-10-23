import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface DashboardStats {
  totalUsers: number;
  activeUsers: number;
  totalDocuments: number;
  indexedDocuments: number;
  totalConversations: number;
  totalMessages: number;
  parLangue?: { langue: string; count: number }[];
  parIntention?: { intention: string; count: number }[];
  parJour?: { jour: string; count: number }[];
}

@Injectable({
  providedIn: 'root'
})
export class StatService {
  private apiUrl = environment.apiUrl + '/stats';

  constructor(private http: HttpClient) {}

  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(this.apiUrl);
  }
}
