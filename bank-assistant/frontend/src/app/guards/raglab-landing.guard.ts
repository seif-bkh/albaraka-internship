import { Injectable, inject } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Sends `/admin/raglab` to the first screen the caller is actually allowed to open.
 *
 * <p>The console has a read-only audience (document managers and auditors who may look
 * at the corpus but must not run model-backed queries). Redirecting everyone to "ask"
 * meant the guard on that child route bounced them straight back out of the console they
 * had just clicked on, which read as a broken link rather than as a permission boundary.
 */
@Injectable({ providedIn: 'root' })
export class RaglabLandingGuard implements CanActivate {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  canActivate(): UrlTree {
    const target = this.auth.hasPermission('RAGLAB_CONSOLE_RUN') ? 'ask' : 'documents';
    return this.router.parseUrl(`/admin/raglab/${target}`);
  }
}
