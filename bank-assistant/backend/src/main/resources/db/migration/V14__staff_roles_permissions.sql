-- Dynamic staff roles with a closed, code-defined permission catalogue.
-- Existing role codes and user rows are preserved; only their permissions are seeded.

CREATE TABLE admin_roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    is_staff BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    area VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(600) NOT NULL,
    sort_order INTEGER NOT NULL
);

CREATE TABLE admin_role_permissions (
    role_id BIGINT NOT NULL REFERENCES admin_roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES admin_permissions(id) ON DELETE RESTRICT,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE admin_access_audit (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(48) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    target_role_code VARCHAR(64),
    target_user_id BIGINT,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_roles_staff_active ON admin_roles (is_staff, active);
CREATE INDEX idx_admin_role_permissions_permission ON admin_role_permissions (permission_id);
CREATE INDEX idx_admin_access_audit_created_at ON admin_access_audit (created_at DESC);
CREATE INDEX idx_admin_access_audit_actor ON admin_access_audit (actor);

INSERT INTO admin_permissions (code, area, name, description, sort_order) VALUES
    ('DOCUMENTS_READ', 'DOCUMENTS', 'Consulter les documents', 'Afficher la liste des documents et leurs métadonnées.', 10),
    ('DOCUMENTS_CREATE', 'DOCUMENTS', 'Ajouter des documents', 'Téléverser de nouveaux documents dans le référentiel documentaire de l’assistant.', 20),
    ('DOCUMENTS_UPDATE', 'DOCUMENTS', 'Modifier les documents', 'Modifier les métadonnées et le statut des documents. Une réindexation requiert aussi le droit de traitement.', 30),
    ('DOCUMENTS_DELETE', 'DOCUMENTS', 'Supprimer les documents', 'Supprimer un document et son fichier stocké.', 40),
    ('DOCUMENTS_PROCESS', 'DOCUMENTS', 'Traiter et réindexer', 'Relancer les traitements du référentiel documentaire, reconstruire ses index ou invalider le cache documentaire.', 50),
    ('DOCUMENTS_INGESTION_READ', 'DOCUMENTS', 'Consulter l’historique d’ingestion', 'Consulter les journaux et l’état des traitements sans pouvoir les relancer.', 60),
    ('RAGLAB_CONSOLE_VIEW', 'RAGLAB', 'Voir la console RAGLab', 'Consulter le statut, les profils et les ressources RAGLab sans lancer de requête ni modifier la configuration.', 70),
    ('RAGLAB_CONSOLE_RUN', 'RAGLAB', 'Exécuter des requêtes RAGLab', 'Lancer des requêtes de recherche/réponse et des opérations de diagnostic non configurantes.', 80),
    ('RAGLAB_CONSOLE_MANAGE', 'RAGLAB', 'Gérer entièrement la console RAGLab', 'Accès complet : inclut Voir et Exécuter, et permet les clés, réglages, moteurs, fournisseurs, évaluations, index et commandes de la console.', 90)
ON CONFLICT (code) DO NOTHING;

INSERT INTO admin_roles (code, name, description, is_system, is_staff, active) VALUES
    ('SUPER_ADMIN', 'Super Administrateur', 'Rôle système protégé : administration des utilisateurs, des rôles et accès complet aux espaces couverts.', TRUE, TRUE, TRUE),
    ('ADMIN_DOCUMENTS', 'Gestionnaire documentaire', 'Rôle historique — droits Documents/RAGLab équivalents à ceux d’avant la migration.', TRUE, TRUE, TRUE),
    ('ADMIN_SUPERVISION', 'Supervision', 'Rôle historique — consultation et exécution RAGLab, avec accès à l’historique d’ingestion.', TRUE, TRUE, TRUE),
    ('VIEWER', 'Lecteur', 'Rôle historique en lecture seule sur les ressources documentaires et RAGLab.', TRUE, TRUE, TRUE),
    ('UTILISATEUR', 'Client', 'Rôle réservé au portail client ; il ne s’agit pas d’un rôle du personnel.', TRUE, FALSE, TRUE)
ON CONFLICT (code) DO NOTHING;

-- Preserve the effective scoped access of the existing fixed staff roles.
INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.code IN (
    'DOCUMENTS_READ', 'DOCUMENTS_CREATE', 'DOCUMENTS_UPDATE', 'DOCUMENTS_DELETE',
    'DOCUMENTS_PROCESS', 'DOCUMENTS_INGESTION_READ', 'RAGLAB_CONSOLE_VIEW',
    'RAGLAB_CONSOLE_RUN', 'RAGLAB_CONSOLE_MANAGE'
)
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.code IN (
    'DOCUMENTS_READ', 'DOCUMENTS_CREATE', 'DOCUMENTS_UPDATE', 'DOCUMENTS_DELETE',
    'DOCUMENTS_PROCESS', 'DOCUMENTS_INGESTION_READ', 'RAGLAB_CONSOLE_VIEW',
    'RAGLAB_CONSOLE_RUN'
)
WHERE r.code = 'ADMIN_DOCUMENTS'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.code IN (
    'DOCUMENTS_INGESTION_READ', 'RAGLAB_CONSOLE_VIEW', 'RAGLAB_CONSOLE_RUN'
)
WHERE r.code = 'ADMIN_SUPERVISION'
ON CONFLICT DO NOTHING;

INSERT INTO admin_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
JOIN admin_permissions p ON p.code IN (
    'DOCUMENTS_READ', 'DOCUMENTS_INGESTION_READ', 'RAGLAB_CONSOLE_VIEW'
)
WHERE r.code = 'VIEWER'
ON CONFLICT DO NOTHING;

-- The customers table keeps its enum/check constraint. Only staff users move from
-- the old closed enum check to a foreign key referencing the role catalogue.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(64);
ALTER TABLE users
    ADD CONSTRAINT fk_users_admin_role_code
    FOREIGN KEY (role) REFERENCES admin_roles(code) ON UPDATE CASCADE ON DELETE RESTRICT;
