import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AgentAdminService } from './agent-admin.service';

describe('AgentAdminService', () => {
  let service: AgentAdminService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AgentAdminService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the effective endpoint under the relative /api base', () => {
    service.endpoint().subscribe();
    const req = http.expectOne('/api/admin/agent/endpoint');
    expect(req.request.method).toBe('GET');
    req.flush({ url: 'http://10.20.30.40:8000', source: 'DATABASE' });
  });

  it('sends the new address and its reason as JSON', () => {
    service.setEndpoint('http://10.20.30.40:8000', 'bascule RAGLab').subscribe();
    const req = http.expectOne('/api/admin/agent/endpoint');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ url: 'http://10.20.30.40:8000', reason: 'bascule RAGLab' });
    req.flush({ url: 'http://10.20.30.40:8000', source: 'DATABASE' });
  });

  it('carries the reason in the DELETE body when freeing the address', () => {
    service.clearEndpoint('retour au défaut déployé').subscribe();
    const req = http.expectOne('/api/admin/agent/endpoint');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ reason: 'retour au défaut déployé' });
    req.flush({ url: 'http://raglab:8000', source: 'ENV' });
  });

  it('asks for diagnostics even when the service is down (failure is data)', () => {
    service.diagnostics().subscribe(res => {
      expect(res.reachable).toBe(false);
      expect(res.errorReason).toBe('raglab_unreachable');
    });
    const req = http.expectOne('/api/admin/agent/diagnostics');
    expect(req.request.method).toBe('GET');
    req.flush({
      endpoint: { url: 'http://10.20.30.40:8000', source: 'DATABASE', configured: true, editable: true },
      reachable: false, httpStatus: null, latencyMs: 3001,
      error: 'Connexion à http://10.20.30.40:8000 impossible : Connection refused',
      errorReason: 'raglab_unreachable', health: null,
    });
  });

  it('reads the journal with a server-side action filter', () => {
    service.audit(50, 'ENGINE_SET').subscribe();
    const req = http.expectOne(r => r.url === '/api/admin/agent/audit');
    expect(req.request.params.get('limit')).toBe('50');
    expect(req.request.params.get('action')).toBe('ENGINE_SET');
    req.flush([]);
  });
});
