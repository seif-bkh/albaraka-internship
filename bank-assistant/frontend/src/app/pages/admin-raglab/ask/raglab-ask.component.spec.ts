import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RaglabAskComponent } from './raglab-ask.component';
import { RaglabService } from '../../../services/raglab.service';
import { describe, it, expect, beforeEach } from 'vitest';

describe('RaglabAskComponent', () => {
  let component: RaglabAskComponent;
  let fixture: ComponentFixture<RaglabAskComponent>;
  let raglab: RaglabService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RaglabAskComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(RaglabAskComponent);
    component = fixture.componentInstance;
    raglab = TestBed.inject(RaglabService);
  });

  it('initializes with empty chat flow and renders empty state', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Console RAGLab');
  });

  it('renders answered turn with claim cards and citations', () => {
    component.turns = [{
      id: 'turn_1',
      question: 'Quel est le prix ?',
      timestamp: new Date(),
      claimsExpanded: true,
      sourcesExpanded: true,
      response: {
        status: 'answered',
        reason: 'supported',
        answer: 'Le tarif est de 10 DT. [S1]',
        claims: [{
          text: 'Le tarif est de 10 DT.',
          evidence: [{ source_id: 'S1', quote: '10 DT' }]
        }],
        sources: [{
          source_id: 'S1',
          document: 'tarifs.md',
          heading: 'Frais',
          text: 'Tarif standard: 10 DT.'
        }],
        cached: false,
        seconds: 1.2
      }
    }];

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Quel est le prix ?');
    expect(compiled.textContent).toContain('Le tarif est de 10 DT.');
    expect(compiled.textContent).toContain('tarifs.md');
    expect(compiled.textContent).toContain('Frais');
  });

  it('renders refused turn with calm styling and reason taxonomy', () => {
    component.turns = [{
      id: 'turn_2',
      question: 'Mon solde bancaire ?',
      timestamp: new Date(),
      response: {
        status: 'refused',
        reason: 'private_or_live_request',
        answer: 'Je ne peux pas répondre car je n\'ai pas accès aux données privées.',
        inference_performed: false
      }
    }];

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Réponse sécurisée');
    expect(compiled.textContent).toContain('Données personnelles ou temps réel non accessibles');
  });
});
