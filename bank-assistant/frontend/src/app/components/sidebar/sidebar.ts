import { Component, OnInit, ChangeDetectorRef, Output, EventEmitter } from '@angular/core';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService, User } from '../../services/auth.service';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface ChatSession {
  sessionId: string;
  dateCreation: string;
  titre: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './sidebar.html',
  styleUrls: ['./sidebar.css']
})
export class SidebarComponent implements OnInit {
  user: User | null = null;
  isAdmin = false;
  isLegacyStaff = false;
  isCustomer = false;
  sessions: ChatSession[] = [];
  isHistoryExpanded = false;

  @Output() sessionSelected = new EventEmitter<string>();

  constructor(
    public authService: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private http: HttpClient
  ) {}

  get canViewRaglab(): boolean { return this.authService.hasPermission('RAGLAB_CONSOLE_VIEW'); }
  get canRunRaglab(): boolean { return this.authService.hasPermission('RAGLAB_CONSOLE_RUN'); }
  get userRoleLabel(): string {
    if (this.isAdmin) return 'Super administrateur';
    return this.authService.isStaff() ? 'Personnel' : 'Client';
  }

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.user = user;
      this.isAdmin = this.authService.hasRole('SUPER_ADMIN');
      this.isLegacyStaff = this.authService.isLegacyStaff();
      this.isCustomer = this.authService.hasRole('UTILISATEUR');
      if (this.user && this.isCustomer) this.loadSessions();
      else this.sessions = [];
      this.cdr.detectChanges();
    });
  }

  loadSessions(): void {
    const token = this.authService.getAccessToken();
    if (!token) return;
    const headers = new HttpHeaders().set('Authorization', `Bearer ${token}`);
    this.http.get<ChatSession[]>(`${environment.apiUrl}/chat/sessions`, { headers }).subscribe({
      next: data => { this.sessions = data; this.cdr.detectChanges(); },
      error: () => { this.sessions = []; this.cdr.detectChanges(); }
    });
  }

  onSessionClick(sessionId: string): void { this.sessionSelected.emit(sessionId); }
  onNewSessionClick(): void { this.sessionSelected.emit(''); }
  toggleHistory(): void { this.isHistoryExpanded = !this.isHistoryExpanded; }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
