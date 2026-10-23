import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe, TranslationService } from '../../services/translation.service';
import { NewsService, NewsItem } from '../../services/news.service';

/**
 * News feed (P2): active, already-published items, newest first.
 * Renders nothing when there is no news (the host never shows an empty card).
 */
@Component({
  selector: 'app-news-section',
  standalone: true,
  imports: [CommonModule, DatePipe, TranslatePipe],
  templateUrl: './news-section.html',
  styleUrl: './news-section.css'
})
export class NewsSectionComponent implements OnInit {
  private readonly api = inject(NewsService);
  readonly i18n = inject(TranslationService);

  @Input() max = 3;
  items: NewsItem[] = [];

  ngOnInit(): void {
    this.api.active(this.max)
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: items => { this.items = items; },
        error: () => { this.items = []; }
      });
  }
}
