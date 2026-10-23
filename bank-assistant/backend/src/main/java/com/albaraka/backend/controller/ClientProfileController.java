package com.albaraka.backend.controller;

import com.albaraka.backend.model.Beneficiaire;
import com.albaraka.backend.model.Carte;
import com.albaraka.backend.model.Client;
import com.albaraka.backend.model.Compte;
import com.albaraka.backend.model.ConversationEntity;
import com.albaraka.backend.model.Financement;
import com.albaraka.backend.model.MessageEntity;
import com.albaraka.backend.model.Transaction;
import com.albaraka.backend.repository.BeneficiaireRepository;
import com.albaraka.backend.repository.CarteRepository;
import com.albaraka.backend.repository.ClientRepository;
import com.albaraka.backend.repository.CompteRepository;
import com.albaraka.backend.repository.ConversationRepository;
import com.albaraka.backend.repository.FinancementRepository;
import com.albaraka.backend.repository.MessageRepository;
import com.albaraka.backend.repository.TransactionRepository;
import com.albaraka.backend.security.UserDetailsImpl;
import com.albaraka.backend.services.AnswerSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal API of the authenticated bank client (role {@code ROLE_UTILISATEUR}).
 *
 * <p>Everything is scoped server-side to the caller's identity: a client can
 * only ever read/write their own profile, products and conversations. Staff
 * identities are rejected with 403 — {@code /api/client/**} is not their surface
 * (the admin console reads conversations through {@code /api/clients/**}).
 */
@RestController
@RequestMapping("/api/client/me")
@PreAuthorize("hasAuthority('ROLE_UTILISATEUR')")
public class ClientProfileController {

    private static final int MAX_TRANSACTION_LIMIT = 50;

    @Autowired private ClientRepository clientRepository;
    @Autowired private CompteRepository compteRepository;
    @Autowired private CarteRepository carteRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private FinancementRepository financementRepository;
    @Autowired private BeneficiaireRepository beneficiaireRepository;

    // ────────────────────────────── Profil ──────────────────────────────

    /** The caller's profile; assigns the business reference on first read. */
    @GetMapping
    @Transactional
    public ResponseEntity<?> me(@AuthenticationPrincipal UserDetailsImpl principal) {
        Client client = requireClient(principal);
        if (client == null) {
            return notClient();
        }
        if (client.getReferenceClient() == null || client.getReferenceClient().isBlank()) {
            client.setReferenceClient("ABK" + String.format("%06d", client.getId()));
            clientRepository.save(client);
        }
        return ResponseEntity.ok(toProfileDto(client));
    }

    /** Edits the contact/preference fields only (identity fields are read-only). */
    @PutMapping
    @Transactional
    public ResponseEntity<?> updateMe(@AuthenticationPrincipal UserDetailsImpl principal,
                                      @RequestBody Map<String, Object> patch) {
        Client client = requireClient(principal);
        if (client == null) {
            return notClient();
        }
        if (patch.containsKey("telephone")) {
            client.setTelephone(stringOrNull(patch.get("telephone")));
        }
        if (patch.containsKey("adresse")) {
            client.setAdresse(stringOrNull(patch.get("adresse")));
        }
        if (patch.containsKey("ville")) {
            client.setVille(stringOrNull(patch.get("ville")));
        }
        if (patch.containsKey("dateNaissance")) {
            String raw = stringOrNull(patch.get("dateNaissance"));
            if (raw == null || raw.isBlank()) {
                client.setDateNaissance(null);
            } else {
                try {
                    client.setDateNaissance(java.time.LocalDate.parse(raw));
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "dateNaissance doit être au format ISO (yyyy-MM-dd)."));
                }
            }
        }
        if (patch.containsKey("languePreferee")) {
            String langue = stringOrNull(patch.get("languePreferee"));
            client.setLanguePreferee("ar".equalsIgnoreCase(langue) || "en".equalsIgnoreCase(langue)
                    ? langue.toLowerCase() : "fr");
        }
        clientRepository.save(client);
        return ResponseEntity.ok(toProfileDto(client));
    }

    // ────────────────────────────── Produits ──────────────────────────────

    @GetMapping("/comptes")
    public ResponseEntity<?> comptes(@AuthenticationPrincipal UserDetailsImpl principal) {
        Client client = requireClient(principal);
        if (client == null) {
            return notClient();
        }
        return ResponseEntity.ok(compteRepository.findByClientIdOrderByIdAsc(client.getId()).stream()
                .map(this::toCompteDto)
                .toList());
    }

    @GetMapping("/cartes")
    public ResponseEntity<?> cartes(@AuthenticationPrincipal UserDetailsImpl principal) {
        Client client = requireClient(principal);
        if (client == null) {
            return notClient();
        }
        return ResponseEntity.ok(carteRepository.findByClientIdOrderByIdAsc(client.getId()).stream()
                .map(this::toCarteDto)
                .toList());
    }

    /** Financing contracts (Mourabaha, Ijara, …) — read-only, the core bank books them. */
    @GetMapping("/financements")
    public ResponseEntity<?> financements(@AuthenticationPrincipal UserDetailsImpl principal) {
        Client client = requireClient(principal);
        if (client == null) {
            return notClient();
        }
        return ResponseEntity.ok(financementRepository.findByClientIdOrderByDateDebutDesc(client.getId())
                .stream().map(this::toFinancementDto).toList());
    }

    /** Saved transfer beneficiaries. The portal shows the RIB; the assistant never does. */
    @GetMapping("/beneficiaires")
    public ResponseEntity<?> beneficiaires(@AuthenticationPrincipal UserDetailsImpl principal) {
        Client client = requireClient(principal);
        if (client == null) {
            return notClient();
        }
        return ResponseEntity.ok(beneficiaireRepository.findByClientIdOrderByNomAsc(client.getId())
                .stream().map(this::toBeneficiaireDto).toList());
    }

    /** Latest movements of the caller, optionally filtered to one of their accounts. */
    @GetMapping("/transactions")
    public ResponseEntity<?> transactions(@AuthenticationPrincipal UserDetailsImpl principal,
                                          @RequestParam(required = false) Long compteId,
                                          @RequestParam(defaultValue = "12") int limit) {
        Client client = requireClient(principal);
        if (client == null) {
            return notClient();
        }
        int bounded = Math.max(1, Math.min(limit, MAX_TRANSACTION_LIMIT));
        List<Transaction> rows = (compteId == null)
                ? transactionRepository.findByCompteClientIdOrderByIdDesc(client.getId(), PageRequest.of(0, bounded))
                : transactionRepository.findByCompteClientIdAndCompteIdOrderByIdDesc(client.getId(), compteId, PageRequest.of(0, bounded));
        return ResponseEntity.ok(rows.stream().map(this::toTransactionDto).toList());
    }

    // ────────────────────────────── Conversations ──────────────────────────────

    /** The caller's own conversations (client_id link, V8) — anonymous sessions excluded. */
    @GetMapping("/conversations")
    public ResponseEntity<?> conversations(@AuthenticationPrincipal UserDetailsImpl principal) {
        Client client = requireClient(principal);
        if (client == null) {
            return notClient();
        }
        return ResponseEntity.ok(conversationRepository.findByClientIdOrderByDateDebutDesc(client.getId()).stream()
                .map(this::toConversationDto)
                .toList());
    }

    /** Messages of one of the caller's conversations; ownership is enforced. */
    @GetMapping("/conversations/{id}/messages")
    @Transactional(readOnly = true)
    public ResponseEntity<?> conversationMessages(@AuthenticationPrincipal UserDetailsImpl principal,
                                                  @org.springframework.web.bind.annotation.PathVariable Long id) {
        Client client = requireClient(principal);
        if (client == null) {
            return notClient();
        }
        ConversationEntity conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null || !client.getId().equals(conversation.getClientId())) {
            return ResponseEntity.status(404).body(Map.of("error", "Conversation introuvable."));
        }
        List<MessageEntity> messages = messageRepository.findByConversation_IdOrderByDateCreationAsc(id);
        return ResponseEntity.ok(messages.stream()
                .map(m -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", m.getId());
                    dto.put("question", m.getQuestion());
                    // Same reason as `sources` below: this is a client surface, so the
                    // stored text is sanitised too — a row written before the
                    // no-provenance policy may still carry the engine's own wording.
                    dto.put("reponse", AnswerSanitizer.sanitizeStored(m.getReponse()));
                    // No `sources` here: which documents backed the answer is reviewer
                    // information (see /api/admin/monitoring), not client information.
                    dto.put("dateCreation", m.getDateCreation());
                    return dto;
                })
                .toList());
    }

    // ────────────────────────────── Helpers ──────────────────────────────

    private Client requireClient(UserDetailsImpl principal) {
        if (principal == null) {
            return null;
        }
        // Role check happens here (not only in @PreAuthorize) so the 403 message is explicit.
        boolean isClientRole = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_UTILISATEUR".equals(a.getAuthority()));
        if (!isClientRole) {
            return null;
        }
        return clientRepository.findById(principal.getId()).orElse(null);
    }

    private ResponseEntity<Map<String, String>> notClient() {
        return ResponseEntity.status(403)
                .body(Map.of("error", "Espace réservé aux clients de la banque."));
    }

    private static String stringOrNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Map<String, Object> toConversationDto(ConversationEntity c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", c.getId());
        dto.put("sessionId", c.getSessionId());
        dto.put("nombreMessages", c.getNombreMessages());
        dto.put("dateDebut", c.getDateDebut());
        // Sidebar label: first question of the conversation when available.
        List<MessageEntity> first = messageRepository.findMessagesBySessionIdOrderByDateCreationAsc(c.getSessionId());
        dto.put("titre", first.isEmpty() || first.get(0).getQuestion() == null
                ? c.getSessionId()
                : first.get(0).getQuestion());
        return dto;
    }

    private Map<String, Object> toProfileDto(Client c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", c.getId());
        dto.put("email", c.getEmail());
        dto.put("nom", c.getNom());
        dto.put("prenom", c.getPrenom());
        dto.put("telephone", c.getTelephone());
        dto.put("adresse", c.getAdresse());
        dto.put("ville", c.getVille());
        dto.put("dateNaissance", c.getDateNaissance() == null ? null : c.getDateNaissance().toString());
        dto.put("languePreferee", c.getLanguePreferee() == null ? "fr" : c.getLanguePreferee());
        dto.put("segment", c.getSegment());
        dto.put("referenceClient", c.getReferenceClient());
        dto.put("dateCreation", c.getDateCreation());
        return dto;
    }

    private Map<String, Object> toCompteDto(Compte c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", c.getId());
        dto.put("libelle", c.getLibelle());
        dto.put("type", c.getType());
        dto.put("iban", c.getIban());
        dto.put("devise", c.getDevise());
        dto.put("solde", c.getSolde());
        dto.put("soldeDisponible", c.getSoldeDisponible() != null ? c.getSoldeDisponible() : c.getSolde());
        dto.put("decouvertAutorise", c.getDecouvertAutorise());
        dto.put("statut", c.getStatut());
        dto.put("dateOuverture", c.getDateOuverture());
        return dto;
    }

    private Map<String, Object> toCarteDto(Carte c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", c.getId());
        dto.put("type", c.getType());
        dto.put("reseau", c.getReseau());
        dto.put("porteur", c.getPorteur());
        dto.put("expiration", c.getExpiration());
        dto.put("plafondJournalier", c.getPlafondJournalier());
        dto.put("bloque", c.isBloque());
        dto.put("categorie", c.getCategorie());
        dto.put("statut", c.getStatut());
        dto.put("plafondRetrait", c.getPlafondRetrait());
        dto.put("compteId", c.getCompteId());
        return dto;
    }

    private Map<String, Object> toFinancementDto(Financement f) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", f.getId());
        dto.put("reference", f.getReference());
        dto.put("produit", f.getProduit());
        dto.put("objet", f.getObjet());
        dto.put("montantFinance", f.getMontantFinance());
        dto.put("margeTaux", f.getMargeTaux());
        dto.put("dureeMois", f.getDureeMois());
        dto.put("echeanceMensuelle", f.getEcheanceMensuelle());
        dto.put("capitalRestant", f.getCapitalRestant());
        dto.put("dateDebut", f.getDateDebut());
        dto.put("dateFin", f.getDateFin());
        dto.put("prochaineEcheance", f.getProchaineEcheance());
        dto.put("statut", f.getStatut());
        return dto;
    }

    private Map<String, Object> toBeneficiaireDto(Beneficiaire b) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", b.getId());
        dto.put("nom", b.getNom());
        dto.put("rib", b.getRib());
        dto.put("banque", b.getBanque());
        dto.put("pays", b.getPays());
        dto.put("type", b.getType());
        dto.put("actif", b.isActif());
        return dto;
    }

    private Map<String, Object> toTransactionDto(Transaction t) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", t.getId());
        dto.put("compteId", t.getCompte().getId());
        dto.put("dateOperation", t.getDateOperation());
        dto.put("libelle", t.getLibelle());
        dto.put("categorie", t.getCategorie());
        dto.put("montant", t.getMontant());
        dto.put("devise", t.getDevise());
        return dto;
    }
}
