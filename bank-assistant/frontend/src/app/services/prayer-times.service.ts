import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, of, catchError, map } from 'rxjs';
import { environment } from '../../environments/environment';

export type PrayerKey = 'fajr' | 'dhuhr' | 'asr' | 'maghrib' | 'isha';

export interface NextPrayer {
  key: PrayerKey;
  time: string;
  at: Date;
}

export interface PrayerTimings {
  fajr: string;
  dhuhr: string;
  asr: string;
  maghrib: string;
  isha: string;
  next: NextPrayer | null;
}

/** Shape of the Aladhan `timingsByCity` response (we only use `data.timings`). */
interface AladhanResponse {
  code: number;
  status: string;
  data?: { timings?: Record<string, string> };
}

export const PRAYER_ORDER: PrayerKey[] = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'];

/**
 * Prayer times for the default city (environment.prayerCity/country, Aladhan
 * method — Tunisia by default, docs/UIUX_RESEARCH.md §5).
 *
 * Fails open: any network/parse error resolves to `of(null)` so the widget
 * can hide itself instead of breaking the portal.
 */
@Injectable({ providedIn: 'root' })
export class PrayerTimesService {
  constructor(private http: HttpClient) {}

  timings(): Observable<PrayerTimings | null> {
    const url =
      `${environment.prayerApiUrl}/timingsByCity` +
      `?city=${encodeURIComponent(environment.prayerCity)}` +
      `&country=${encodeURIComponent(environment.prayerCountry)}` +
      `&method=${environment.prayerMethod}`;
    return this.http.get<AladhanResponse>(url).pipe(
      map(res => this.parse(res)),
      catchError((err: HttpErrorResponse) => {
        console.warn('Prayer times unavailable:', err?.status);
        return of(null);
      })
    );
  }

  private parse(res: AladhanResponse | null): PrayerTimings | null {
    const t = res?.data?.timings;
    if (!t) {
      return null;
    }
    const base: Record<PrayerKey, string> = {
      fajr: this.clean(t['Fajr']),
      dhuhr: this.clean(t['Dhuhr']),
      asr: this.clean(t['Asr']),
      maghrib: this.clean(t['Maghrib']),
      isha: this.clean(t['Isha'])
    };
    if (PRAYER_ORDER.some(k => !this.isTime(base[k]))) {
      return null;
    }
    return { ...base, next: this.nextPrayer(base) };
  }

  /** Aladhan returns "04:55 (+01)" — keep the clock only. */
  private clean(raw: string | undefined): string {
    return (raw ?? '').split(' ')[0].trim();
  }

  private isTime(s: string): boolean {
    return /^\d{2}:\d{2}$/.test(s);
  }

  /** The next prayer strictly after `now` (rolls to tomorrow's Fajr). */
  nextPrayer(base: Record<PrayerKey, string>, now: Date = new Date()): NextPrayer | null {
    for (const key of PRAYER_ORDER) {
      const at = this.at(base[key], now, 0);
      if (at.getTime() > now.getTime()) {
        return { key, time: base[key], at };
      }
    }
    const fajrAt = this.at(base.fajr, now, 1);
    return { key: 'fajr', time: base.fajr, at: fajrAt };
  }

  private at(hhmm: string, ref: Date, dayOffset: number): Date {
    const [h, m] = hhmm.split(':').map(Number);
    const d = new Date(ref);
    d.setDate(d.getDate() + dayOffset);
    d.setHours(h, m, 0, 0);
    return d;
  }
}
