import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ClientService } from './client.service';

describe('ClientService', () => {
  let service: ClientService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ClientService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => { http.verify(); localStorage.clear(); });

  it('fetches the profile from /api/client/me', () => {
    let seen: any;
    service.me().subscribe(r => (seen = r));
    http.expectOne('/api/client/me').flush({ id: 7, email: 'c@t.tn', nom: 'N', prenom: 'P' });
    expect(seen.id).toBe(7);
  });

  it('PUTs the editable profile patch only', () => {
    service.updateMe({ ville: 'Tunis' }).subscribe();
    const req = http.expectOne('/api/client/me');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ ville: 'Tunis' });
    req.flush({ id: 7 });
  });

  it('loads comptes, cartes and transactions with query params', () => {
    service.comptes().subscribe();
    http.expectOne('/api/client/me/comptes').flush([]);

    service.cartes().subscribe();
    http.expectOne('/api/client/me/cartes').flush([]);

    let tx: any;
    service.transactions(2, 5).subscribe(r => (tx = r));
    const req = http.expectOne(r => r.url === '/api/client/me/transactions'
      && r.params.get('compteId') === '2' && r.params.get('limit') === '5');
    req.flush([{ id: 1 }]);
    expect(tx.length).toBe(1);
  });

  it('loads conversations and a single conversation detail', () => {
    service.conversations().subscribe();
    http.expectOne('/api/client/me/conversations').flush([]);

    service.conversationMessages(12).subscribe();
    http.expectOne('/api/client/me/conversations/12/messages').flush([]);
  });

  it('lists simulation products and POSTs a financement simulation', () => {
    let products: string[] = [];
    service.simulationProducts().subscribe(r => (products = r));
    http.expectOne('/api/client/me/simulation/products').flush(['Sayarat Al Baraka']);
    expect(products).toEqual(['Sayarat Al Baraka']);

    service.simulateFinancement({ product: 'Sayarat Al Baraka', amount: 120000, months: 60 }).subscribe();
    const req = http.expectOne('/api/client/me/simulation/financement');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ product: 'Sayarat Al Baraka', amount: 120000, months: 60 });
    req.flush({ rateAvailable: true });
  });

  it('fetches advisor contacts and creates advisor requests', () => {
    service.advisorContacts().subscribe();
    http.expectOne('/api/client/me/advisor/contacts').flush(['71 18 65 00']);

    service.createAdvisorRequest({ kind: 'CALLBACK', telephone: '+216 20 123 456' }).subscribe();
    const req = http.expectOne('/api/client/me/advisor/requests');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.kind).toBe('CALLBACK');
    req.flush({ id: 1, kind: 'CALLBACK', status: 'EN_ATTENTE', createdAt: 'x' });
  });
});
