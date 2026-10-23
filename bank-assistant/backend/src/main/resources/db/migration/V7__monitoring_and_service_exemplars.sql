-- Monitoring: keep, per exchange, the grounding outcome, domain, language and
-- the agent's pipeline trace (JSON text) so an operator can see *why* an
-- answer came out the way it did (/monitoring in the admin UI).
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS grounded   boolean;
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS domaine    varchar(40);
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS langue     varchar(8);
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS trace_json text;
CREATE INDEX IF NOT EXISTS messages_date_creation_idx ON public.messages (date_creation DESC);

-- "When can I come by / opening hours / contact" used to fall on the nearest
-- exemplar, which was service/overview. Give them their own exemplars.
INSERT INTO public.catalog_exemplar (domain, intent, text, language, topic, slots, updated_by) VALUES
 ('service', 'location',  'quels sont les horaires d''ouverture de l''agence', 'fr', 'horaires', NULL, 'migration'),
 ('service', 'location',  'quand puis-je passer pour parler à un conseiller', 'fr', 'horaires', NULL, 'migration'),
 ('service', 'location',  'comment contacter le service client, numéro de téléphone', 'fr', 'contact', NULL, 'migration'),
 ('service', 'location',  'ما هي أوقات عمل الفرع', 'ar', 'horaires', NULL, 'migration'),
 ('service', 'location',  'what are the opening hours, when can I come to the branch', 'en', 'horaires', NULL, 'migration'),
 ('service', 'procedure', 'comment prendre rendez-vous avec un conseiller', 'fr', 'rendez-vous', NULL, 'migration')
ON CONFLICT DO NOTHING;

INSERT INTO public.catalog_release (version, sealed_by, exemplar_count, note)
SELECT '2026.09.3', 'migration',
       (SELECT count(*) FROM public.catalog_exemplar WHERE active),
       'Opening hours / contact / appointment exemplars'
WHERE NOT EXISTS (SELECT 1 FROM public.catalog_release WHERE version = '2026.09.3');

INSERT INTO public.catalog_release_exemplar (release_id, domain, intent, text, language, topic, slots)
SELECT r.id, e.domain, e.intent, e.text, e.language, e.topic, e.slots
FROM public.catalog_release r, public.catalog_exemplar e
WHERE r.version = '2026.09.3' AND e.active
  AND NOT EXISTS (SELECT 1 FROM public.catalog_release_exemplar x WHERE x.release_id = r.id);
