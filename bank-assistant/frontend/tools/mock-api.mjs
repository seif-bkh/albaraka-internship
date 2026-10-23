// Tiny mock of the Spring backend so the SPA can be rendered (screenshots,
// design work, demos) without Java/Postgres. `node tools/mock-api.mjs` -> :8081.
// Data is intentionally realistic (French/Arabic corpus, real product names).
import http from 'node:http';

const now = () => new Date().toISOString();
const daysAgo = (d, h = 10) => { const x = new Date(); x.setDate(x.getDate() - d); x.setHours(h, 12, 0, 0); return x.toISOString(); };

const users = [
  { id: 1, email: 'admin@albaraka.tn', nom: 'Admin', prenom: 'Super', role: 'SUPER_ADMIN', actif: true, dateCreation: daysAgo(90), derniereConnexion: daysAgo(0, 8) },
  { id: 2, email: 's.fellari@albaraka.tn', nom: 'Fellari', prenom: 'Salim', role: 'ADMIN_DOCUMENTS', actif: true, dateCreation: daysAgo(60), derniereConnexion: daysAgo(1) },
  { id: 3, email: 'supervision@albaraka.tn', nom: 'Trabelsi', prenom: 'Amel', role: 'ADMIN_SUPERVISION', actif: true, dateCreation: daysAgo(40), derniereConnexion: daysAgo(3) },
  { id: 4, email: 'viewer@albaraka.tn', nom: 'Gharbi', prenom: 'Nizar', role: 'VIEWER', actif: true, dateCreation: daysAgo(30), derniereConnexion: null },
];

// ── Portail client (données de démonstration, mêmes formes que l'API Java) ──
const clientProfile = {
  id: 100, email: 'client@albaraka.tn', nom: 'Ben Salah', prenom: 'Yassine',
  telephone: '+216 98 234 567', adresse: '12 Avenue Habib Bourguiba', ville: 'Tunis',
  dateNaissance: '1991-04-17', languePreferee: 'fr', segment: 'PARTICULIER',
  referenceClient: 'ABK000120', dateCreation: daysAgo(430),
};
const comptes = [
  { id: 1, libelle: 'Compte principal', type: 'COMPTE_COURANT', iban: 'TN59 04000 0123456789102 68', devise: 'TND', solde: 12450.480, dateOuverture: daysAgo(430) },
  { id: 2, libelle: 'Épargne CEAI', type: 'EPARGNE', iban: 'TN59 04000 0987654321098 71', devise: 'TND', solde: 6292.000, dateOuverture: daysAgo(300) },
];
const cartes = [
  { id: 1, type: 'EVASION', reseau: 'MASTERCARD', porteur: '4571 **** **** 6842', expiration: '09/29', plafondJournalier: 3000, bloque: false, compteId: 1 },
  { id: 2, type: 'GOLD_NATIONALE', reseau: 'MASTERCARD', porteur: '5213 **** **** 1149', expiration: '03/28', plafondJournalier: 1500, bloque: false, compteId: 1 },
];
const transactions = [
  { id: 21, compteId: 1, dateOperation: daysAgo(0, 12), libelle: 'Salaire — Ste Médina Capital', categorie: 'VIREMENT', montant: 4200, devise: 'TND' },
  { id: 20, compteId: 1, dateOperation: daysAgo(1, 19), libelle: 'Carrefour Lac 2', categorie: 'COURSES', montant: -184.320, devise: 'TND' },
  { id: 19, compteId: 1, dateOperation: daysAgo(1, 13), libelle: 'Resto Dar El Jeld', categorie: 'RESTAURATION', montant: -96.500, devise: 'TND' },
  { id: 18, compteId: 2, dateOperation: daysAgo(2, 10), libelle: 'Intérêts CEAI', categorie: 'INTERETS', montant: 22.615, devise: 'TND' },
  { id: 17, compteId: 1, dateOperation: daysAgo(3, 9), libelle: 'Prélèvement SONEDE', categorie: 'FACTURES', montant: -48.400, devise: 'TND' },
  { id: 16, compteId: 1, dateOperation: daysAgo(4, 18), libelle: 'Retrait DAB Menzah 6', categorie: 'RETRAIT', montant: -200, devise: 'TND' },
  { id: 15, compteId: 1, dateOperation: daysAgo(6, 15), libelle: 'Amazon EU', categorie: 'ACHATS', montant: -129.990, devise: 'TND' },
  { id: 14, compteId: 2, dateOperation: daysAgo(8, 11), libelle: 'Virement épargne', categorie: 'VIREMENT', montant: 500, devise: 'TND' },
  { id: 13, compteId: 1, dateOperation: daysAgo(9, 8), libelle: 'Prélèvement STEG', categorie: 'FACTURES', montant: -87.230, devise: 'TND' },
  { id: 12, compteId: 1, dateOperation: daysAgo(11, 20), libelle: 'Monoprix La Marsa', categorie: 'COURSES', montant: -233.115, devise: 'TND' },
];
const myConversations = [
  { id: 12, sessionId: 'a1b2c3d4-0000-4000-8000-000000000012', nombreMessages: 4, dateDebut: daysAgo(0, 10), titre: "C'est quoi le financement Mourabaha ?" },
  { id: 11, sessionId: 'a1b2c3d4-0000-4000-8000-000000000011', nombreMessages: 2, dateDebut: daysAgo(3, 15), titre: 'Documents pour financer une voiture' },
  { id: 9, sessionId: 'a1b2c3d4-0000-4000-8000-000000000009', nombreMessages: 2, dateDebut: daysAgo(12, 9), titre: "Quels sont les horaires d'ouverture ?" },
];

const documents = [
  { id: 1, nomFichier: 'Guide_Interne_Operations_Bancaires_Islamiques.docx', type: 'financement', tailleFichier: 91421, langue: 'ar', statut: 'VALIDE', statutIndexation: 'INDEXE', dateDepot: daysAgo(5), dateMiseAJour: daysAgo(5) },
  { id: 2, nomFichier: 'Circulaire_BCT_2019-08.pdf', type: 'conformite', tailleFichier: 527283, langue: 'ar', statut: 'VALIDE', statutIndexation: 'INDEXE', dateDepot: daysAgo(5), dateMiseAJour: daysAgo(4) },
  { id: 3, nomFichier: 'Loi_2016-48.pdf', type: 'conformite', tailleFichier: 345840, langue: 'ar', statut: 'VALIDE', statutIndexation: 'EN_COURS', dateDepot: daysAgo(1), dateMiseAJour: daysAgo(1) },
  { id: 4, nomFichier: 'Fiche_produit_Sayarat_Al_Baraka.pdf', type: 'financement', tailleFichier: 210044, langue: 'fr', statut: 'VALIDE', statutIndexation: 'ERREUR', dateDepot: daysAgo(0, 9), dateMiseAJour: daysAgo(0, 9) },
  { id: 5, nomFichier: 'Conditions_Pack_Platinum.pdf', type: 'cartes', tailleFichier: 120311, langue: 'fr', statut: 'ARCHIVE', statutIndexation: 'INDEXE', dateDepot: daysAgo(20), dateMiseAJour: daysAgo(10) },
];

const ingestion = [
  { journalId: 11, documentId: 4, nomFichier: 'Fiche_produit_Sayarat_Al_Baraka.pdf', type: 'financement', version: 'v1', statutDocument: 'ACTIF', statutIndexation: 'ERREUR', statutJournal: 'ERREUR', nombreChunks: 0, dureeMs: 4210, messageErreur: '429 Quota exceeded for metric embed_content_free_tier_requests (limit 1000/day)', dateDebut: daysAgo(0, 9), dateFin: daysAgo(0, 9) },
  { journalId: 10, documentId: 3, nomFichier: 'Loi_2016-48.pdf', type: 'conformite', version: 'v1', statutDocument: 'ACTIF', statutIndexation: 'EN_COURS', statutJournal: 'EN_COURS', nombreChunks: null, dureeMs: null, messageErreur: null, dateDebut: daysAgo(1), dateFin: null },
  { journalId: 9, documentId: 2, nomFichier: 'Circulaire_BCT_2019-08.pdf', type: 'conformite', version: 'v2', statutDocument: 'ACTIF', statutIndexation: 'INDEXE', statutJournal: 'SUCCES', nombreChunks: 38, dureeMs: 18320, messageErreur: null, dateDebut: daysAgo(4), dateFin: daysAgo(4) },
  { journalId: 8, documentId: 1, nomFichier: 'Guide_Interne_Operations_Bancaires_Islamiques.docx', type: 'financement', version: 'v1', statutDocument: 'ACTIF', statutIndexation: 'INDEXE', statutJournal: 'SUCCES', nombreChunks: 212, dureeMs: 96110, messageErreur: null, dateDebut: daysAgo(5), dateFin: daysAgo(5) },
];

const facts = [
  { id: 1, domain: 'financement', key: 'car_rate', value: 'Marge indexée sur le TMM + 3 points (grille en agence)', factType: 'RATE', topic: 'voiture', language: 'fr', source: 'governance:sayarat-rate', active: true, validFrom: daysAgo(30), updatedBy: 'admin@albaraka.tn', updatedAt: daysAgo(30) },
  { id: 2, domain: 'financement', key: 'car_max_duration', value: '7 ans maximum, mensualités fixes', factType: 'CONDITION', topic: 'voiture', language: 'fr', source: 'albaraka.com.tn/sayarat', active: true, validFrom: daysAgo(30), updatedBy: 'admin@albaraka.tn', updatedAt: daysAgo(12) },
  { id: 3, domain: 'comptes', key: 'ceai_min_deposit', value: 'Dépôt initial minimum : 50 DT', factType: 'CONDITION', topic: 'CEAI', language: 'fr', source: null, active: false, validFrom: null, updatedBy: 's.fellari@albaraka.tn', updatedAt: daysAgo(2) },
];

const exemplars = [
  { id: 1, domain: 'financement', intent: 'describe', text: "c'est quoi le financement Mourabaha", language: 'fr', topic: 'Mourabaha', slots: null, active: true, updatedBy: 'seed', updatedAt: daysAgo(40) },
  { id: 2, domain: 'financement', intent: 'rate', text: 'quel est le taux pour financer une voiture', language: 'fr', topic: 'voiture', slots: null, active: true, updatedBy: 'seed', updatedAt: daysAgo(40) },
  { id: 3, domain: 'service', intent: 'location', text: "quels sont les horaires d'ouverture de l'agence", language: 'fr', topic: 'horaires', slots: null, active: true, updatedBy: 'migration', updatedAt: daysAgo(0) },
  { id: 4, domain: 'service', intent: 'overview', text: 'ما هي الخدمات التي تقدمونها', language: 'ar', topic: null, slots: null, active: true, updatedBy: 'migration', updatedAt: daysAgo(1) },
  { id: 5, domain: 'conversationnel', intent: 'greeting', text: 'hello', language: 'en', topic: null, slots: null, active: true, updatedBy: 'migration', updatedAt: daysAgo(1) },
  { id: 6, domain: 'hors_perimetre', intent: 'reject', text: 'donne-moi une recette de cuisine', language: 'fr', topic: null, slots: null, active: false, updatedBy: 'admin@albaraka.tn', updatedAt: daysAgo(3) },
];
const releases = [
  { id: 3, version: '2026.09.3', sealedBy: 'migration', sealedAt: daysAgo(0), exemplarCount: 34, note: 'Opening hours / contact / appointment exemplars', published: true },
  { id: 2, version: '2026.09.2', sealedBy: 'migration', sealedAt: daysAgo(1), exemplarCount: 28, note: 'Overview intent + AR/EN conversational exemplars', published: false },
  { id: 1, version: '2026.09.1', sealedBy: 'seed', sealedAt: daysAgo(40), exemplarCount: 18, note: 'Initial seed', published: false },
];

const trace = {
  steps: [
    { name: 'language', ms: 0.4, data: { requested: 'auto', detected: 'fr', message: "c'est quoi le financement Mourabaha ?" } },
    { name: 'classify', ms: 212.7, data: { language: 'fr', method: 'embedding-nearest-exemplar', thresholds: { confident: 0.55, ambiguous: 0.35 }, candidates: [
      { similarity: 0.94, domain: 'financement', intent: 'describe', exemplar: "c'est quoi le financement Mourabaha", language: 'fr' },
      { similarity: 0.71, domain: 'financement', intent: 'rate', exemplar: 'quel est le taux pour financer une voiture', language: 'fr' },
      { similarity: 0.62, domain: 'financement', intent: 'documents', exemplar: 'quels documents pour un financement voiture', language: 'fr' },
      { similarity: 0.44, domain: 'service', intent: 'overview', exemplar: 'quels sont les choix disponibles', language: 'fr' },
      { similarity: 0.31, domain: 'conversationnel', intent: 'greeting', exemplar: 'bonjour', language: 'fr' } ] } },
    { name: 'route', ms: 0.1, data: { domain: 'financement', intent: 'describe', route: 'explain', confidence: 0.94, topic: 'Mourabaha', out_of_scope: false, language: 'fr' } },
    { name: 'retrieve', ms: 388.2, data: { scope: 'domain=financement', query: "c'est quoi le financement Mourabaha ?", min_similarity: 0.5, candidates: [
      { similarity: 0.78, kept: true, title: 'Guide Interne Operations Bancaires Islamiques', domain: 'financement', version: 'v1', excerpt: 'عملية التمويل بالمرابحة هي عملية يتولى بمقتضاها البنك، بناء على طلب الحريف الآمر بالشراء، شراء منقولات أو عقارات أو خدمات لدى طرف ثالث ثم بيعها للآمر بالشراء بثمن يعادل تكلفة شرائها مع زيادة هامش ربح محدد مسبقا…' },
      { similarity: 0.74, kept: true, title: 'Circulaire BCT 2019 08', domain: 'conformite', version: 'v2', excerpt: 'الفصل 4 : عملية التمويل بالمرابحة هي عملية يتولى بمقتضاها البنك أو المؤسسة المالية شراء منقولات أو عقارات أو خدمات…' },
      { similarity: 0.61, kept: true, title: 'Guide Interne Operations Bancaires Islamiques', domain: 'financement', version: 'v1', excerpt: 'يجب أن يكون الأصل في ملك البنك عند إبرام عقد بيع المرابحة، وأن يكون البيع منجزا وغير معلق على شرط…' },
      { similarity: 0.47, kept: false, title: 'Loi 2016 48', domain: 'conformite', version: 'v1', excerpt: 'الفصل 11 : يمكن للبنوك والمؤسسات المالية ممارسة عمليات الصيرفة الإسلامية…' },
      { similarity: 0.39, kept: false, title: 'Circulaire BCT 2019 08', domain: 'conformite', version: 'v2', excerpt: 'العنوان الثاني : عمليات التمويل التشاركي…' } ] } },
    { name: 'gate', ms: 0.6, data: { verdict: 'passed', reasons: [], citations: ['Guide_Interne_Operations_Bancaires_Islamiques.docx', 'Circulaire_BCT_2019-08.pdf'], coverage: 1 } },
    { name: 'llm_prompt', ms: 1.1, data: { model: 'openai/gpt-oss-120b', system: 'You are a warm, precise Al Baraka Bank assistant. Turn the provided structured answer into a concise, natural reply in the requested language…', user: 'Language: fr\nTopic: Mourabaha\nSources: Guide Interne Operations Bancaires Islamiques, Circulaire BCT 2019 08\nSections:\n[what_it_is] عملية التمويل بالمرابحة هي عملية يتولى بمقتضاها البنك…\n[how_it_works] يجب أن يكون الأصل في ملك البنك عند إبرام عقد بيع المرابحة…\n[sources] …' } },
  ],
};
const answerMourabaha = "La Mourabaha est un financement dans lequel la banque achète, à votre demande, le bien que vous souhaitez (véhicule, logement, équipement) puis vous le revend à un prix égal à son coût d'achat augmenté d'une marge de bénéfice fixée à l'avance. Vous remboursez ce prix par mensualités connues dès la signature.\n\nConditions clés :\n- Le bien doit appartenir à la banque au moment de la vente.\n- La vente est ferme et non conditionnelle.\n- La marge est fixe et connue du client dès le départ.";
const myConversationMessages = [
  { id: 90, question: "C'est quoi le financement Mourabaha ?", reponse: answerMourabaha, sources: ['Guide_Interne_Operations_Bancaires_Islamiques.docx', 'Circulaire_BCT_2019-08.pdf'], dateCreation: daysAgo(0, 10) },
  { id: 91, question: 'Et quel est le taux pour une voiture ?', reponse: 'La marge pour Sayarat Al Baraka est indexée sur le TMM majoré de 3 points ; la grille exacte est disponible en agence. Le financement couvre jusqu’à 80 % du prix sur 7 ans maximum.', sources: ['governance:sayarat-rate', 'Guide_Interne_Operations_Bancaires_Islamiques.docx'], dateCreation: daysAgo(0, 11) },
];
const turns = [
  { id: 41, dateCreation: daysAgo(0, 11), sessionId: 'a1b2c3d4-…', question: 'quand puis-je passer pour parler à un conseiller ?', reponse: "Je n'ai pas encore d'information fiable sur ce sujet dans mes documents. Pour cette demande, contactez directement votre agence Al Baraka.", intention: 'location', domaine: 'service', langue: 'fr', grounded: false, sources: [], hasTrace: true },
  { id: 40, dateCreation: daysAgo(0, 11), sessionId: 'a1b2c3d4-…', question: "c'est quoi le financement Mourabaha ?", reponse: answerMourabaha, intention: 'describe', domaine: 'financement', langue: 'fr', grounded: true, sources: ['Guide_Interne_Operations_Bancaires_Islamiques.docx', 'Circulaire_BCT_2019-08.pdf'], hasTrace: true },
  { id: 39, dateCreation: daysAgo(0, 10), sessionId: 'e5f6a7b8-…', question: 'ما هي الخدمات التي تقدمونها', reponse: 'إليكم ما تصفه وثائقنا: المرابحة (المرابحة)، الإجارة مع خيار التملك، الاستصناع، السلم، المشاركة، المضاربة…', intention: 'overview', domaine: 'service', langue: 'ar', grounded: true, sources: ['Circulaire_BCT_2019-08.pdf', 'Guide_Interne_Operations_Bancaires_Islamiques.docx'], hasTrace: true },
  { id: 38, dateCreation: daysAgo(1), sessionId: 'c9d0e1f2-…', question: 'donne-moi une recette de couscous', reponse: "Je ne peux pas vous aider sur ce sujet. Contactez Al Baraka pour plus d'informations.", intention: 'reject', domaine: 'hors_perimetre', langue: 'fr', grounded: false, sources: [], hasTrace: true },
  { id: 37, dateCreation: daysAgo(1), sessionId: 'c9d0e1f2-…', question: 'what is the rate for car financing?', reponse: 'The profit margin for Sayarat Al Baraka car financing is indexed on the TMM plus 3 points; the exact grid is available in branch. Financing covers up to 80% of the price over a maximum of 7 years with fixed instalments.', intention: 'rate', domaine: 'financement', langue: 'en', grounded: true, sources: ['governance:sayarat-rate', 'Guide_Interne_Operations_Bancaires_Islamiques.docx'], hasTrace: true },
  { id: 36, dateCreation: daysAgo(2), sessionId: '11223344-…', question: 'bonjour', reponse: "Bonjour ! Je suis l'assistant d'Al Baraka Bank Tunisie. Posez-moi votre question sur nos produits et services.", intention: 'greeting', domaine: 'conversationnel', langue: 'fr', grounded: true, sources: [], hasTrace: true },
];

const conversations = [
  { id: 12, sessionIdTronque: 'a1b2c3d4', langue: 'fr', nombreMessages: 4, dateDebut: daysAgo(0, 10), dateFin: daysAgo(0, 11), localisation: 'Tunis' },
  { id: 11, sessionIdTronque: 'e5f6a7b8', langue: 'ar', nombreMessages: 2, dateDebut: daysAgo(0, 9), dateFin: daysAgo(0, 10), localisation: 'Sfax' },
  { id: 10, sessionIdTronque: 'c9d0e1f2', langue: 'en', nombreMessages: 3, dateDebut: daysAgo(1), dateFin: daysAgo(1), localisation: 'Sousse' },
  { id: 9, sessionIdTronque: '11223344', langue: 'fr', nombreMessages: 1, dateDebut: daysAgo(2), dateFin: daysAgo(2), localisation: 'Manouba' },
];
const convMessages = {
  conversationId: 12, sessionId: 'a1b2c3d4-0000-4000-8000-000000000000', langue: 'fr', adresseIp: '41.x.x.x', dateDebut: daysAgo(0, 10), dateFin: daysAgo(0, 11),
  messages: [
    { id: 36, question: 'bonjour', questionReformulee: null, intention: 'greeting', reponse: "Bonjour ! Je suis l'assistant d'Al Baraka Bank Tunisie…", sourcesUtilisees: [], ragUtilise: false, dateCreation: daysAgo(0, 10) },
    { id: 40, question: "c'est quoi le financement Mourabaha ?", questionReformulee: null, intention: 'describe', reponse: answerMourabaha, sourcesUtilisees: ['Guide_Interne_Operations_Bancaires_Islamiques.docx'], ragUtilise: true, dateCreation: daysAgo(0, 11) },
    { id: 41, question: 'quand puis-je passer pour parler à un conseiller ?', questionReformulee: null, intention: 'location', reponse: "Je n'ai pas encore d'information fiable sur ce sujet…", sourcesUtilisees: [], ragUtilise: false, dateCreation: daysAgo(0, 11) },
  ],
};

const stats = {
  totalUsers: 4, activeUsers: 3, totalDocuments: 5, indexedDocuments: 3, totalConversations: 128, totalMessages: 412, totalClients: 96,
  parLangue: [{ langue: 'fr', count: 262 }, { langue: 'ar', count: 118 }, { langue: 'en', count: 32 }],
  parIntention: [{ intention: 'describe', count: 171 }, { intention: 'rate', count: 74 }, { intention: 'documents', count: 51 }, { intention: 'greeting', count: 48 }, { intention: 'overview', count: 33 }, { intention: 'reject', count: 21 }, { intention: 'location', count: 14 }],
  parJour: Array.from({ length: 14 }, (_, i) => ({ jour: daysAgo(13 - i).slice(0, 10), count: [12, 18, 22, 31, 27, 9, 6, 29, 35, 41, 38, 44, 30, 26][i] })),
};

const sessions = [
  { sessionId: 'a1b2c3d4-…', titre: 'Financement Mourabaha', dateDebut: daysAgo(0, 10), nombreMessages: 4 },
  { sessionId: '99887766-…', titre: 'Cartes et packs', dateDebut: daysAgo(3), nombreMessages: 2 },
];

function json(res, code, body) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*' });
  res.end(JSON.stringify(body));
}
function readBody(req) { return new Promise(r => { let b = ''; req.on('data', c => b += c); req.on('end', () => { try { r(JSON.parse(b || '{}')); } catch { r({}); } }); }); }

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;
  const m = req.method;
  if (m === 'OPTIONS') { res.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*' }); return res.end(); }

  if (p === '/api/auth/login' && m === 'POST') {
    const b = await readBody(req);
    // Identity split: bank clients (clients table) vs staff (users table).
    if (b.email === clientProfile.email) {
      return json(res, 200, { token: 'mock.jwt.token', refreshToken: 'mock.refresh', id: clientProfile.id, email: clientProfile.email, roles: ['ROLE_UTILISATEUR'] });
    }
    const u = users.find(x => x.email === b.email) || users[0];
    return json(res, 200, { token: 'mock.jwt.token', refreshToken: 'mock.refresh', id: u.id, email: u.email, roles: ['ROLE_' + u.role] });
  }
  if (p === '/api/auth/refresh') return json(res, 200, { token: 'mock.jwt.token', refreshToken: 'mock.refresh' });
  if (p === '/api/auth/logout') return json(res, 200, {});

  // ── Portail client (/api/client/me/**) ──
  if (p === '/api/client/me' && m === 'GET') return json(res, 200, clientProfile);
  if (p === '/api/client/me' && m === 'PUT') { const b = await readBody(req); Object.assign(clientProfile, { telephone: b.telephone ?? clientProfile.telephone, adresse: b.adresse ?? clientProfile.adresse, ville: b.ville ?? clientProfile.ville, dateNaissance: b.dateNaissance ?? clientProfile.dateNaissance, languePreferee: b.languePreferee ?? clientProfile.languePreferee }); return json(res, 200, clientProfile); }
  if (p === '/api/client/me/comptes') return json(res, 200, comptes);
  if (p === '/api/client/me/cartes') return json(res, 200, cartes);
  if (p === '/api/client/me/transactions') { const id = url.searchParams.get('compteId'); const limit = Number(url.searchParams.get('limit') || 12); const list = (id ? transactions.filter(t => String(t.compteId) === id) : transactions).slice(0, limit); return json(res, 200, list); }
  if (p === '/api/client/me/conversations' && m === 'GET') return json(res, 200, myConversations);
  if (p.match(/^\/api\/client\/me\/conversations\/\d+\/messages$/)) return json(res, 200, myConversationMessages);
  if (p === '/api/stats') return json(res, 200, stats);
  if (p === '/api/users' && m === 'GET') return json(res, 200, users);
  if (p === '/api/documents' && m === 'GET') return json(res, 200, { content: documents, totalElements: documents.length });
  if (p.match(/^\/api\/documents\/\d+\/utilisations/)) return json(res, 200, { content: turns.slice(1, 4).map(t => ({ messageId: t.id, conversationId: 12, sessionIdTronque: 'a1b2c3d4', question: t.question, intention: t.intention, dateCreation: t.dateCreation })), totalElements: 3 });
  if (p === '/api/admin/ingestion/status') return json(res, 200, ingestion);
  if (p === '/api/admin/facts/vocabulary') return json(res, 200, { domains: ['financement', 'comptes', 'cartes', 'conformite', 'service'], factTypes: ['RATE', 'CONDITION', 'FEE', 'DOCUMENT', 'OTHER'] });
  if (p === '/api/admin/facts' && m === 'GET') return json(res, 200, facts);
  if (p.match(/^\/api\/admin\/facts\/\d+\/history/)) return json(res, 200, [{ id: 1, action: 'UPDATE', oldValue: 'TMM + 3.5 points', newValue: facts[0].value, reason: 'Nouvelle grille septembre', actor: 'admin@albaraka.tn', at: daysAgo(12) }]);
  if (p === '/api/admin/taxonomy/vocabulary') return json(res, 200, { domains: ['financement', 'comptes', 'cartes', 'conformite', 'service', 'conversationnel', 'hors_perimetre'], intents: ['overview', 'describe', 'compare', 'rate', 'eligibility', 'documents', 'procedure', 'location', 'compliance', 'greeting', 'reject'] });
  if (p === '/api/admin/taxonomy/exemplars') return json(res, 200, exemplars);
  if (p === '/api/admin/taxonomy/releases') return json(res, 200, releases);
  if (p === '/api/admin/monitoring/turns') return json(res, 200, turns);
  if (p.match(/^\/api\/admin\/monitoring\/turns\/\d+$/)) { const t = turns.find(x => String(x.id) === p.split('/').pop()) || turns[1]; return json(res, 200, { summary: t, trace }); }
  if (p === '/api/admin/monitoring/explain') return json(res, 200, { turn_id: 'x', text: answerMourabaha, grounded: true, structured: { domain: 'financement', intent: 'describe', citations: turns[1].sources, citation_titles: ['Guide Interne Operations Bancaires Islamiques', 'Circulaire BCT 2019 08'] }, gate_reasons: [], trace });
  if (p === '/api/clients/conversations') return json(res, 200, { content: conversations, totalElements: 128 });
  if (p.match(/^\/api\/clients\/conversations\/\d+\/messages/)) return json(res, 200, convMessages);
  if (p.match(/^\/api\/clients\/conversations\/\d+\/resume/)) return json(res, 200, { conversationId: 12, langue: 'fr', nombreMessages: 4, dateDebut: daysAgo(0, 10), dateFin: daysAgo(0, 11), dureeTotaleMinutes: 42, sourcesUniques: ['Guide_Interne_Operations_Bancaires_Islamiques.docx'], adresseIpMasquee: '41.x.x.x' });
  if (p === '/api/chat/sessions') return json(res, 200, sessions);
  if (p === '/api/chat/history') return json(res, 200, convMessages.messages);
  if (p === '/api/chat/message' && m === 'POST') { const b = await readBody(req); const hours = /horaire|passer|conseiller|hours/i.test(b.message || ''); return json(res, 200, { reponse: hours ? turns[0].reponse : answerMourabaha, sessionId: b.sessionId || 'a1b2c3d4-0000-4000-8000-000000000000', tokensConsommes: 420, tokensLimite: 3000, nombreMessages: 2, messagesLimite: 20, sources: hours ? [] : ['Guide Interne Operations Bancaires Islamiques', 'Circulaire BCT 2019 08'], grounded: !hours }); }
  if (p === '/api/chat/message/stream' && m === 'POST') {
    const b = await readBody(req); const hours = /horaire|passer|conseiller|hours/i.test(b.message || '');
    const text = hours ? turns[0].reponse : answerMourabaha;
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', 'Access-Control-Allow-Origin': '*' });
    const words = text.split(/(?<=\s)/); let i = 0;
    const iv = setInterval(() => {
      if (i < words.length) { res.write(`data: ${JSON.stringify({ type: 'delta', text: words[i++] })}\n\n`); return; }
      clearInterval(iv);
      res.write(`data: ${JSON.stringify({ type: 'done', grounded: !hours, reponse: text, sources: hours ? [] : ['Guide Interne Operations Bancaires Islamiques', 'Circulaire BCT 2019 08'], sessionId: b.sessionId || 'a1b2c3d4-0000-4000-8000-000000000000', tokensConsommes: 420, tokensLimite: 3000, nombreMessages: 2, messagesLimite: 20 })}\n\n`);
      res.end();
    }, 15);
    return;
  }
  json(res, 404, { message: 'mock: no route for ' + m + ' ' + p });
});
server.listen(8081, '0.0.0.0', () => console.log('mock API on :8081'));
