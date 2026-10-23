import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AccessAuditEntry,
  AccessControlService,
  AdminRole,
  PermissionDefinition,
  RolePayload
} from '../../services/access-control.service';

@Component({
  selector: 'app-admin-roles',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-roles.component.html',
  styleUrl: './admin-roles.component.css'
})
export class AdminRolesComponent implements OnInit {
  permissions: PermissionDefinition[] = [];
  roles: AdminRole[] = [];
  audit: AccessAuditEntry[] = [];
  selectedRole: AdminRole | null = null;
  isCreating = false;
  loading = true;
  saving = false;
  errorMessage = '';
  successMessage = '';
  dependencyMessage = '';

  form: RolePayload = { name: '', description: '', permissions: [] };

  constructor(private access: AccessControlService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void { this.loadData(); }

  get documentsPermissions(): PermissionDefinition[] {
    return this.permissions.filter(permission => permission.area === 'DOCUMENTS');
  }

  get raglabPermissions(): PermissionDefinition[] {
    return this.permissions.filter(permission => permission.area === 'RAGLAB');
  }

  get permissionCount(): number { return this.form.permissions.length; }
  get isProtected(): boolean { return !!this.selectedRole?.protectedRole; }

  loadData(): void {
    this.loading = true;
    this.errorMessage = '';
    this.access.getPermissions().subscribe({
      next: permissions => { this.permissions = permissions; this.cdr.detectChanges(); },
      error: err => { this.errorMessage = this.messageFrom(err, 'Le catalogue de permissions est indisponible.'); this.cdr.detectChanges(); }
    });
    this.access.getRoles().subscribe({
      next: roles => {
        this.roles = roles;
        this.loading = false;
        if (this.selectedRole) {
          this.selectedRole = roles.find(role => role.id === this.selectedRole?.id) || null;
          if (this.selectedRole) this.useRole(this.selectedRole);
        }
        if (!this.selectedRole && !this.isCreating && roles.length) this.useRole(roles[0]);
        this.cdr.detectChanges();
      },
      error: err => { this.loading = false; this.errorMessage = this.messageFrom(err, 'Impossible de charger les rôles.'); this.cdr.detectChanges(); }
    });
    this.loadAudit();
  }

  loadAudit(): void {
    this.access.getAudit().subscribe({
      next: entries => { this.audit = entries; this.cdr.detectChanges(); },
      error: () => { this.audit = []; this.cdr.detectChanges(); }
    });
  }

  useRole(role: AdminRole): void {
    this.isCreating = false;
    this.selectedRole = role;
    this.errorMessage = '';
    this.successMessage = '';
    this.dependencyMessage = '';
    this.form = {
      name: role.name,
      description: role.description || '',
      permissions: [...role.permissions]
    };
  }

  beginCreate(): void {
    this.selectedRole = null;
    this.isCreating = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.dependencyMessage = '';
    this.form = { name: '', description: '', permissions: [] };
  }

  isPermissionGranted(code: string): boolean {
    if (this.form.permissions.includes(code)) return true;
    return this.form.permissions.includes('RAGLAB_CONSOLE_MANAGE')
      && (code === 'RAGLAB_CONSOLE_VIEW' || code === 'RAGLAB_CONSOLE_RUN');
  }

  isPermissionDisabled(code: string): boolean {
    if (this.isProtected) return true;
    return this.form.permissions.includes('RAGLAB_CONSOLE_MANAGE')
      && (code === 'RAGLAB_CONSOLE_VIEW' || code === 'RAGLAB_CONSOLE_RUN');
  }

  togglePermission(code: string, checked: boolean): void {
    if (this.isProtected || this.isPermissionDisabled(code)) return;
    const selected = new Set(this.form.permissions);
    this.dependencyMessage = '';

    if (checked) selected.add(code);
    else selected.delete(code);

    if (code === 'RAGLAB_CONSOLE_MANAGE' && checked) {
      // Manage contains View and Run; don't store redundant direct grants.
      selected.delete('RAGLAB_CONSOLE_VIEW');
      selected.delete('RAGLAB_CONSOLE_RUN');
    }
    if (code === 'RAGLAB_CONSOLE_VIEW' && !checked && selected.has('RAGLAB_CONSOLE_RUN')) {
      selected.delete('RAGLAB_CONSOLE_RUN');
      this.dependencyMessage = 'Le droit Exécuter a aussi été retiré : il requiert Voir.';
    }
    if (code === 'RAGLAB_CONSOLE_RUN' && checked && !selected.has('RAGLAB_CONSOLE_VIEW')) {
      selected.add('RAGLAB_CONSOLE_VIEW');
      this.dependencyMessage = 'Voir a été ajouté automatiquement, car Exécuter le requiert.';
    }

    const documentMutations = [
      'DOCUMENTS_CREATE', 'DOCUMENTS_UPDATE', 'DOCUMENTS_DELETE', 'DOCUMENTS_PROCESS'
    ];
    if (documentMutations.includes(code) && checked && !selected.has('DOCUMENTS_READ')) {
      selected.add('DOCUMENTS_READ');
      this.dependencyMessage = 'Consulter les documents a été ajouté : les actions de gestion requièrent la lecture.';
    }
    if (code === 'DOCUMENTS_READ' && !checked) {
      let removed = false;
      for (const permission of documentMutations) {
        if (selected.delete(permission)) removed = true;
      }
      if (removed) this.dependencyMessage = 'Les actions de gestion ont été retirées, car elles requièrent la lecture.';
    }

    this.form.permissions = this.permissions
      .filter(permission => selected.has(permission.code))
      .map(permission => permission.code);
  }

  saveRole(): void {
    this.errorMessage = '';
    this.successMessage = '';
    const payload: RolePayload = {
      name: this.form.name.trim(),
      description: this.form.description.trim(),
      permissions: [...this.form.permissions]
    };
    if (!payload.name) {
      this.errorMessage = 'Le nom du rôle est obligatoire.';
      return;
    }
    if (!payload.permissions.length) {
      this.errorMessage = 'Sélectionnez au moins une permission.';
      return;
    }

    this.saving = true;
    const request = this.isCreating || !this.selectedRole
      ? this.access.createRole(payload)
      : this.access.updateRole(this.selectedRole.id, payload);
    request.subscribe({
      next: saved => {
        this.saving = false;
        this.selectedRole = saved;
        this.isCreating = false;
        this.successMessage = 'Rôle enregistré. Les changements d’accès sont actifs immédiatement.';
        this.form = { name: saved.name, description: saved.description || '', permissions: [...saved.permissions] };
        this.access.getRoles().subscribe({
          next: roles => { this.roles = roles; this.selectedRole = roles.find(role => role.id === saved.id) || saved; this.cdr.detectChanges(); },
          error: () => this.cdr.detectChanges()
        });
        this.loadAudit();
        this.cdr.detectChanges();
      },
      error: err => {
        this.saving = false;
        this.errorMessage = this.messageFrom(err, 'Enregistrement impossible.');
        this.cdr.detectChanges();
      }
    });
  }

  toggleActive(): void {
    if (!this.selectedRole || this.selectedRole.protectedRole || this.selectedRole.systemRole) return;
    const nextActive = !this.selectedRole.active;
    const prompt = nextActive
      ? `Réactiver le rôle « ${this.selectedRole.name} » ?`
      : `Désactiver « ${this.selectedRole.name} » ? Les comptes actifs doivent d’abord être réaffectés.`;
    if (!confirm(prompt)) return;

    this.access.setRoleActive(this.selectedRole.id, nextActive).subscribe({
      next: saved => {
        this.selectedRole = saved;
        this.roles = this.roles.map(role => role.id === saved.id ? saved : role);
        this.successMessage = nextActive ? 'Rôle réactivé.' : 'Rôle désactivé.';
        this.loadAudit();
        this.cdr.detectChanges();
      },
      error: err => { this.errorMessage = this.messageFrom(err, 'Changement de statut impossible.'); this.cdr.detectChanges(); }
    });
  }

  private messageFrom(error: any, fallback: string): string {
    const body = error?.error;
    return (typeof body === 'string' && body)
      || body?.detail || body?.message || body?.error || fallback;
  }
}
