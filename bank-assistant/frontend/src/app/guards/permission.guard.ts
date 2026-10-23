import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({ providedIn: 'root' })
export class PermissionGuard implements CanActivate {
  constructor(private auth: AuthService, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot): boolean | UrlTree {
    const permissions = (route.data['permissions'] as string[] | undefined) ?? [];
    const mode = route.data['permissionMode'] === 'all' ? 'all' : 'any';
    if (!permissions.length) return false;

    const allowed = mode === 'all'
      ? permissions.every(permission => this.auth.hasPermission(permission))
      : permissions.some(permission => this.auth.hasPermission(permission));
    return allowed ? true : this.router.parseUrl(this.auth.homeForCurrentUser());
  }
}
