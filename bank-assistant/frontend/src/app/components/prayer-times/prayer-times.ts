import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslationService } from '../../services/translation.service';
import { PrayerTimesService, PrayerTimings, PrayerKey, PRAYER_ORDER } from '../../services/prayer-times.service';

/**
 * Prayer times card (P2). Fails open: on any error the component renders
 * nothing — a decorative widget must never break the portal.
 */
@Component({
  selector: 'app-prayer-times',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './prayer-times.html',
  styleUrl: './prayer-times.css'
})
export class PrayerTimesComponent implements OnInit, OnDestroy {
  private readonly api = inject(PrayerTimesService);
  readonly i18n = inject(TranslationService);

  timings: PrayerTimings | null = null;
  countdown = '';

  private timer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.api.timings()
      .pipe(takeUntilDestroyed())
      .subscribe(t => {
        this.timings = t; // null → widget hidden
        if (t) {
          this.updateCountdown();
          this.timer = setInterval(() => this.updateCountdown(), 60_000);
        }
      });
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  /** Rows in worship order, for the template. */
  get prayers(): { key: PrayerKey; time: string }[] {
    if (!this.timings) {
      return [];
    }
    return PRAYER_ORDER.map(key => ({ key, time: this.timings![key] }));
  }

  updateCountdown(): void {
    const next = this.timings?.next;
    if (!next) {
      this.countdown = '';
      return;
    }
    const ms = next.at.getTime() - Date.now();
    if (ms <= 0) {
      this.countdown = '';
      return;
    }
    const totalMin = Math.floor(ms / 60_000);
    const h = Math.floor(totalMin / 60);
    const m = totalMin % 60;
    this.countdown = h > 0 ? `${h}h${String(m).padStart(2, '0')}` : `${m}min`;
  }
}
