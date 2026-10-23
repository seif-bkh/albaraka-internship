import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => { http.verify(); localStorage.clear(); });

  it('stores tokens and user on login and answers role checks with the ROLE_ prefix', () => {
    let seen: any;
    service.login('a@b.tn', 'secret').subscribe(r => (seen = r));
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ email: 'a@b.tn', motDePasse: 'secret' });
    req.flush({ token: 't1', refreshToken: 'r1', id: 7, email: 'a@b.tn', roles: ['ROLE_ADMIN_DOCUMENTS'] });

    expect(seen.token).toBe('t1');
    expect(service.getAccessToken()).toBe('t1');
    expect(service.currentUserValue?.email).toBe('a@b.tn');
    expect(service.hasRole('ADMIN_DOCUMENTS')).toBe(true);
    expect(service.hasRole('SUPER_ADMIN')).toBe(false);
  });

  it('propagates login failures and leaves no session behind', () => {
    let failed = false;
    service.login('a@b.tn', 'bad').subscribe({ error: () => (failed = true) });
    http.expectOne('/api/auth/login').flush({ message: 'nope' }, { status: 401, statusText: 'Unauthorized' });
    expect(failed).toBe(true);
    expect(service.currentUserValue).toBeNull();
    expect(service.hasRole('VIEWER')).toBe(false);
  });

  it('refreshes the access token and logout clears everything', () => {
    localStorage.setItem('refreshToken', 'r1');
    service.refreshToken().subscribe();
    http.expectOne('/api/auth/refresh').flush({ token: 't2', refreshToken: 'r2' });
    expect(service.getAccessToken()).toBe('t2');
    expect(localStorage.getItem('refreshToken')).toBe('r2');

    service.logout();
    expect(http.expectOne('/api/auth/logout').request.body).toEqual({ refreshToken: 'r2' });
    expect(service.getAccessToken()).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(service.currentUserValue).toBeNull();
    expect(() => service.refreshToken()).toThrowError('No refresh token available');
  });

  it('restores a persisted user and drops a corrupt one', () => {
    localStorage.setItem('user', JSON.stringify({ id: 1, email: 'x@y.tn', roles: ['ROLE_VIEWER'] }));
    const restored = TestBed.runInInjectionContext(() => new AuthService(TestBed.inject(AuthService)['http']));
    expect(restored.hasRole('VIEWER')).toBe(true);

    localStorage.setItem('user', '{not json');
    const broken = new AuthService(TestBed.inject(AuthService)['http']);
    expect(broken.currentUserValue).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });

  it('recognizes dynamic staff permissions and selects a permitted landing page', () => {
    service.login('operator@example.test', 'secret').subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'staff-token', refreshToken: 'staff-refresh', id: 9, email: 'operator@example.test',
      roles: ['ROLE_STAFF', 'PERM_DOCUMENTS_INGESTION_READ', 'PERM_RAGLAB_CONSOLE_VIEW']
    });

    expect(service.isStaff()).toBe(true);
    expect(service.hasPermission('DOCUMENTS_INGESTION_READ')).toBe(true);
    expect(service.hasPermission('PERM_RAGLAB_CONSOLE_VIEW')).toBe(true);
    expect(service.hasPermission('DOCUMENTS_DELETE')).toBe(false);
    expect(service.homeForCurrentUser()).toBe('/admin/raglab/documents');
  });

  it('supports devQuickLogin for one-click development authentication', () => {
    service.devQuickLogin('admin').subscribe();
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ email: 'admin@albaraka.tn', motDePasse: 'postgres' });
    req.flush({ token: 'dev-token', refreshToken: 'dev-refresh', id: 1, email: 'admin@albaraka.tn', roles: ['ROLE_SUPER_ADMIN'] });

    expect(service.getAccessToken()).toBe('dev-token');
    expect(service.hasRole('SUPER_ADMIN')).toBe(true);
  });
});
