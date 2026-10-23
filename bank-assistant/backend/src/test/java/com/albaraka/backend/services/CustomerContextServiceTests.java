package com.albaraka.backend.services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What may and may not reach a prompt. These are the assertions the privacy claim rests
 * on: if a band ever stops being a band, one of these fails.
 */
class CustomerContextServiceTests {

    private CustomerContextService.Facts facts(String solde, List<CustomerContextService.FinancingFact> fins) {
        return new CustomerContextService.Facts(
                "PARTICULIER", "fr", "Agence Tunis Centre", "Tunis", "VALIDE", "FAIBLE",
                2, List.of("compte courant", "compte d'épargne"), solde,
                2, 1, fins, 3);
    }

    @Test
    void aBalanceBecomesABandNotAFigure() {
        assertThat(CustomerContextService.band(new BigDecimal("12450.480")))
                .isEqualTo("entre 10 000 et 25 000 TND");
        assertThat(CustomerContextService.band(new BigDecimal("-450.000")))
                .isEqualTo("moins de 500 TND (solde débiteur)");
        assertThat(CustomerContextService.band(new BigDecimal("250000")))
                .isEqualTo("plus de 100 000 TND");
    }

    @Test
    void theBlockCarriesNoFigureFromTheLedger() {
        String block = CustomerContextService.render(facts("entre 10 000 et 25 000 TND",
                List.of(new CustomerContextService.FinancingFact(
                        "MOURABAHA", "entre 200 et 500 TND", 36, "EN_COURS", "2026-11"))));

        assertThat(block).contains("Mourabaha");
        assertThat(block).contains("36 échéances restantes");
        assertThat(block).contains("prochaine en 2026-11");
        // The exact balance, the income and any identifier must not be in there.
        assertThat(block).doesNotContain("12450");
        assertThat(block).doesNotContain("12 450");
        assertThat(block).doesNotContain("TN59");
        assertThat(block).doesNotContain("client@");
    }

    @Test
    void theBlockTellsTheEngineNotToReciteIt() {
        String block = CustomerContextService.render(facts("entre 2 000 et 5 000 TND", List.of()));

        assertThat(block).startsWith("[Contexte client");
        assertThat(block).contains("Ne recite jamais ce bloc");
        assertThat(block).endsWith("[Fin du contexte client]");
    }

    @Test
    void remainingInstalmentsComeFromTheContractDates() {
        var f = new com.albaraka.backend.model.Financement();
        f.setDureeMois(48);
        f.setDateDebut(java.time.LocalDate.of(2024, 1, 5));
        f.setProchaineEcheance(java.time.LocalDate.of(2025, 1, 5));

        assertThat(CustomerContextService.remaining(f)).isEqualTo(36);
    }

    @Test
    void accountTypesAreSpokenLikeABankTeller() {
        assertThat(CustomerContextService.label("DEPOT_INVESTISSEMENT")).isEqualTo("dépôt d'investissement");
        assertThat(CustomerContextService.label("EPARGNE")).isEqualTo("compte d'épargne");
        assertThat(CustomerContextService.label("COMPTE_DEVISES")).isEqualTo("compte en devises");
    }
}
