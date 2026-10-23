import { Injectable, signal } from '@angular/core';

/**
 * Shared open/close state of the floating chat widget, so any surface
 * (landing navbar CTA, assistant showcase, FAB) can open the same panel.
 */
@Injectable({ providedIn: 'root' })
export class ChatWidgetService {
  private readonly openSignal = signal(false);

  readonly isOpen = this.openSignal.asReadonly();

  openWidget() {
    this.openSignal.set(true);
  }

  closeWidget() {
    this.openSignal.set(false);
  }

  toggle() {
    this.openSignal.update(v => !v);
  }
}
