import { Component, ChangeDetectorRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { TranslatePipe, TranslationService } from '../../services/translation.service';
import { environment } from '../../../environments/environment';

type LoginTab = 'client' | 'staff';

/**
 * Unified login for the two identity types:
 *  - Espace Client  → bank clients (`clients` table, ROLE_UTILISATEUR) → /portal
 *  - Espace Personnel → bank staff (`users` table) → /admin/dashboard
 * Both tabs post to the same /api/auth/login endpoint; the backend resolves
 * the identity and the redirect follows the returned role.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe],
  templateUrl: './login.html',
  styleUrls: ['./login.css']
})
export class LoginComponent implements OnInit {
  readonly year = new Date().getFullYear();
  /**
   * The one-click buttons sign in with the development fixture's accounts. They
   * are compiled out of a production build, together with the credentials they
   * would send.
   */
  readonly isDev = !environment.production;
  tab: LoginTab = 'client';
  email = '';
  password = '';
  errorMessage = '';
  loading = false;
  justCreated = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef,
    public i18n: TranslationService
  ) {}

  get i18nDir(): 'rtl' | 'ltr' {
    return this.i18n.lang() === 'ar' ? 'rtl' : 'ltr';
  }

  ngOnInit() {
    // Freshly registered clients land here from /register.
    this.justCreated = this.route.snapshot.queryParamMap.get('created') === '1';
    const requested = this.route.snapshot.queryParamMap.get('tab');
    if (requested === 'staff') {
      this.tab = 'staff';
    }
    // Already signed in? Go straight to the right space.
    if (this.authService.getAccessToken()) {
      this.router.navigateByUrl(this.authService.homeForCurrentUser());
    }
  }

  switchTab(tab: LoginTab) {
    this.tab = tab;
    this.errorMessage = '';
    this.cdr.detectChanges();
  }

  quickLogin(role: 'staff' | 'client') {
    this.errorMessage = '';
    this.loading = true;
    this.cdr.detectChanges();
    const target = role === 'staff' ? 'admin' : 'client';
    this.authService.devQuickLogin(target).subscribe({
      next: () => {
        this.loading = false;
        this.cdr.detectChanges();
        this.router.navigateByUrl(this.authService.homeForCurrentUser());
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err?.error?.message || 'Échec de la connexion automatique.';
        this.cdr.detectChanges();
      }
    });
  }

  onSubmit() {
    this.errorMessage = '';
    this.justCreated = false;

    if (!this.email || !this.password) {
      this.errorMessage = 'Veuillez saisir votre email et votre mot de passe.';
      this.cdr.detectChanges();
      return;
    }

    this.loading = true;
    this.cdr.detectChanges();

    this.authService.login(this.email, this.password).subscribe({
      next: () => {
        this.loading = false;
        this.cdr.detectChanges();
        this.router.navigateByUrl(this.authService.homeForCurrentUser());
      },
      error: (err) => {
        this.loading = false;
        if (err.status === 0) {
          this.errorMessage = 'Impossible de contacter le serveur. Réessayez dans un instant.';
        } else if (err.status === 401 || err.status === 403) {
          this.errorMessage = 'Identifiants invalides.';
        } else {
          this.errorMessage = err?.error?.message || 'Identifiants invalides ou erreur serveur.';
        }
        this.cdr.detectChanges();
      }
    });
  }
}
