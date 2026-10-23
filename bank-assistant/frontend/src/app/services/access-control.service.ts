import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface PermissionDefinition {
  code: string;
  area: 'DOCUMENTS' | 'RAGLAB' | string;
  name: string;
  description: string;
  sortOrder: number;
}

export interface AdminRole {
  id: number;
  code: string;
  name: string;
  description: string | null;
  systemRole: boolean;
  protectedRole: boolean;
  active: boolean;
  assignedUsers: number;
  /** Direct grants saved for this role. */
  permissions: string[];
  /** Effective grants, including RAGLab Manage => View + Run. */
  effectivePermissions: string[];
  createdAt: string | null;
  updatedAt: string | null;
}

export interface RolePayload {
  name: string;
  description: string;
  permissions: string[];
}

export interface AccessAuditEntry {
  id: number;
  action: string;
  actor: string;
  targetRoleCode: string | null;
  targetUserId: number | null;
  details: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class AccessControlService {
  private readonly apiUrl = `${environment.apiUrl}/admin/access-control`;

  constructor(private http: HttpClient) {}

  getPermissions(): Observable<PermissionDefinition[]> {
    return this.http.get<PermissionDefinition[]>(`${this.apiUrl}/permissions`);
  }

  getRoles(): Observable<AdminRole[]> {
    return this.http.get<AdminRole[]>(`${this.apiUrl}/roles`);
  }

  createRole(payload: RolePayload): Observable<AdminRole> {
    return this.http.post<AdminRole>(`${this.apiUrl}/roles`, payload);
  }

  updateRole(id: number, payload: RolePayload): Observable<AdminRole> {
    return this.http.put<AdminRole>(`${this.apiUrl}/roles/${id}`, payload);
  }

  setRoleActive(id: number, active: boolean): Observable<AdminRole> {
    return this.http.patch<AdminRole>(`${this.apiUrl}/roles/${id}/active`, { active });
  }

  getAudit(): Observable<AccessAuditEntry[]> {
    return this.http.get<AccessAuditEntry[]>(`${this.apiUrl}/audit`);
  }
}
