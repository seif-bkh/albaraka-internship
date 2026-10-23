-- Phase 3.6 — server-side refresh-token revocation.
--
-- Each refresh JWT carries a `jti` that must exist here, unrevoked and unexpired,
-- to be honoured. /refresh ROTATES: the presented token is revoked and a new one
-- issued in the same family. Presenting an already-revoked token (theft or
-- replay) revokes the entire family. /logout revokes the presented token;
-- admins can revoke every token of a user (password reset, offboarding).

CREATE TABLE public.refresh_tokens (
    jti          varchar(64)  PRIMARY KEY,
    family       varchar(64)  NOT NULL,
    subject      varchar(255) NOT NULL,
    issued_at    timestamp    NOT NULL DEFAULT now(),
    expires_at   timestamp    NOT NULL,
    revoked_at   timestamp,
    revoke_reason varchar(64)
);

CREATE INDEX idx_refresh_tokens_subject ON public.refresh_tokens (subject);
CREATE INDEX idx_refresh_tokens_family  ON public.refresh_tokens (family);
