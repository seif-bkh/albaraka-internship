import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { RaglabStatusBarComponent } from './raglab-status-bar.component';
import { RaglabService } from '../../../services/raglab.service';
import { describe, it, expect, beforeEach } from 'vitest';

describe('RaglabStatusBarComponent', () => {
  let component: RaglabStatusBarComponent;
  let fixture: ComponentFixture<RaglabStatusBarComponent>;
  let raglab: RaglabService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule, RaglabStatusBarComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(RaglabStatusBarComponent);
    component = fixture.componentInstance;
    raglab = TestBed.inject(RaglabService);
  });

  it('renders status bar with profile and index pills', () => {
    raglab.health.set({
      status: 'ok',
      version: '1.2.3',
      profile: {
        embedding: { provider: 'nvidia', model: 'nvidia/nemotron-3-embed-1b' },
        answer: { provider: 'xkiro', model: 'qwen/qwen3.8-max:free' },
        chunking: { mode: 'restructure', size: 220, overlap: 40 },
        retrieval: { top_k: 5, mode: 'vector' },
        pipeline: 'supported pipeline — the same model pair main.py answer uses'
      },
      index: {
        collection: 'test_collection',
        count: 346,
        tokenizer_match: true
      },
      keys: {
        NVIDIA_API_KEY: 'set',
        XKIRO_API_KEY: 'set'
      },
      ingest: {
        state: 'idle',
        stored: 346,
        error: null
      }
    });

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('RAGLab');
    expect(compiled.textContent).toContain('v1.2.3');
    expect(compiled.textContent).toContain('346 fragments');
    expect(compiled.textContent).toContain('2/2');
    expect(compiled.textContent).toContain('Supporté');
  });

  it('shows sealed banner when ingest is running', () => {
    raglab.health.set({
      status: 'ok',
      ingest: {
        state: 'running',
        started_at: '2026-09-16T12:00:00Z'
      }
    });

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Indexation en cours');
    expect(compiled.textContent).toContain('Le corpus est scellé');
  });
});
