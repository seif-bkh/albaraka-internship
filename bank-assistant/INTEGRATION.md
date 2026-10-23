# Architecture d'Intégration du Chat Public

Ce document décrit la liaison complète entre le frontend client, le backend Spring Boot,
RAGLab (le service RAG externe) et le frontend Admin.

> **État depuis le 2026-10-22.** L'agent Python interne (`agent.py`, `api.py`, `albaraka/`)
> a été retiré : RAGLab est le seul moteur. Le backend est le seul écrivain en base, et il
> ne reste plus deux services qui se partagent les mêmes tables.

## Composants et flux de données

```text
[Frontend Client] (Angular)
       │
       │ POST /api/chat/message            (JSON: sessionId, message, langue)
       ▼
[Backend Spring Boot] (port 8081)
       │
       │ 1. Vérifie les limites de session (tokens/messages) dans `public_chat_session`.
       │ 2. Ouvre la ligne de l'échange (`startExchange`) avant l'appel.
       │ 3. Appelle le moteur, avec le jeton de service partagé :
       │      POST {RAGLAB_URL}/answer
       │      X-Service-Token: <RAGLAB_TOKEN>
       ▼
[RAGLab] (externe — http://<ip>:<port>)
       │
       │ Récupération + génération + citations, puis renvoie la réponse structurée
       │ (texte, affirmations, sources, statut de grounding). Il n'écrit rien en base.
       ▼
[Backend Spring Boot]
       │
       │ 1. Persiste l'échange dans `messages` / `conversations` (seul écrivain).
       │ 2. Applique le scrub PII sortant et la formulation de refus de la banque.
       │ 3. Règle le quota de session et émet la trame SSE finale.
       ▼
[PostgreSQL]
       │
       │ `conversations`, `messages`, `business_facts`, `documents` (historique)…
       ▼
[Frontend Admin] (Angular)
       │
       │ Historique complet, sources documentaires, trace par échange.
```

## Points clés

1. **Écrivain unique** : le backend Java possède le schéma (Flyway) et écrit les échanges.
   RAGLab est sans état du point de vue de cette base : il répond, il ne persiste pas.
2. **SessionID cohérent** : le `sessionId` (UUID) généré par le client Angular traverse tout
   le cycle de vie sans être recréé.
3. **Limites de session** : les compteurs de tokens et de messages sont contrôlés de manière
   stricte dans Spring Boot *avant* d'appeler le moteur, préservant le quota.
4. **Suivi temps réel** : l'admin voit la session dès son premier message, parce que le
   backend écrit la ligne au début de l'échange, pas à la fin.
5. **Confidentialité des sources** : les sources sont stockées et affichées côté admin, mais
   le frontend client ne les expose pas.
6. **Résilience** : service injoignable ⇒ le backend répond « Service IA temporairement
   indisponible » (ou un refus honnête) et journalise l'échec ; il ne fabrique jamais de
   réponse. Sans `RAGLAB_URL`, la console dit « aucune adresse configurée ».
7. **Un seul moteur** : le sélecteur LEGACY/RAGLAB, `/admin/agent/engine`, la file
   d'ingestion Redis et le panneau de documents interne ont été supprimés avec l'agent. Le
   corpus et son index appartiennent à RAGLab (écran *Console RAGLab → Documents*).
