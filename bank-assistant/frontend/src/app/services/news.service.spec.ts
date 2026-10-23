import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { NewsService } from './news.service';

describe('NewsService', () => {
  let service: NewsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(NewsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => { http.verify(); });

  it('fetches the public active feed with the limit', () => {
    let seen: any;
    service.active(4).subscribe(r => (seen = r));
    http.expectOne(r => r.url === '/api/news/active' && r.params.get('limit') === '4').flush([]);
    expect(seen).toEqual([]);
  });

  it('creates an item via the admin API', () => {
    service.create({ title: 'T', body: 'B', publishedAt: '2026-09-07' }).subscribe();
    const req = http.expectOne('/api/admin/news');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ title: 'T', body: 'B', publishedAt: '2026-09-07' });
    req.flush({ id: 1 });
  });

  it('toggles and deletes by id', () => {
    service.toggleActive(7).subscribe();
    http.expectOne('/api/admin/news/7/toggle-active').flush({ id: 7, active: false });

    service.remove(7).subscribe();
    const req = http.expectOne('/api/admin/news/7');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
