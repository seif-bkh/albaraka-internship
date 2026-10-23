-- "What do you offer / what can you do" is one of the most common first
-- messages and was falling into the greeting bucket. Add the OVERVIEW intent
-- with FR/AR/EN exemplars (plus AR/EN greetings) and seal them as a new release
-- so the running agent picks them up on next boot / publish.
INSERT INTO public.catalog_category (domain, intent, label_fr) VALUES
 ('service', 'overview', 'présenter l''offre et ce que l''assistant sait faire')
ON CONFLICT DO NOTHING;

INSERT INTO public.catalog_exemplar (domain, intent, text, language, topic, slots, updated_by) VALUES
 ('service', 'overview', 'quels sont les choix disponibles', 'fr', NULL, NULL, 'migration'),
 ('service', 'overview', 'présentez-moi vos services brièvement', 'fr', NULL, NULL, 'migration'),
 ('service', 'overview', 'quels produits propose la banque Al Baraka', 'fr', NULL, NULL, 'migration'),
 ('service', 'overview', 'que pouvez-vous faire pour moi', 'fr', NULL, NULL, 'migration'),
 ('service', 'overview', 'ما هي الخدمات التي تقدمونها', 'ar', NULL, NULL, 'migration'),
 ('service', 'overview', 'ما هي منتجات بنك البركة', 'ar', NULL, NULL, 'migration'),
 ('service', 'overview', 'what services do you offer', 'en', NULL, NULL, 'migration'),
 ('service', 'overview', 'what can you help me with', 'en', NULL, NULL, 'migration'),
 ('conversationnel', 'greeting', 'السلام عليكم', 'ar', NULL, NULL, 'migration'),
 ('conversationnel', 'greeting', 'hello', 'en', NULL, NULL, 'migration')
ON CONFLICT DO NOTHING;

INSERT INTO public.catalog_release (version, sealed_by, exemplar_count, note)
SELECT '2026.09.2', 'migration',
       (SELECT count(*) FROM public.catalog_exemplar WHERE active),
       'Overview intent + AR/EN conversational exemplars'
WHERE NOT EXISTS (SELECT 1 FROM public.catalog_release WHERE version = '2026.09.2');

INSERT INTO public.catalog_release_exemplar (release_id, domain, intent, text, language, topic, slots)
SELECT r.id, e.domain, e.intent, e.text, e.language, e.topic, e.slots
FROM public.catalog_release r, public.catalog_exemplar e
WHERE r.version = '2026.09.2' AND e.active
  AND NOT EXISTS (SELECT 1 FROM public.catalog_release_exemplar x WHERE x.release_id = r.id);
