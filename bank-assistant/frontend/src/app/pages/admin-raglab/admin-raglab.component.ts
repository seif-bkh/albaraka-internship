import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { RaglabStatusBarComponent } from './status-bar/raglab-status-bar.component';
import { AuthService } from '../../services/auth.service';
import { RaglabService } from '../../services/raglab.service';

/**
 * The console shell: one header, one instrument strip, five screens.
 *
 * <p>It owns two lifecycle facts the screens should not have to think about: the
 * `/health` poll runs only while this component is mounted (the service is
 * application-wide, so a poll started in its constructor followed the operator into the
 * portal and the public site), and the two permission flags are read from signals rather
 * than through getters, so `OnPush` cannot leave a tab hidden after the user resolves.
 */
@Component({
  selector: 'app-admin-raglab',
  standalone: true,
  imports: [CommonModule, RouterModule, RaglabStatusBarComponent],
  templateUrl: './admin-raglab.component.html',
  styleUrl: './admin-raglab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminRaglabComponent implements OnInit {
  private readonly raglab = inject(RaglabService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly canRun = signal(false);
  readonly canManage = signal(false);

  constructor() {
    this.auth.currentUser$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.readPermissions());
  }

  ngOnInit(): void {
    this.readPermissions();
    this.raglab.startPolling();
  }

  ngOnDestroy(): void {
    this.raglab.stopPolling();
  }

  private readPermissions(): void {
    this.canRun.set(this.auth.hasPermission('RAGLAB_CONSOLE_RUN'));
    this.canManage.set(this.auth.hasPermission('RAGLAB_CONSOLE_MANAGE'));
  }
}
