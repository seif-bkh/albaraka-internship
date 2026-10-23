import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { GovernanceService } from './governance.service';

describe('GovernanceService', () => {
  let service: GovernanceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GovernanceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists facts filtered by domain under the relative /api base', () => {
    service.listFacts('financement').subscribe();
    const req = http.expectOne(r => r.url === '/api/admin/facts');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('domain')).toBe('financement');
    req.flush([]);
  });

  it('sends the audit reason in the DELETE body when deactivating a fact', () => {
    service.deactivateFact(7, 'grille tarifaire retirée').subscribe();
    const req = http.expectOne('/api/admin/facts/7');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ reason: 'grille tarifaire retirée' });
    req.flush({});
  });

  it('seals a catalog release with a note', () => {
    service.sealRelease('v2').subscribe();
    const req = http.expectOne('/api/admin/taxonomy/releases');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ note: 'v2' });
    req.flush({});
  });
});

describe('GovernanceService (remaining endpoints)', () => {
  let service: GovernanceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(GovernanceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('covers facts vocabulary, get, history, create, update and reactivate', () => {
    service.factsVocabulary().subscribe();
    http.expectOne('/api/admin/facts/vocabulary').flush({ domains: [], factTypes: [] });
    service.factHistory(1).subscribe();
    http.expectOne('/api/admin/facts/1/history').flush([]);
    service.createFact({ domain: 'comptes', key: 'k', value: 'v', factType: 'fee' } as any).subscribe();
    expect(http.expectOne('/api/admin/facts').request.method).toBe('POST');
    service.updateFact(1, { domain: 'comptes', key: 'k', value: 'v2', factType: 'fee', reason: 'r' } as any).subscribe();
    expect(http.expectOne('/api/admin/facts/1').request.method).toBe('PUT');
    service.reactivateFact(1, 'back').subscribe();
    expect(http.expectOne('/api/admin/facts/1/reactivate').request.body).toEqual({ reason: 'back' });
  });

  it('covers taxonomy vocabulary, exemplar CRUD, releases and ingestion status', () => {
    service.taxonomyVocabulary().subscribe();
    http.expectOne('/api/admin/taxonomy/vocabulary').flush({ domains: [], intents: [] });
    service.listExemplars().subscribe();
    http.expectOne(r => r.url === '/api/admin/taxonomy/exemplars' && !r.params.has('domain')).flush([]);
    service.listExemplars('service').subscribe();
    http.expectOne(r => r.url === '/api/admin/taxonomy/exemplars' && r.params.get('domain') === 'service').flush([]);
    service.createExemplar({ domain: 'service', intent: 'location', text: 't', language: 'fr' } as any).subscribe();
    expect(http.expectOne('/api/admin/taxonomy/exemplars').request.method).toBe('POST');
    service.updateExemplar(4, { domain: 'service', intent: 'location', text: 't2', language: 'fr' } as any).subscribe();
    expect(http.expectOne('/api/admin/taxonomy/exemplars/4').request.method).toBe('PUT');
    service.deactivateExemplar(4).subscribe();
    expect(http.expectOne('/api/admin/taxonomy/exemplars/4').request.method).toBe('DELETE');
    service.reactivateExemplar(4).subscribe();
    expect(http.expectOne('/api/admin/taxonomy/exemplars/4/reactivate').request.method).toBe('POST');
    service.listReleases().subscribe();
    http.expectOne('/api/admin/taxonomy/releases').flush([]);
  });
});
