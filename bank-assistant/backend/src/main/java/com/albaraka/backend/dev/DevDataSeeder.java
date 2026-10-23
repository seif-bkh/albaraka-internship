package com.albaraka.backend.dev;

import com.albaraka.backend.model.AdminPermission;
import com.albaraka.backend.model.AdminRole;
import com.albaraka.backend.model.Agence;
import com.albaraka.backend.model.Beneficiaire;
import com.albaraka.backend.model.Carte;
import com.albaraka.backend.model.Client;
import com.albaraka.backend.model.Compte;
import com.albaraka.backend.model.Financement;
import com.albaraka.backend.model.Role;
import com.albaraka.backend.model.Transaction;
import com.albaraka.backend.model.User;
import com.albaraka.backend.repository.AdminPermissionRepository;
import com.albaraka.backend.repository.AdminRoleRepository;
import com.albaraka.backend.repository.AgenceRepository;
import com.albaraka.backend.repository.BeneficiaireRepository;
import com.albaraka.backend.repository.CarteRepository;
import com.albaraka.backend.repository.ClientRepository;
import com.albaraka.backend.repository.CompteRepository;
import com.albaraka.backend.repository.FinancementRepository;
import com.albaraka.backend.repository.TransactionRepository;
import com.albaraka.backend.repository.UserRepository;
import com.albaraka.backend.security.AdminPermissionCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Development fixture: a small, realistic population to try the product against.
 *
 * <p>It never runs by accident. {@code app.dev-seed.enabled} defaults to false and is
 * only turned on by the documented local commands, so a production boot seeds nothing —
 * a known password in a live database is the cheapest way to lose one.
 *
 * <p>Two populations, matching the two kinds of account the product has:
 * <ul>
 *   <li><b>Staff</b> — one user per system role plus two custom roles a general manager
 *       would plausibly create, so every permission combination can be tried.</li>
 *   <li><b>Customers</b> — four people with different segments, languages, branches and
 *       banking situations (a car Mourabaha in progress, a professional Ijara, an
 *       investment deposit, one file still in KYC). Their records are what the assistant
 *       can describe in bands, and what the portal shows in full.</li>
 * </ul>
 *
 * <p>Idempotent: every block is guarded by an existence check, so restarting the stack
 * neither duplicates rows nor resets a password someone changed while testing.
 */
@Component
@Order(20)
@ConditionalOnProperty(prefix = "app.dev-seed", name = "enabled", havingValue = "true")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    /** Documented in docs/CUSTOMER_DATA_MODEL.md — never use outside a laptop. */
    static final String STAFF_PASSWORD = "dev12345";
    static final String CLIENT_PASSWORD = "client123";

    private final UserRepository users;
    private final ClientRepository clients;
    private final CompteRepository comptes;
    private final CarteRepository cartes;
    private final TransactionRepository transactions;
    private final FinancementRepository financements;
    private final BeneficiaireRepository beneficiaires;
    private final AgenceRepository agences;
    private final AdminRoleRepository roles;
    private final AdminPermissionRepository permissions;
    private final PasswordEncoder encoder;

    public DevDataSeeder(UserRepository users,
                         ClientRepository clients,
                         CompteRepository comptes,
                         CarteRepository cartes,
                         TransactionRepository transactions,
                         FinancementRepository financements,
                         BeneficiaireRepository beneficiaires,
                         AgenceRepository agences,
                         AdminRoleRepository roles,
                         AdminPermissionRepository permissions,
                         PasswordEncoder encoder) {
        this.users = users;
        this.clients = clients;
        this.comptes = comptes;
        this.cartes = cartes;
        this.transactions = transactions;
        this.financements = financements;
        this.beneficiaires = beneficiaires;
        this.agences = agences;
        this.roles = roles;
        this.permissions = permissions;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.warn("DEV SEED ENABLED — inserting fixture staff and customers. Never run this on a shared host.");
        seedRoles();
        seedStaff();
        seedCustomers();
        long staff = users.count();
        long customers = clients.count();
        log.warn("DEV SEED DONE — {} staff users, {} customers. See docs/CUSTOMER_DATA_MODEL.md for the credentials.", staff, customers);
    }

    // ────────────────────────────────────────────────────────────── roles

    /**
     * Two custom staff roles, created the way the general manager would create them in
     * the console: a customer-facing officer and an auditor. Both are flagged staff, which
     * is the only kind of role the assignment endpoints accept.
     */
    private void seedRoles() {
        role("CHARGE_CLIENTS", "Chargé de clientèle",
                "Lecture du référentiel documentaire et de la console RAGLab, sans écriture.",
                AdminPermissionCodes.DOCUMENTS_READ, AdminPermissionCodes.RAGLAB_VIEW);
        role("AUDITEUR", "Auditeur interne",
                "Lecture des documents et de l'historique des traitements, sans exécution.",
                AdminPermissionCodes.DOCUMENTS_READ, AdminPermissionCodes.DOCUMENTS_INGESTION_READ);
    }

    private void role(String code, String name, String description, String... permissionCodes) {
        Set<AdminPermission> granted = Set.copyOf(
                permissions.findByCodeInOrderBySortOrderAsc(Set.of(permissionCodes)));
        AdminRole existing = roles.findByCode(code).orElse(null);
        if (existing != null) {
            // Keep the fixture truthful if someone edited it in the console: only fill
            // an empty definition, never overwrite a deliberate change.
            if (existing.getPermissions().isEmpty()) {
                existing.updateDefinition(name, description, granted);
                roles.save(existing);
            }
            return;
        }
        AdminRole created = AdminRole.custom(code, name, description);
        created.updateDefinition(name, description, granted);
        roles.save(created);
    }

    // ────────────────────────────────────────────────────────────── staff

    private void seedStaff() {
        staff("documents@albaraka.tn", "Karim", "Mansour", "ADMIN_DOCUMENTS");
        staff("supervision@albaraka.tn", "Leila", "Gharbi", "ADMIN_SUPERVISION");
        staff("lecteur@albaraka.tn", "Sami", "Ferchichi", "VIEWER");
        staff("charge.clients@albaraka.tn", "Nour", "Baccouche", "CHARGE_CLIENTS");
        staff("auditeur@albaraka.tn", "Hatem", "Jlassi", "AUDITEUR");
    }

    private void staff(String email, String prenom, String nom, String roleCode) {
        if (users.findByEmail(email).isPresent()) {
            return;
        }
        if (!roles.existsByCode(roleCode)) {
            log.warn("DEV SEED — role {} is missing, skipping {}", roleCode, email);
            return;
        }
        User user = new User();
        user.setEmail(email);
        user.setMotDePasse(encoder.encode(STAFF_PASSWORD));
        user.setPrenom(prenom);
        user.setNom(nom);
        user.setRole(roleCode);
        user.setActif(true);
        users.save(user);
    }

    // ────────────────────────────────────────────────────────────── customers

    private void seedCustomers() {
        // 1. The historical demo client keeps its credentials; this only fills the
        //    banking relationship that V15 introduced, if the row predates it.
        enrichExisting("client@albaraka.tn");

        // 2. A private customer with a car Mourabaha running, Arabic-speaking.
        Client sana = customer("sana.trabelsi@albaraka.tn", "Sana", "Trabelsi",
                "PARTICULIER", "ar", "ARIANA", "Ariana", "Ingénieure logiciel", "Vermeg",
                new BigDecimal("3400.000"), LocalDate.of(1990, 6, 2), "VALIDE", "FAIBLE",
                LocalDate.of(2018, 3, 12));
        if (sana != null) {
            Compte courant = account(sana, "Compte courant", "COMPTE_COURANT",
                    "TN5910006035183500123456", "4820.750", "4750.750", "12.900", "TUN-ARIANA");
            account(sana, "Épargne CEAI", "COMPTE_EPARGNE",
                    "TN5910006035183500654321", "9250.000", null, "0", "TUN-ARIANA");
            card(sana, courant, "CIB CLASSIC", "CIB", "4123 **** **** 5567", "07/28",
                    "DEBIT", "1500.000", "400.000", false);
            movement(courant, "Salaire — Vermeg", "VIREMENT", "3450.000", 2, "VIREMENT", "Vermeg");
            movement(courant, "Mourabaha auto — échéance", "FINANCEMENT", "-412.500", 3, "PRELEVEMENT", "Al Baraka Bank");
            movement(courant, "MG Lac 2", "COURSES", "-215.400", 5, "CARTE", "MG");
            movement(courant, "STEG", "FACTURES", "-72.100", 9, "PRELEVEMENT", "STEG");
            financing(sana, "MOU-2024-0117", "MOURABAHA", "Voiture Kia Picanto",
                    "18500.000", "3500.000", "6.250", 48, "412.500", "14250.000",
                    LocalDate.now().minusMonths(12), "EN_COURS", "TUN-ARIANA");
            beneficiary(sana, "Ahmed Trabelsi", "TN5910006035183500998877", "Amen Bank", "VIREMENT");
            beneficiary(sana, "Propriétaire appartement", "TN5910006035183500111222", "BIAT", "VIREMENT");
        }

        // 3. A professional customer with an Ijara on equipment, French-speaking.
        Client mohamed = customer("mohamed.bouazizi@albaraka.tn", "Mohamed", "Bouazizi",
                "PROFESSIONNEL", "fr", "SOUSSE", "Sousse", "Gérant", "Bouazizi Distribution SARL",
                new BigDecimal("7200.000"), LocalDate.of(1982, 11, 21), "VALIDE", "MOYEN",
                LocalDate.of(2015, 9, 1));
        if (mohamed != null) {
            Compte pro = account(mohamed, "Compte professionnel", "COMPTE_PROFESSIONNEL",
                    "TN5910006035183500222333", "26400.500", "24100.500", "0", "TUN-SOUSSE");
            account(mohamed, "Dépôt d'investissement", "DEPOT_INVESTISSEMENT",
                    "TN5910006035183500444555", "60000.000", null, "0", "TUN-SOUSSE");
            card(mohamed, pro, "VISA BUSINESS", "VISA", "4532 **** **** 7781", "11/27",
                    "CREDIT", "6000.000", "1500.000", false);
            movement(pro, "Virement client — Pharmacie Centrale", "ENCAISSEMENT", "8900.000", 1, "VIREMENT", "Pharmacie Centrale");
            movement(pro, "Ijara matériel — échéance", "FINANCEMENT", "-1180.000", 4, "PRELEVEMENT", "Al Baraka Bank");
            movement(pro, "Fournisseur — Sotudis", "ACHATS", "-3200.000", 7, "VIREMENT", "Sotudis");
            financing(mohamed, "IJA-2023-0042", "IJARA", "Chariot élévateur",
                    "84000.000", "16800.000", "5.750", 60, "1180.000", "56000.000",
                    LocalDate.now().minusMonths(20), "EN_COURS", "TUN-SOUSSE");
            beneficiary(mohamed, "Sotudis", "TN5910006035183500777888", "BIAT", "VIREMENT");
        }

        // 4. A young customer saving, with a card temporarily blocked, English-speaking
        //    and a KYC file still being reviewed.
        Client ines = customer("ines.khemiri@albaraka.tn", "Ines", "Khemiri",
                "PARTICULIER", "en", "SFAX", "Sfax", "Étudiante en master", null,
                new BigDecimal("450.000"), LocalDate.of(2001, 1, 30), "EN_COURS", "FAIBLE",
                LocalDate.of(2024, 10, 5));
        if (ines != null) {
            Compte jeune = account(ines, "Compte courant étudiant", "COMPTE_COURANT",
                    "TN5910006035183500555666", "720.300", "720.300", "0", "TUN-SFAX");
            account(ines, "Épargne projet", "COMPTE_EPARGNE",
                    "TN5910006035183500666777", "2150.000", null, "0", "TUN-SFAX");
            card(ines, jeune, "CIB JEUNE", "CIB", "4123 **** **** 9902", "02/29",
                    "DEBIT", "800.000", "300.000", true);
            movement(jeune, "Bourse ministère", "VIREMENT", "350.000", 6, "VIREMENT", "Ministère");
            movement(jeune, "Librairie Al Kitab", "LOISIRS", "-68.900", 8, "CARTE", "Al Kitab");
            beneficiary(ines, "Mère — Leila Khemiri", "TN5910006035183500888999", "STB", "VIREMENT");
        }
    }

    /** Fills V15 fields for a customer that already existed before this migration. */
    private void enrichExisting(String email) {
        Client client = clients.findByEmail(email).orElse(null);
        if (client == null) {
            return;
        }
        if (client.getAgence() == null) {
            client.setAgence(agence("TUN-CENTRE"));
        }
        if (client.getStatutKyc() == null || client.getStatutKyc().isBlank()) {
            client.setStatutKyc("VALIDE");
        }
        clients.save(client);

        if (!comptes.findByClientIdOrderByIdAsc(client.getId()).isEmpty()
                && financements.findByClientIdOrderByDateDebutDesc(client.getId()).isEmpty()) {
            Compte courant = comptes.findByClientIdOrderByIdAsc(client.getId()).get(0);
            financing(client, "MOU-2025-0009", "MOURABAHA", "Travaux d'aménagement",
                    "24000.000", "6000.000", "6.500", 60, "455.000", "21300.000",
                    LocalDate.now().minusMonths(6), "EN_COURS", "TUN-CENTRE");
            beneficiary(client, "Sami Ben Salah", "TN5910006035183500121290", "Amen Bank", "VIREMENT");
            if (courant.getSoldeDisponible() == null) {
                courant.setSoldeDisponible(courant.getSolde());
                comptes.save(courant);
            }
        }
    }

    private Client customer(String email, String prenom, String nom, String segment, String langue,
                            String agenceCode, String ville, String profession, String employeur,
                            BigDecimal revenu, LocalDate naissance, String kyc, String risque,
                            LocalDate relationDepuis) {
        Client existing = clients.findByEmail(email).orElse(null);
        if (existing != null) {
            return null;
        }
        Client client = new Client();
        client.setEmail(email);
        client.setMotDePasse(encoder.encode(CLIENT_PASSWORD));
        client.setPrenom(prenom);
        client.setNom(nom);
        client.setRole(Role.UTILISATEUR);
        client.setSegment(segment);
        client.setLanguePreferee(langue);
        client.setVille(ville);
        client.setAgence(agence(agenceCode));
        client.setStatutKyc(kyc);
        client.setNiveauRisque(risque);
        client.setDateNaissance(naissance);
        client.setTelephone("+216 2" + (10000000 + Math.abs(email.hashCode()) % 89999999));
        client.setAdresse("Rue de la Liberté, " + ville);
        client.setSituationProfessionnelle(profession);
        client.setEmployeur(employeur);
        client.setRevenuMensuel(revenu);
        client.setRelationDepuis(relationDepuis);
        return clients.save(client);
    }

    private Agence agence(String code) {
        return agences.findByCode(code).orElse(null);
    }

    private Compte account(Client client, String libelle, String type, String iban,
                           String solde, String disponible, String decouvert, String agenceCode) {
        Compte compte = new Compte();
        compte.setClient(client);
        compte.setLibelle(libelle);
        compte.setType(type);
        compte.setIban(iban);
        compte.setDevise("TND");
        compte.setSolde(new BigDecimal(solde));
        compte.setSoldeDisponible(disponible == null ? null : new BigDecimal(disponible));
        compte.setDecouvertAutorise(new BigDecimal(decouvert));
        compte.setDateOuverture(LocalDateTime.now().minusMonths(30));
        compte.setStatut("ACTIF");
        return comptes.save(compte);
    }

    private void card(Client client, Compte compte, String type, String reseau, String porteur,
                      String expiration, String categorie, String plafondJour, String plafondRetrait,
                      boolean bloquee) {
        Carte carte = new Carte();
        carte.setClient(client);
        carte.setCompteId(compte.getId());
        carte.setType(type);
        carte.setReseau(reseau);
        carte.setPorteur(porteur);
        carte.setExpiration(expiration);
        carte.setCategorie(categorie);
        carte.setPlafondJournalier(new BigDecimal(plafondJour));
        carte.setPlafondRetrait(new BigDecimal(plafondRetrait));
        carte.setBloque(bloquee);
        carte.setStatut(bloquee ? "BLOQUEE" : "ACTIVE");
        cartes.save(carte);
    }

    private void movement(Compte compte, String libelle, String categorie, String montant,
                          int daysAgo, String canal, String contrepartie) {
        Transaction t = new Transaction();
        t.setCompte(compte);
        t.setLibelle(libelle);
        t.setCategorie(categorie);
        t.setMontant(new BigDecimal(montant));
        t.setDevise("TND");
        t.setDateOperation(LocalDateTime.now().minusDays(daysAgo));
        t.setDateValeur(LocalDateTime.now().minusDays(daysAgo));
        t.setCanal(canal);
        t.setContrepartie(contrepartie);
        t.setStatut("COMPTABILISEE");
        transactions.save(t);
    }

    private void financing(Client client, String reference, String produit, String objet,
                           String montant, String apport, String marge, int duree, String echeance,
                           String reste, LocalDate debut, String statut, String agenceCode) {
        Financement f = new Financement();
        f.setClient(client);
        f.setReference(reference);
        f.setProduit(produit);
        f.setObjet(objet);
        f.setMontantFinance(new BigDecimal(montant));
        f.setApport(new BigDecimal(apport));
        f.setMargeTaux(new BigDecimal(marge));
        f.setDureeMois(duree);
        f.setEcheanceMensuelle(new BigDecimal(echeance));
        f.setCapitalRestant(new BigDecimal(reste));
        f.setDateDebut(debut);
        f.setDateFin(debut.plusMonths(duree));
        f.setProchaineEcheance(LocalDate.now().withDayOfMonth(5).plusMonths(1));
        f.setStatut(statut);
        f.setAgence(agence(agenceCode));
        financements.save(f);
    }

    private void beneficiary(Client client, String nom, String rib, String banque, String type) {
        Beneficiaire b = new Beneficiaire();
        b.setClient(client);
        b.setNom(nom);
        b.setRib(rib);
        b.setBanque(banque);
        b.setType(type);
        b.setCreeLe(LocalDateTime.now().minusDays(30));
        beneficiaires.save(b);
    }
}
