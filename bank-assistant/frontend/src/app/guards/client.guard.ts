import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({ providedIn: 'root' })
export class ClientGuard implements CanActivate {
  constructor(private authService: AuthService, private router: Router) {}

  canActivate(): boolean {
    if (this.authService.getAccessToken() && this.authService.hasRole('UTILISATEUR') && !this.authService.isStaff()) {
      return true;
    }
    // A logged-in staff member has no business in the client portal.
    this.router.navigate([this.authService.getAccessToken() ? this.authService.homeForCurrentUser() : '/login']);
    return false;
  }
}
