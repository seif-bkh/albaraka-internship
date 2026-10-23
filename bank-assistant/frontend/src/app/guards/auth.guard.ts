import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {
  constructor(private authService: AuthService, private router: Router) {}

  canActivate(): boolean | Observable<boolean> {
    if (this.authService.getAccessToken()) {
      return true;
    }
    // In local development, seamlessly auto-authenticate as client for portal routes
    if (!environment.production && !this.authService.getAccessToken()) {
      return this.authService.devQuickLogin('client').pipe(
        map(() => true),
        catchError(() => {
          this.router.navigate(['/login']);
          return of(false);
        })
      );
    }
    this.router.navigate(['/login']);
    return false;
  }
}
