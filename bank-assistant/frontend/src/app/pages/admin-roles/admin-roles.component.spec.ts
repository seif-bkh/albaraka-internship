import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AccessControlService, AdminRole, PermissionDefinition } from '../../services/access-control.service';
import { AdminRolesComponent } from './admin-roles.component';

const CATALOG: PermissionDefinition[] = [
  { code: 'DOCUMENTS_READ', area: 'DOCUMENTS', name: 'Consulter', description: 'Lire', sortOrder: 10 },
  { code: 'DOCUMENTS_CREATE', area: 'DOCUMENTS', name: 'Créer', description: 'Ajouter', sortOrder: 20 },
  { code: 'DOCUMENTS_UPDATE', area: 'DOCUMENTS', name: 'Modifier', description: 'Modifier', sortOrder: 30 },
  { code: 'DOCUMENTS_DELETE', area: 'DOCUMENTS', name: 'Supprimer', description: 'Supprimer', sortOrder: 40 },
  { code: 'DOCUMENTS_PROCESS', area: 'DOCUMENTS', name: 'Traiter', description: 'Réindexer', sortOrder: 50 },
  { code: 'DOCUMENTS_INGESTION_READ', area: 'DOCUMENTS', name: 'Journal', description: 'Historique', sortOrder: 60 },
  { code: 'RAGLAB_CONSOLE_VIEW', area: 'RAGLAB', name: 'Voir', description: 'Lire', sortOrder: 70 },
  { code: 'RAGLAB_CONSOLE_RUN', area: 'RAGLAB', name: 'Exécuter', description: 'Lancer', sortOrder: 80 },
  { code: 'RAGLAB_CONSOLE_MANAGE', area: 'RAGLAB', name: 'Gérer', description: 'Tout gérer', sortOrder: 90 },
];

function role(overrides: Partial<AdminRole> = {}): AdminRole {
  return {
    id: 1,
    code: 'SUPER_ADMIN',
    name: 'Super Administrateur',
    description: null,
    systemRole: true,
    protectedRole: true,
    active: true,
    assignedUsers: 1,
    permissions: [],
    effectivePermissions: [],
    createdAt: null,
    updatedAt: null,
    ...overrides,
  };
}

describe('AdminRolesComponent', () => {
  let component: AdminRolesComponent;
  let fixture: ComponentFixture<AdminRolesComponent>;

  beforeEach(() => {
    const access = {
      getPermissions: () => of(CATALOG),
      getRoles: () => of([role()]),
      getAudit: () => of([]),
      createRole: () => of(role()),
      updateRole: () => of(role()),
      setRoleActive: () => of(role()),
    };
    TestBed.configureTestingModule({
      imports: [AdminRolesComponent],
      providers: [{ provide: AccessControlService, useValue: access }],
    });
    fixture = TestBed.createComponent(AdminRolesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('automatically adds document read when processing is granted', () => {
    component.beginCreate();
    component.togglePermission('DOCUMENTS_PROCESS', true);

    expect(component.form.permissions).toEqual(['DOCUMENTS_READ', 'DOCUMENTS_PROCESS']);
  });

  it('removes dependent document mutations if document read is removed', () => {
    component.beginCreate();
    component.form.permissions = ['DOCUMENTS_READ', 'DOCUMENTS_CREATE', 'DOCUMENTS_DELETE', 'DOCUMENTS_PROCESS'];
    component.togglePermission('DOCUMENTS_READ', false);

    expect(component.form.permissions).toEqual([]);
  });

  it('makes RAGLab Manage imply View and Run without persisting duplicate grants', () => {
    component.beginCreate();
    component.togglePermission('RAGLAB_CONSOLE_RUN', true);
    expect(component.form.permissions).toEqual(['RAGLAB_CONSOLE_VIEW', 'RAGLAB_CONSOLE_RUN']);

    component.togglePermission('RAGLAB_CONSOLE_MANAGE', true);

    expect(component.form.permissions).toEqual(['RAGLAB_CONSOLE_MANAGE']);
    expect(component.isPermissionGranted('RAGLAB_CONSOLE_VIEW')).toBe(true);
    expect(component.isPermissionGranted('RAGLAB_CONSOLE_RUN')).toBe(true);
    expect(component.isPermissionDisabled('RAGLAB_CONSOLE_VIEW')).toBe(true);
    expect(component.isPermissionDisabled('RAGLAB_CONSOLE_RUN')).toBe(true);
  });

  it('does not allow adding a permission key outside the loaded catalogue', () => {
    component.beginCreate();
    component.togglePermission('TRANSFER_APPROVE', true);

    expect(component.form.permissions).toEqual([]);
  });

  it('keeps the protected Super Admin definition read-only', () => {
    component.useRole(role());
    component.togglePermission('DOCUMENTS_READ', false);

    expect(component.isProtected).toBe(true);
    expect(component.form.permissions).toEqual([]);
  });
});
