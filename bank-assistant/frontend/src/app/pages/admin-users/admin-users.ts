import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccessControlService, AdminRole } from '../../services/access-control.service';
import { CreateStaffUser, UpdateStaffUser, User, UserService } from '../../services/user.service';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin-users.html',
  styleUrl: './admin-users.css',
})
export class AdminUsers implements OnInit {
  users: User[] = [];
  roles: AdminRole[] = [];
  loading = false;
  errorMsg = '';

  showModal = false;
  editingUser: User | null = null;
  formData: CreateStaffUser & { actif: boolean } = {
    email: '', motDePasse: '', nom: '', prenom: '', role: 'VIEWER', actif: true
  };
  modalErrorMsg = '';
  saving = false;

  constructor(
    private userService: UserService,
    private accessControl: AccessControlService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadRoles();
    this.loadUsers();
  }

  get selectableRoles(): AdminRole[] {
    return this.roles.filter(role => role.active || role.code === this.formData.role);
  }

  loadRoles(): void {
    this.accessControl.getRoles().subscribe({
      next: roles => { this.roles = roles; this.cdr.detectChanges(); },
      error: () => { this.errorMsg = 'Impossible de charger les rôles actifs.'; this.cdr.detectChanges(); }
    });
  }

  loadUsers(): void {
    this.loading = true;
    this.errorMsg = '';
    this.userService.getUsers().subscribe({
      next: data => { this.users = data; this.loading = false; this.cdr.detectChanges(); },
      error: err => {
        this.loading = false;
        this.errorMsg = this.messageFrom(err, 'Impossible de charger les utilisateurs.');
        this.cdr.detectChanges();
      }
    });
  }

  openModal(user?: User): void {
    this.modalErrorMsg = '';
    if (user) {
      this.editingUser = user;
      this.formData = {
        email: user.email,
        motDePasse: '',
        nom: user.nom || '',
        prenom: user.prenom || '',
        role: user.role,
        actif: user.actif
      };
    } else {
      this.editingUser = null;
      this.formData = {
        email: '', motDePasse: '', nom: '', prenom: '',
        role: this.roles.find(role => role.active && !role.protectedRole)?.code
          || this.roles.find(role => role.active)?.code || '',
        actif: true
      };
    }
    this.showModal = true;
    this.cdr.detectChanges();
  }

  closeModal(): void {
    if (this.saving) return;
    this.showModal = false;
    this.cdr.detectChanges();
  }

  saveUser(): void {
    this.modalErrorMsg = '';
    if (!this.formData.role) {
      this.modalErrorMsg = 'Sélectionnez un rôle actif.';
      return;
    }
    this.saving = true;
    const password = this.formData.motDePasse?.trim() || undefined;
    if (this.editingUser?.id) {
      const payload: UpdateStaffUser = {
        nom: this.formData.nom?.trim(),
        prenom: this.formData.prenom?.trim(),
        role: this.formData.role,
        actif: this.formData.actif,
        motDePasse: password
      };
      this.userService.updateUser(this.editingUser.id, payload).subscribe({
        next: () => { this.saving = false; this.showModal = false; this.loadUsers(); },
        error: err => { this.saving = false; this.modalErrorMsg = this.messageFrom(err, 'Erreur lors de la modification.'); this.cdr.detectChanges(); }
      });
    } else {
      const payload: CreateStaffUser = {
        email: this.formData.email?.trim(),
        motDePasse: password || '',
        nom: this.formData.nom?.trim(),
        prenom: this.formData.prenom?.trim(),
        role: this.formData.role
      };
      this.userService.createUser(payload).subscribe({
        next: () => { this.saving = false; this.showModal = false; this.loadUsers(); },
        error: err => { this.saving = false; this.modalErrorMsg = this.messageFrom(err, 'Erreur lors de la création.'); this.cdr.detectChanges(); }
      });
    }
  }

  deleteUser(id: number): void {
    if (!confirm('Supprimer définitivement ce compte du personnel ?')) return;
    this.userService.deleteUser(id).subscribe({
      next: () => this.loadUsers(),
      error: err => { this.errorMsg = this.messageFrom(err, 'Suppression impossible.'); this.cdr.detectChanges(); }
    });
  }

  toggleActif(user: User): void {
    if (!user.id) return;
    const payload: UpdateStaffUser = {
      nom: user.nom || '', prenom: user.prenom || '', role: user.role, actif: !user.actif
    };
    this.userService.updateUser(user.id, payload).subscribe({
      next: () => this.loadUsers(),
      error: err => { this.errorMsg = this.messageFrom(err, 'Modification impossible.'); this.cdr.detectChanges(); }
    });
  }

  roleLabel(user: User): string {
    return user.roleName || this.roles.find(role => role.code === user.role)?.name || user.role || '—';
  }

  isRoleInactive(code: string): boolean {
    return this.roles.some(role => role.code === code && !role.active);
  }

  private messageFrom(error: any, fallback: string): string {
    const body = error?.error;
    return (typeof body === 'string' && body)
      || body?.detail || body?.message || body?.error || fallback;
  }
}
