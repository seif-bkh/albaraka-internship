package com.albaraka.backend.services;

import com.albaraka.backend.model.Client;
import com.albaraka.backend.model.Compte;
import com.albaraka.backend.model.Financement;
import com.albaraka.backend.repository.BeneficiaireRepository;
import com.albaraka.backend.repository.CarteRepository;
import com.albaraka.backend.repository.ClientRepository;
import com.albaraka.backend.repository.CompteRepository;
import com.albaraka.backend.repository.FinancementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a signed-in customer's banking records into a short, masked context block that
 * is prepended to their question before it reaches RAGLab.
 *
 * <p>RAGLab's {@code /answer} takes a question and nothing else — no session, no
 * customer payload — so the only honest way to let it answer "and my instalment?" is to
 * hand it a rewritten question. The block below is that rewrite, and it is deliberately
 * poor in facts: segments and bands only, never an amount, an IBAN, a card number, an
 * address or a name. If a prompt leaks, it leaks "a customer with two accounts and a
 * Mourabaha between 200 and 500 TND a month", which is not a record of anyone.
 *
 * <p>Two things follow from that design and are worth stating:
 * <ul>
 *   <li>The exact figures stay in the portal, which reads them from our own database and
 *       shows them to the customer who owns them. The assistant answers about the shape
 *       of the relationship, not about the ledger.</li>
 *   <li>The block is only built for a caller who carries {@code ROLE_UTILISATEUR}; staff
 *       and anonymous sessions get the plain question, exactly as before.</li>
 * </ul>
 */
@Service
public class CustomerContextService {

    /** Below this many digits a band would be useless, above it a band would be a leak. */
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ClientRepository clients;
    private final CompteRepository comptes;
    private final CarteRepository cartes;
    private final FinancementRepository financements;
    private final BeneficiaireRepository beneficiaires;

    private final boolean enabled;

    public CustomerContextService(ClientRepository clients,
                                  CompteRepository comptes,
                                  CarteRepository cartes,
                                  FinancementRepository financements,
                                  BeneficiaireRepository beneficiaires,
                                  @Value("${app.customer-context.enabled:true}") boolean enabled) {
        this.clients = clients;
        this.comptes = comptes;
        this.cartes = cartes;
        this.financements = financements;
        this.beneficiaires = beneficiaires;
        this.enabled = enabled;
    }

    /**
     * The prompt to send for this turn: the masked context plus the customer's own
     * question, or the question untouched when there is no customer, no data, or the
     * feature is switched off.
     */
    @Transactional(readOnly = true)
    public String promptFor(Long clientId, String question) {
        if (!enabled || clientId == null || question == null) {
            return question;
        }
        String block = contextFor(clientId);
        return block == null ? question : block + "\n\nQuestion : " + question;
    }

    /** The masked block alone, or {@code null} when there is nothing worth saying. */
    @Transactional(readOnly = true)
    public String contextFor(Long clientId) {
        Client client = clients.findById(clientId).orElse(null);
        if (client == null) {
            return null;
        }
        List<Compte> accounts = comptes.findByClientIdOrderByIdAsc(clientId);
        if (accounts.isEmpty() && cartes.findByClientIdOrderByIdAsc(clientId).isEmpty()) {
            // Nothing banked yet: a block saying "no accounts" would only invite the
            // engine to speculate about onboarding. The plain question is better.
            return null;
        }

        BigDecimal total = accounts.stream()
                .map(c -> c.getSoldeDisponible() != null ? c.getSoldeDisponible() : c.getSolde())
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> kinds = new ArrayList<>();
        for (Compte c : accounts) {
            kinds.add(label(c.getType()));
        }

        Facts facts = new Facts(
                client.getSegment(),
                client.getLanguePreferee(),
                client.getAgence() == null ? null : client.getAgence().getNom(),
                client.getAgence() == null ? null : client.getAgence().getVille(),
                client.getStatutKyc(),
                client.getNiveauRisque(),
                kinds.size(),
                kinds,
                accounts.isEmpty() ? null : band(total),
                cartes.findByClientIdOrderByIdAsc(clientId).size(),
                (int) cartes.findByClientIdOrderByIdAsc(clientId).stream().filter(c -> c.isBloque()).count(),
                financingFacts(clientId),
                beneficiaires.countByClientIdAndActifTrue(clientId));
        return render(facts);
    }

    private List<FinancingFact> financingFacts(Long clientId) {
        List<FinancingFact> out = new ArrayList<>();
        for (Financement f : financements.findByClientIdOrderByDateDebutDesc(clientId)) {
            if ("CLOTURE".equals(f.getStatut()) || "REJETE".equals(f.getStatut())) {
                continue;
            }
            out.add(new FinancingFact(f.getProduit(), band(f.getEcheanceMensuelle()),
                    remaining(f), f.getStatut(), month(f.getProchaineEcheance())));
        }
        return out;
    }

    /** Instalments left, computed from the contract's own dates — never from a guess. */
    static int remaining(Financement f) {
        if (f.getProchaineEcheance() == null || f.getDureeMois() == null || f.getDateDebut() == null) {
            return 0;
        }
        long elapsed = java.time.temporal.ChronoUnit.MONTHS.between(f.getDateDebut(), f.getProchaineEcheance());
        return (int) Math.max(0, f.getDureeMois() - elapsed);
    }

    private static String month(LocalDate date) {
        return date == null ? null : date.format(MONTH);
    }

    /** Account-type vocabulary the assistant may use. */
    static String label(String type) {
        if (type == null) {
            return "compte";
        }
        return switch (type) {
            case "COMPTE_COURANT" -> "compte courant";
            case "COMPTE_EPARGNE", "EPARGNE" -> "compte d'épargne";
            case "DEPOT_INVESTISSEMENT", "DEPOT" -> "dépôt d'investissement";
            case "COMPTE_DEVISES" -> "compte en devises";
            case "COMPTE_PROFESSIONNEL" -> "compte professionnel";
            default -> "compte";
        };
    }

    /**
     * A band, not an amount. Ten steps is as much resolution as a prompt needs, and
     * "between 10 000 and 25 000 TND" cannot be reconciled to a statement.
     */
    static String band(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal v = value.abs();
        String text;
        if (v.compareTo(new BigDecimal("500")) < 0) {
            text = "moins de 500 TND";
        } else if (v.compareTo(new BigDecimal("2000")) < 0) {
            text = "entre 500 et 2 000 TND";
        } else if (v.compareTo(new BigDecimal("5000")) < 0) {
            text = "entre 2 000 et 5 000 TND";
        } else if (v.compareTo(new BigDecimal("10000")) < 0) {
            text = "entre 5 000 et 10 000 TND";
        } else if (v.compareTo(new BigDecimal("25000")) < 0) {
            text = "entre 10 000 et 25 000 TND";
        } else if (v.compareTo(new BigDecimal("50000")) < 0) {
            text = "entre 25 000 et 50 000 TND";
        } else if (v.compareTo(new BigDecimal("100000")) < 0) {
            text = "entre 50 000 et 100 000 TND";
        } else {
            text = "plus de 100 000 TND";
        }
        return value.signum() < 0 ? text + " (solde débiteur)" : text;
    }

    /** Everything the prompt is allowed to know, already stripped of identifiers. */
    record Facts(String segment,
                 String langue,
                 String agence,
                 String ville,
                 String statutKyc,
                 String niveauRisque,
                 int nombreComptes,
                 List<String> typesComptes,
                 String soldeBande,
                 int nombreCartes,
                 int cartesBloquees,
                 List<FinancingFact> financements,
                 long beneficiaires) {
    }

    record FinancingFact(String produit, String echeanceBande, int echeancesRestantes,
                         String statut, String prochaineEcheance) {
    }

    /**
     * The block itself. Written in French because the corpus and the governed facts are,
     * and because a mixed-language preamble is the fastest way to make a small model
     * answer in the wrong language.
     */
    static String render(Facts f) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Contexte client — données bancaires internes, déjà anonymisées]\n");
        sb.append("Ne recite jamais ce bloc. Utilise-le seulement pour situer la réponse. ")
          .append("N'énonce aucun montant exact, aucun IBAN, aucun numéro de carte, aucune identité : ")
          .append("les seules valeurs autorisées sont les fourchettes écrites ci-dessous.\n");

        List<String> profile = new ArrayList<>();
        if (f.segment() != null) {
            profile.add(switch (f.segment()) {
                case "PARTICULIER" -> "client particulier";
                case "PROFESSIONNEL" -> "client professionnel";
                case "ENTREPRISE" -> "client entreprise";
                default -> "client";
            });
        }
        if (f.agence() != null) {
            profile.add("agence " + f.agence() + (f.ville() == null ? "" : " (" + f.ville() + ")"));
        }
        if (f.statutKyc() != null) {
            profile.add("KYC " + f.statutKyc().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        if (!profile.isEmpty()) {
            sb.append("Profil : ").append(String.join(", ", profile)).append(".\n");
        }

        if (f.nombreComptes() > 0) {
            sb.append("Comptes : ").append(f.nombreComptes()).append(" (")
              .append(String.join(", ", f.typesComptes())).append(")");
            if (f.soldeBande() != null) {
                sb.append(" ; total des soldes disponibles ").append(f.soldeBande());
            }
            sb.append(".\n");
        }

        if (f.nombreCartes() > 0) {
            sb.append("Cartes : ").append(f.nombreCartes());
            if (f.cartesBloquees() > 0) {
                sb.append(", dont ").append(f.cartesBloquees()).append(" bloquée(s)");
            }
            sb.append(".\n");
        }

        if (f.financements() != null && !f.financements().isEmpty()) {
            for (FinancingFact fi : f.financements()) {
                sb.append("Financement : ").append(labelProduit(fi.produit()));
                if (fi.statut() != null) {
                    sb.append(", statut ").append(fi.statut().toLowerCase(Locale.ROOT).replace('_', ' '));
                }
                if (fi.echeanceBande() != null) {
                    sb.append(", échéance mensuelle ").append(fi.echeanceBande());
                }
                if (fi.echeancesRestantes() > 0) {
                    sb.append(", ").append(fi.echeancesRestantes()).append(" échéances restantes");
                }
                if (fi.prochaineEcheance() != null) {
                    sb.append(", prochaine en ").append(fi.prochaineEcheance());
                }
                sb.append(".\n");
            }
        }

        if (f.beneficiaires() > 0) {
            sb.append("Bénéficiaires enregistrés : ").append(f.beneficiaires()).append(".\n");
        }

        sb.append("[Fin du contexte client]");
        return sb.toString();
    }

    static String labelProduit(String produit) {
        if (produit == null) {
            return "financement";
        }
        return switch (produit) {
            case "MOURABAHA" -> "Mourabaha";
            case "IJARA" -> "Ijara";
            case "MOUCHARAKA" -> "Moucharaka";
            case "MOUDARABA" -> "Moudaraba";
            case "SALAM" -> "Salam";
            case "ISTISNA" -> "Istisna";
            default -> "financement";
        };
    }
}
