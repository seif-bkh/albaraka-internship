import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, map, tap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';

export interface User {
  id: number;
  email: string;
  /** Spring authorities: ROLE_* and PERM_*; refreshed from the backend. */
  roles: string[];
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  id: number;
  email: string;
  roles: string[];
}

interface CurrentUserResponse {
  id: number;
  email: string;
  roles: string[];
}

/** Original system staff roles; custom roles carry ROLE_STAFF instead. */
export const STAFF_ROLES = ['SUPER_ADMIN', 'ADMIN_DOCUMENTS', 'ADMIN_SUPERVISION', 'VIEWER'];

@Injectable({ providedIn: 'root' })
export class AuthService {
  // apiUrl is relative; the dev server/nginx proxies it to the backend.
  private readonly baseUrl = environment.apiUrl;
  private readonly authUrl = `${this.baseUrl}/auth`;

  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {
    const userJson = localStorage.getItem('user');
    if (userJson) {
      try {
        this.currentUserSubject.next(JSON.parse(userJson));
      } catch {
        localStorage.removeItem('user');
      }
    }
  }

  public get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  login(email: string, motDePasse: string): Observable<LoginResponse> {
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    return this.http.post<LoginResponse>(`${this.authUrl}/login`, { email, motDePasse }, { headers }).pipe(
      tap(response => {
        localStorage.setItem('accessToken', response.token);
        localStorage.setItem('refreshToken', response.refreshToken);
        this.storeUser(response);
      })
    );
  }

  /** Re-read effective authorities after a staff role or permission change. */
  refreshProfile(): Observable<User> {
    return this.http.get<CurrentUserResponse>(`${this.authUrl}/me`).pipe(
      tap(response => this.storeUser(response)),
      map(response => ({ id: response.id, email: response.email, roles: response.roles || [] }))
    );
  }

  registerClient(nom: string, prenom: string, email: string, motDePasse: string, telephone?: string): Observable<any> {
    return this.http.post<any>(`${this.authUrl}/register-client`, { nom, prenom, email, motDePasse, telephone });
  }

  logout(): void {
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
      this.http.post(`${this.authUrl}/logout`, { refreshToken }).subscribe({ error: () => {} });
    }
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    this.currentUserSubject.next(null);
  }

  refreshToken(): Observable<LoginResponse> {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) throw new Error('No refresh token available');
    return this.http.post<LoginResponse>(`${this.authUrl}/refresh`, { refreshToken }).pipe(tap(response => {
      localStorage.setItem('accessToken', response.token);
      if (response.refreshToken) localStorage.setItem('refreshToken', response.refreshToken);
      this.storeUser(response);
    }));
  }

  getAccessToken(): string | null {
    return localStorage.getItem('accessToken');
  }

  hasRole(role: string): boolean {
    return this.currentUserValue?.roles?.includes(`ROLE_${role}`) ?? false;
  }

  hasAnyRole(roles: string[]): boolean {
    return roles.some(role => this.hasRole(role));
  }

  hasPermission(permission: string): boolean {
    if (this.hasRole('SUPER_ADMIN')) return true;
    const authority = permission.startsWith('PERM_') ? permission : `PERM_${permission}`;
    return this.currentUserValue?.roles?.includes(authority) ?? false;
  }

  hasAnyPermission(permissions: string[]): boolean {
    return permissions.some(permission => this.hasPermission(permission));
  }

  hasAllPermissions(permissions: string[]): boolean {
    return permissions.every(permission => this.hasPermission(permission));
  }

  /** True for staff identities, including dynamically-created database roles. */
  isStaff(): boolean {
    return this.hasRole('STAFF') || STAFF_ROLES.some(role => this.hasRole(role));
  }

  isLegacyStaff(): boolean {
    return STAFF_ROLES.some(role => this.hasRole(role));
  }

  homeForCurrentUser(): string {
    if (!this.isStaff()) return '/portal';
    if (this.hasRole('SUPER_ADMIN') || this.isLegacyStaff()) return '/admin/dashboard';
    if (this.hasPermission('RAGLAB_CONSOLE_VIEW')) return '/admin/raglab/documents';
    return '/login';
  }

  /**
   * One-click development login for local testing with the seeded fixture.
   *
   * <p>The credentials live in `environment.devAccounts`, which a production build
   * replaces with null — so this method cannot sign anyone in there, and the
   * strings never reach the bundle.
   */
  devQuickLogin(role: 'admin' | 'client' = 'admin'): Observable<LoginResponse> {
    const account = environment.devAccounts?.[role];
    if (!account) {
      return throwError(() => new Error('No development account in this build.'));
    }
    return this.login(account.email, account.password);
  }

  private storeUser(response: Pick<CurrentUserResponse, 'id' | 'email' | 'roles'>): void {
    const user: User = { id: response.id, email: response.email, roles: response.roles || [] };
    localStorage.setItem('user', JSON.stringify(user));
    this.currentUserSubject.next(user);
  }
}
