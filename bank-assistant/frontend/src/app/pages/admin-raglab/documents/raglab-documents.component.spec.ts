import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../../services/auth.service';
import { RaglabService } from '../../../services/raglab.service';
import { RaglabDocumentsComponent } from './raglab-documents.component';

describe('RaglabDocumentsComponent access controls', () => {
  let roles: Set<string>;
  let permissions: Set<string>;

  beforeEach(() => {
    roles = new Set<string>();
    permissions = new Set<string>();
    TestBed.configureTestingModule({
      imports: [RaglabDocumentsComponent],
      providers: [
        { provide: AuthService, useValue: {
          hasRole: (role: string) => roles.has(role),
          hasPermission: (permission: string) => permissions.has(permission),
        } },
        { provide: RaglabService, useValue: { health: signal(null) } },
      ],
    });
  });

  function component(): RaglabDocumentsComponent {
    return TestBed.createComponent(RaglabDocumentsComponent).componentInstance;
  }

  it('does not treat standalone Documents grants as access to the RAGLab feed', () => {
    permissions = new Set(['DOCUMENTS_CREATE', 'DOCUMENTS_DELETE', 'DOCUMENTS_PROCESS', 'RAGLAB_CONSOLE_VIEW']);

    const page = component();

    expect(page.canCreateDocuments).toBe(false);
    expect(page.canDeleteDocuments).toBe(false);
    expect(page.canProcessDocuments).toBe(false);
  });

  it('gives RAGLab Manage every feed and indexing action', () => {
    permissions = new Set(['RAGLAB_CONSOLE_MANAGE']);

    const page = component();

    expect(page.canCreateDocuments).toBe(true);
    expect(page.canDeleteDocuments).toBe(true);
    expect(page.canProcessDocuments).toBe(true);
  });

  it('preserves the seeded ADMIN_DOCUMENTS feed access only while its matching grants remain', () => {
    roles = new Set(['ADMIN_DOCUMENTS']);
    permissions = new Set(['DOCUMENTS_CREATE', 'DOCUMENTS_DELETE', 'DOCUMENTS_PROCESS', 'RAGLAB_CONSOLE_VIEW']);

    const page = component();

    expect(page.canCreateDocuments).toBe(true);
    expect(page.canDeleteDocuments).toBe(true);
    expect(page.canProcessDocuments).toBe(true);
  });

  it('does not let the legacy role bypass a removed process grant', () => {
    roles = new Set(['ADMIN_DOCUMENTS']);
    permissions = new Set(['DOCUMENTS_CREATE', 'DOCUMENTS_DELETE', 'RAGLAB_CONSOLE_VIEW']);

    const page = component();

    expect(page.canCreateDocuments).toBe(true);
    expect(page.canDeleteDocuments).toBe(true);
    expect(page.canProcessDocuments).toBe(false);
  });
});
