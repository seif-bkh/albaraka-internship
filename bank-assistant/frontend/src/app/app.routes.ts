import { Routes } from '@angular/router';
import { AdminLayoutComponent } from './components/admin-layout/admin-layout';
import { AdminDashboard } from './pages/admin-dashboard/admin-dashboard';
import { AdminStats } from './pages/admin-stats/admin-stats';
import { AdminUsers } from './pages/admin-users/admin-users';
import { AdminHistorique } from './pages/admin-historique/admin-historique';
import { AdminFacts } from './pages/admin-facts/admin-facts';
import { AdminTaxonomy } from './pages/admin-taxonomy/admin-taxonomy';
import { AdminMonitoring } from './pages/admin-monitoring/admin-monitoring';
import { LoginComponent } from './pages/login/login';
import { AuthGuard } from './guards/auth.guard';
import { RoleGuard } from './guards/role.guard';
import { StaffGuard } from './guards/staff.guard';
import { ClientGuard } from './guards/client.guard';
import { PermissionGuard } from './guards/permission.guard';
import { RaglabLandingGuard } from './guards/raglab-landing.guard';
import { AdminHomeGuard } from './guards/admin-home.guard';

const legacyStaffRoles = ['SUPER_ADMIN', 'ADMIN_DOCUMENTS', 'ADMIN_SUPERVISION', 'VIEWER'];

export const routes: Routes = [
  // Public
  { path: '', loadComponent: () => import('./pages/landing/landing').then(m => m.LandingComponent) },
  { path: 'login', component: LoginComponent },
  { path: 'register', loadComponent: () => import('./pages/register/register').then(m => m.RegisterComponent) },
  { path: 'chat', loadComponent: () => import('./pages/public-chat/public-chat.component').then(m => m.PublicChatComponent) },

  // Portail client (bank clients — role UTILISATEUR)
  {
    path: 'portal',
    loadComponent: () => import('./components/portal-layout/portal-layout').then(m => m.PortalLayoutComponent),
    canActivate: [AuthGuard, ClientGuard],
    children: [
      { path: '', loadComponent: () => import('./pages/portal/portal-home/portal-home').then(m => m.PortalHomeComponent) },
      { path: 'assistant', loadComponent: () => import('./pages/portal/portal-assistant/portal-assistant').then(m => m.PortalAssistantComponent) },
      { path: 'profil', loadComponent: () => import('./pages/portal/portal-profile/portal-profile').then(m => m.PortalProfileComponent) },
    ]
  },

  // Back-office banque (staff)
  {
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [AuthGuard, StaffGuard],
    children: [
      { path: '', pathMatch: 'full', canActivate: [AdminHomeGuard], children: [] },
      { path: 'dashboard', component: AdminDashboard, canActivate: [RoleGuard], data: { expectedRoles: legacyStaffRoles } },
      { path: 'facts', component: AdminFacts, canActivate: [RoleGuard], data: { expectedRoles: legacyStaffRoles } },
      { path: 'news', loadComponent: () => import('./pages/admin-news/admin-news').then(m => m.AdminNews), canActivate: [RoleGuard], data: { expectedRoles: legacyStaffRoles } },
      { path: 'taxonomy', component: AdminTaxonomy, canActivate: [RoleGuard], data: { expectedRoles: legacyStaffRoles } },
      { path: 'stats', component: AdminStats, canActivate: [RoleGuard], data: { expectedRoles: legacyStaffRoles } },
      { path: 'monitoring', component: AdminMonitoring, canActivate: [RoleGuard], data: { expectedRoles: legacyStaffRoles } },
      {
        // RAGLab and its Assistant IA control plane use the same View/Run/Manage catalogue.
        path: 'agent',
        canActivate: [PermissionGuard],
        data: { permissions: ['RAGLAB_CONSOLE_VIEW'] },
        children: [
          // RAGLab is the only engine, so the section opens on its connection.
          { path: '', redirectTo: 'health', pathMatch: 'full' },
          {
            path: 'health',
            loadComponent: () => import('./pages/admin-agent-health/admin-agent-health').then(m => m.AdminAgentHealth)
          },
          {
            path: 'journal',
            loadComponent: () => import('./pages/admin-agent-journal/admin-agent-journal').then(m => m.AdminAgentJournalComponent)
          },
          {
            path: 'quality',
            loadComponent: () => import('./pages/admin-agent-quality/admin-agent-quality').then(m => m.AdminAgentQuality),
            canActivate: [PermissionGuard],
            data: { permissions: ['RAGLAB_CONSOLE_RUN'] }
          }
        ]
      },
      {
        path: 'raglab',
        loadComponent: () => import('./pages/admin-raglab/admin-raglab.component').then(m => m.AdminRaglabComponent),
        canActivate: [PermissionGuard],
        data: { permissions: ['RAGLAB_CONSOLE_VIEW'] },
        children: [
          // The landing screen depends on the permission set: a viewer who may not run
          // queries must land on the corpus, not be refused by the "ask" guard.
          { path: '', pathMatch: 'full', canActivate: [RaglabLandingGuard] },
          {
            path: 'ask',
            loadComponent: () => import('./pages/admin-raglab/ask/raglab-ask.component').then(m => m.RaglabAskComponent),
            canActivate: [PermissionGuard],
            data: { permissions: ['RAGLAB_CONSOLE_RUN'] }
          },
          {
            path: 'documents',
            loadComponent: () => import('./pages/admin-raglab/documents/raglab-documents.component').then(m => m.RaglabDocumentsComponent)
          },
          {
            path: 'retrieval',
            loadComponent: () => import('./pages/admin-raglab/retrieval/raglab-retrieval.component').then(m => m.RaglabRetrievalComponent)
          },
          {
            path: 'settings',
            loadComponent: () => import('./pages/admin-raglab/settings/raglab-settings.component').then(m => m.RaglabSettingsComponent),
            // Hiding the tab is presentation; the route has to enforce it too.
            canActivate: [PermissionGuard],
            data: { permissions: ['RAGLAB_CONSOLE_MANAGE'] }
          },
          {
            path: 'evaluations',
            loadComponent: () => import('./pages/admin-raglab/evaluations/raglab-evaluations.component').then(m => m.RaglabEvaluationsComponent),
            canActivate: [PermissionGuard],
            data: { permissions: ['RAGLAB_CONSOLE_MANAGE'] }
          }
        ]
      },
      {
        path: 'users',
        component: AdminUsers,
        canActivate: [RoleGuard],
        data: { expectedRole: 'SUPER_ADMIN' }
      },
      {
        path: 'roles',
        loadComponent: () => import('./pages/admin-roles/admin-roles.component').then(m => m.AdminRolesComponent),
        canActivate: [RoleGuard],
        data: { expectedRole: 'SUPER_ADMIN' }
      },
      {
        path: 'historique',
        component: AdminHistorique,
        canActivate: [RoleGuard],
        data: { expectedRole: 'SUPER_ADMIN' }
      }
    ]
  },

  { path: '**', redirectTo: '' }
];
