-- Ingestion error messages (provider stack traces, quota messages) do not fit in
-- varchar(255); the result reporter used to 500 and the document stayed EN_COURS.
ALTER TABLE public.documents ALTER COLUMN message_erreur_indexation TYPE text;
