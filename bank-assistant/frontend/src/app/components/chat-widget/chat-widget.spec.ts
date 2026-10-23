import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatWidgetComponent } from './chat-widget';
import { ChatWidgetService } from '../../services/chat-widget.service';

describe('ChatWidgetComponent', () => {
  let component: ChatWidgetComponent;
  let fixture: ComponentFixture<ChatWidgetComponent>;
  let widgetService: ChatWidgetService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatWidgetComponent],
      providers: [
        ChatWidgetService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ChatWidgetComponent);
    component = fixture.componentInstance;
    widgetService = TestBed.inject(ChatWidgetService);
    fixture.detectChanges();
  });

  it('initializes in closed state', () => {
    expect(widgetService.isOpen()).toBe(false);
  });

  it('toggles open state and adds welcome message if empty', () => {
    component.toggle();
    expect(widgetService.isOpen()).toBe(true);
    expect(component.messages.length).toBeGreaterThan(0);
    expect(component.messages[0].role).toBe('assistant');
  });

  it('renders no provenance for the customer, even if an API ever sends it', () => {
    // Sources are admin-only: nothing in the widget may show citations, a
    // "source:" line or a link list. This guards the removal, not a toggle.
    component.toggle();
    component.messages = [
      { role: 'assistant', content: "Le Mourabaha couvre l'automobile.", time: '12:00' }
    ];
    fixture.detectChanges();

    const panel: HTMLElement = fixture.nativeElement;
    expect(panel.querySelector('.sources-drawer')).toBeNull();
    expect(panel.querySelector('.sources-toggle')).toBeNull();
    expect(panel.querySelector('.sources-list')).toBeNull();
    expect(panel.querySelector('.bubble')?.textContent?.trim())
      .toBe("Le Mourabaha couvre l'automobile.");
  });
});
