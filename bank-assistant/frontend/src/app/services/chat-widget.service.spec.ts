import { TestBed } from '@angular/core/testing';

import { ChatWidgetService } from './chat-widget.service';

describe('ChatWidgetService', () => {
  let service: ChatWidgetService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChatWidgetService);
  });

  it('starts closed and toggles open/close', () => {
    expect(service.isOpen()).toBe(false);
    service.openWidget();
    expect(service.isOpen()).toBe(true);
    service.closeWidget();
    expect(service.isOpen()).toBe(false);
  });

  it('toggle flips the current state', () => {
    service.toggle();
    expect(service.isOpen()).toBe(true);
    service.toggle();
    expect(service.isOpen()).toBe(false);
  });
});
