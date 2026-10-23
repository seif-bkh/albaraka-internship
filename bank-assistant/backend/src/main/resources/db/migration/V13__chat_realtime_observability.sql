-- Real-time observability: model, tokens in/out, latency, asked_at, answered_at, user_id, status
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS user_id bigint;
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS model varchar(64);
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS tokens_in integer;
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS tokens_out integer;
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS latency_ms integer;
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS asked_at timestamp without time zone;
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS answered_at timestamp without time zone;
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS status varchar(20) DEFAULT 'ANSWERED';

-- Backfill asked_at from date_creation where missing
UPDATE public.messages SET asked_at = date_creation, answered_at = date_creation WHERE asked_at IS NULL;

CREATE INDEX IF NOT EXISTS messages_asked_at_idx ON public.messages (asked_at DESC);
CREATE INDEX IF NOT EXISTS messages_status_idx ON public.messages (status);
