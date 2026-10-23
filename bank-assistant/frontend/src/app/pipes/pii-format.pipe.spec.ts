import { TestBed } from '@angular/core/testing';
import { DomSanitizer } from '@angular/platform-browser';
import { PiiFormatPipe } from './pii-format.pipe';
import { describe, it, expect, beforeEach } from 'vitest';

describe('PiiFormatPipe', () => {
  let pipe: PiiFormatPipe;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    const sanitizer = TestBed.inject(DomSanitizer);
    pipe = new PiiFormatPipe(sanitizer);
  });

  it('replaces [EMAIL], [PHONE], [RIB], [CIN] tokens with styled chips', () => {
    const raw = 'Client avec [EMAIL], tél [PHONE], rib [RIB], et identifiant [CIN].';
    const result = pipe.transform(raw) as any;
    const str = result.changingThisBreaksApplicationSecurity;

    expect(str).toContain('pii-chip pii-email');
    expect(str).toContain('pii-chip pii-phone');
    expect(str).toContain('pii-chip pii-rib');
    expect(str).toContain('pii-chip pii-cin');
  });

  it('replaces [S1], [S2] citation references with interactive badges', () => {
    const raw = 'La marge est fixée à l\'avance [S1] et approuvée [S3].';
    const result = pipe.transform(raw) as any;
    const str = result.changingThisBreaksApplicationSecurity;

    expect(str).toContain('citation-badge');
    expect(str).toContain('data-source="S1"');
    expect(str).toContain('data-source="S3"');
    expect(str).not.toContain('&lt;span class=');
  });

  it('handles empty input gracefully', () => {
    expect(pipe.transform('')).toBe('');
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });

  it('escapes html entities before replacing tokens', () => {
    const raw = '<script>alert(1)</script> [EMAIL]';
    const result = pipe.transform(raw) as any;
    const str = result.changingThisBreaksApplicationSecurity;

    expect(str).not.toContain('<script>');
    expect(str).toContain('&lt;script&gt;');
    expect(str).toContain('pii-chip pii-email');
  });
});
