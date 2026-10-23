import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { RaglabLandingGuard } from './raglab-landing.guard';
import { AuthService } from '../services/auth.service';

describe('RaglabLandingGuard', () => {
  let guard: RaglabLandingGuard;
  let auth: { hasPermission: (p: string) => boolean };
  let router: { parseUrl: (url: string) => unknown };

  beforeEach(() => {
    auth = { hasPermission: vi.fn() };
    router = { parseUrl: vi.fn((url: string) => ({ url })) };

    TestBed.configureTestingModule({
      providers: [
        RaglabLandingGuard,
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    guard = TestBed.inject(RaglabLandingGuard);
  });

  it('opens the ask screen for someone allowed to run queries', () => {
    (auth.hasPermission as any).mockImplementation((p: string) => p === 'RAGLAB_CONSOLE_RUN');
    expect(guard.canActivate()).toEqual({ url: '/admin/raglab/ask' });
  });

  it('opens the corpus for a read-only viewer instead of bouncing them out', () => {
    (auth.hasPermission as any).mockReturnValue(false);
    expect(guard.canActivate()).toEqual({ url: '/admin/raglab/documents' });
  });
});
