import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe, TranslationService } from '../../services/translation.service';
import { AuthService } from '../../services/auth.service';

/**
 * Shell of the bank client portal: a banking-style topbar (brand, primary
 * navigation, language switcher, account menu) above the routed pages.
 */
@Component({
  selector: 'app-portal-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './portal-layout.html',
  styleUrl: './portal-layout.css'
})
export class PortalLayoutComponent {
  readonly i18n = inject(TranslationService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly year = new Date().getFullYear();

  get firstName(): string {
    return this.auth.currentUserValue?.email?.split('@')[0] ?? '';
  }

  get initials(): string {
    const email = this.auth.currentUserValue?.email ?? '';
    return email ? email.slice(0, 2).toUpperCase() : 'AB';
  }

  get dir(): 'rtl' | 'ltr' {
    return this.i18n.lang() === 'ar' ? 'rtl' : 'ltr';
  }

  setLang(lang: string) {
    this.i18n.setLang(lang);
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
