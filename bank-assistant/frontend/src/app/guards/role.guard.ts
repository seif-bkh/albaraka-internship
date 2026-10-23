import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({ providedIn: 'root' })
export class RoleGuard implements CanActivate {
  constructor(private authService: AuthService, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot): boolean | UrlTree {
    const expectedRoles = (route.data['expectedRoles'] as string[] | undefined)
      ?? [route.data['expectedRole'] as string | undefined].filter((role): role is string => !!role);
    const allowed = expectedRoles.some(role => this.authService.hasRole(role));
    if (this.authService.getAccessToken() && allowed) return true;
    return this.router.parseUrl(this.authService.homeForCurrentUser());
  }
}
