import { Component, HostListener, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { TranslatePipe, TranslationService } from '../../services/translation.service';
import { ChatWidgetService } from '../../services/chat-widget.service';
import { ChatWidgetComponent } from '../../components/chat-widget/chat-widget';
import { NewsSectionComponent } from '../../components/news-section/news-section';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslatePipe, ChatWidgetComponent, NewsSectionComponent],
  templateUrl: './landing.html',
  styleUrl: './landing.css'
})
export class LandingComponent {
  readonly year = new Date().getFullYear();
  readonly i18n = inject(TranslationService);
  readonly widget = inject(ChatWidgetService);
  private router = inject(Router);

  /** Solid navbar once the hero is scrolled past. */
  scrolled = false;

  readonly products = [
    { icon: 'mourabaha', key: 'mourabaha' },
    { icon: 'ijara', key: 'ijara' },
    { icon: 'car', key: 'sayarat' },
    { icon: 'travel', key: 'rahalet' },
    { icon: 'card', key: 'cards' },
    { icon: 'pack', key: 'yasmine' },
  ];

  readonly services = [
    { icon: 'phone', key: 'banecti' },
    { icon: 'calc', key: 'simulator' },
    { icon: 'calendar', key: 'appointment' },
    { icon: 'change', key: 'change' },
  ];

  readonly faqs = [1, 2, 3, 4, 5];

  @HostListener('window:scroll')
  onScroll() {
    this.scrolled = window.scrollY > 24;
  }

  get dir(): 'rtl' | 'ltr' {
    return this.i18n.lang() === 'ar' ? 'rtl' : 'ltr';
  }

  setLang(lang: string) {
    this.i18n.setLang(lang);
  }

  goto(sectionId: string) {
    document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  openAssistant() {
    this.widget.openWidget();
  }
}
