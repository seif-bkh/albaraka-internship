import { Component, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { TranslatePipe, TranslationService } from '../../services/translation.service';

/**
 * Client account creation (POST /api/auth/register-client).
 * Creates an UTILISATEUR identity; optional phone is stored on the profile.
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe],
  templateUrl: './register.html',
  styleUrl: './register.css'
})
export class RegisterComponent {
  readonly year = new Date().getFullYear();
  readonly i18n = inject(TranslationService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);

  prenom = '';
  nom = '';
  email = '';
  telephone = '';
  password = '';
  confirm = '';
  acceptTerms = false;
  errorMessage = '';
  loading = false;

  get dir(): 'rtl' | 'ltr' {
    return this.i18n.lang() === 'ar' ? 'rtl' : 'ltr';
  }

  onSubmit() {
    this.errorMessage = '';
    if (!this.prenom || !this.nom || !this.email || !this.password) {
      this.errorMessage = 'Veuillez remplir tous les champs obligatoires.';
      return;
    }
    if (this.password.length < 8) {
      this.errorMessage = 'Le mot de passe doit contenir au moins 8 caractères.';
      return;
    }
    if (this.password !== this.confirm) {
      this.errorMessage = this.i18n.t('reg.mismatch');
      return;
    }
    if (!this.acceptTerms) {
      this.errorMessage = this.i18n.t('reg.terms');
      return;
    }

    this.loading = true;
    this.cdr.detectChanges();

    this.authService.registerClient(this.nom, this.prenom, this.email, this.password, this.telephone).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/login'], { queryParams: { created: '1' } });
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err?.error?.error
          || err?.error?.message
          || "Erreur lors de la création du compte. Vérifiez vos informations et réessayez.";
        this.cdr.detectChanges();
      }
    });
  }
}
