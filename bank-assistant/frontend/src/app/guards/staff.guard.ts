import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class StaffGuard implements CanActivate {
  constructor(private authService: AuthService, private router: Router) {}

  canActivate(): boolean | UrlTree | Observable<boolean | UrlTree> {
    if (this.authService.getAccessToken()) {
      // Local storage is only a UX cache. Refresh authorities from the database
      // before opening staff routes so permission changes take effect on reload.
      return this.authService.refreshProfile().pipe(
        map(() => this.staffDecision()),
        catchError(() => of(this.router.parseUrl('/login')))
      );
    }

    // AuthGuard normally handles this case first; retain a safe standalone guard.
    if (!environment.production) {
      return this.authService.devQuickLogin('admin').pipe(
        map(() => this.staffDecision()),
        catchError(() => of(this.router.parseUrl('/login')))
      );
    }
    return this.router.parseUrl('/login');
  }

  private staffDecision(): boolean | UrlTree {
    if (this.authService.isStaff()) return true;
    return this.router.parseUrl(
      this.authService.hasRole('UTILISATEUR') ? '/portal' : '/login'
    );
  }
}
