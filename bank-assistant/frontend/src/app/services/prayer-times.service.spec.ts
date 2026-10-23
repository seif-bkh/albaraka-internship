import { TestBed } from '@angular/core/testing';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PrayerTimesService, PrayerKey } from './prayer-times.service';

describe('PrayerTimesService', () => {
  let service: PrayerTimesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PrayerTimesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => { http.verify(); });

  const aladhanResponse = {
    code: 200,
    status: 'OK',
    data: {
      timings: {
        Fajr: '04:55 (+01)', Sunrise: '06:12 (+01)', Dhuhr: '12:14 (+01)',
        Asr: '15:37 (+01)', Maghrib: '18:29 (+01)', Isha: '19:54 (+01)'
      }
    }
  };

  it('queries timingsByCity with the configured city/country/method and cleans times', () => {
    let seen: any;
    service.timings().subscribe(r => (seen = r));
    const req = http.expectOne(r =>
      r.url.startsWith('/api') === false &&
      r.url.includes('timingsByCity') &&
      r.url.includes('city=Tunis') &&
      r.url.includes('country=Tunisia') &&
      r.url.includes('method=18'));
    req.flush(aladhanResponse);

    expect(seen.fajr).toBe('04:55');
    expect(seen.maghrib).toBe('18:29');
    expect(seen.next).toBeTruthy();
  });

  it('resolves the next prayer strictly after now', () => {
    const base: Record<PrayerKey, string> = {
      fajr: '04:55', dhuhr: '12:14', asr: '15:37', maghrib: '18:29', isha: '19:54'
    };
    // At 10:00 the next prayer is Dhuhr.
    let now = new Date(2026, 8, 7, 10, 0, 0);
    expect(service.nextPrayer(base, now)).toEqual(
      expect.objectContaining({ key: 'dhuhr', time: '12:14' }));
    // At 20:00 everything is past → tomorrow's Fajr.
    now = new Date(2026, 8, 7, 20, 0, 0);
    const next = service.nextPrayer(base, now);
    expect(next?.key).toBe('fajr');
    expect(next?.at.getDate()).toBe(8);
  });

  it('fails open on network errors and malformed payloads', () => {
    let seen: any = 'unset';
    service.timings().subscribe(r => (seen = r));
    http.expectOne(r => !r.url.startsWith('/api'))
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    expect(seen).toBeNull();

    let malformed: any = 'unset';
    service.timings().subscribe(r => (malformed = r));
    http.expectOne(r => !r.url.startsWith('/api')).flush({ code: 200, data: { timings: {} } });
    expect(malformed).toBeNull();
  });
});
