import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Send each staff role to the first page it is actually allowed to use. */
@Injectable({ providedIn: 'root' })
export class AdminHomeGuard implements CanActivate {
  constructor(private auth: AuthService, private router: Router) {}

  canActivate(): UrlTree {
    return this.router.parseUrl(this.auth.homeForCurrentUser());
  }
}
