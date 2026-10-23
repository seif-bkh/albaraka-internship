import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AdminMonitoring, RealtimeTurn } from './admin-monitoring';
import { environment } from '../../../environments/environment';

describe('AdminMonitoring', () => {
  let component: AdminMonitoring;
  let fixture: ComponentFixture<AdminMonitoring>;
  let httpMock: HttpTestingController;

  const mockLiveTurns: RealtimeTurn[] = [
    {
      id: 1024,
      userId: 55,
      sessionId: 'abc-123-session',
      question: 'ما هو أفضل بنك إسلامي؟',
      answer: 'أعتذر، لا يمكنني المقارنة التفضيلية.',
      sources: ['doc_12', 'faq_3'],
      documents: [
        { document: 'Guide_Interne.docx', title: 'Guide interne', heading: 'Mourabaha',
          chunkId: 'c17', similarity: 0.81, kept: true, cited: true, engine: 'LEGACY',
          label: 'Guide_Interne.docx › Mourabaha' },
        { document: 'Loi_2016-48.pdf', title: 'Loi 2016-48', heading: null, chunkId: 'c02',
          similarity: 0.21, kept: false, cited: false, engine: 'LEGACY', label: 'Loi_2016-48.pdf' }
      ],
      clientId: 55,
      intention: 'commercial',
      langue: 'ar',
      domaine: 'financement',
      grounded: true,
      hasTrace: true,
      model: 'gpt-4o',
      tokensIn: 120,
      tokensOut: 80,
      latencyMs: 820,
      status: 'ANSWERED',
      askedAt: '2026-09-20T14:32:11Z',
      answeredAt: '2026-09-20T14:32:12Z'
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminMonitoring],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminMonitoring);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    component.ngOnDestroy();
    httpMock.verify();
  });

  it('loads live feed and default monitoring data on init', () => {
    fixture.detectChanges();

    // Expect turns call
    const turnsReq = httpMock.expectOne(`${environment.apiUrl}/admin/monitoring/turns?limit=100&engine=ALL`);
    turnsReq.flush([]);

    // Expect kpis call
    const kpisReq = httpMock.expectOne(`${environment.apiUrl}/admin/monitoring/kpis?window=200`);
    kpisReq.flush({ window: 200, turns: 0, engines: [] });

    // Expect live-feed call
    const liveReq = httpMock.expectOne(`${environment.apiUrl}/admin/monitoring/live-feed?limit=50`);
    liveReq.flush(mockLiveTurns);

    expect(component.liveTurns.length).toBe(1);
    expect(component.liveTurns[0].id).toBe(1024);
    expect(component.liveTurns[0].userId).toBe(55);
    expect(component.liveTurns[0].model).toBe('gpt-4o');
    expect(component.liveTurns[0].latencyMs).toBe(820);
    expect(component.liveTurns[0].sources).toEqual(['doc_12', 'faq_3']);
  });

  it('toggles auto refresh state', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/monitoring/turns?limit=100&engine=ALL`).flush([]);
    httpMock.expectOne(`${environment.apiUrl}/admin/monitoring/kpis?window=200`).flush({ window: 200, turns: 0, engines: [] });
    httpMock.expectOne(`${environment.apiUrl}/admin/monitoring/live-feed?limit=50`).flush(mockLiveTurns);

    expect(component.autoRefresh).toBe(true);
    component.toggleAutoRefresh();
    expect(component.autoRefresh).toBe(false);
  });

  it('selects a live turn to inspect', () => {
    component.selectLiveTurn(mockLiveTurns[0]);
    expect(component.selectedLiveTurn).toEqual(mockLiveTurns[0]);
  });

  it('lists the documents the assistant consulted, dropped ones included', () => {
    component.selectLiveTurn(mockLiveTurns[0]);
    const docs = component.documentsOf(component.selectedLiveTurn);
    expect(docs.length).toBe(2);
    expect(docs[0].cited).toBe(true);
    expect(docs[1].kept).toBe(false);
  });

  it('polls only for what is newer and updates a row in place', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/monitoring/turns?limit=100&engine=ALL`).flush([]);
    httpMock.expectOne(`${environment.apiUrl}/admin/monitoring/kpis?window=200`)
      .flush({ window: 200, turns: 0, engines: [] });
    httpMock.expectOne(`${environment.apiUrl}/admin/monitoring/live-feed?limit=50`).flush([mockLiveTurns[0]]);
    expect(component.liveTurns.length).toBe(1);

    // A question that has just arrived shows up as IN_PROGRESS, then the same row
    // comes back answered: the feed grows and updates, it never resets.
    component.loadLiveFeed(true);
    const next = httpMock.expectOne(
      `${environment.apiUrl}/admin/monitoring/live-feed?limit=50&since=${encodeURIComponent('2026-09-20T14:32:11Z')}`);
    next.flush([
      { ...mockLiveTurns[0], id: 1025, status: 'IN_PROGRESS', answer: null,
        askedAt: '2026-09-20T14:40:00Z', answeredAt: null, sources: [], documents: [] }
    ]);
    expect(component.liveTurns.length).toBe(2);
    expect(component.liveTurns[0].id).toBe(1025);
    expect(component.liveTurns[1].answer).toContain('أعتذر');
  });
});
