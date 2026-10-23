package com.albaraka.backend.services;

import com.albaraka.backend.dto.FinancingSimulationRequest;
import com.albaraka.backend.dto.FinancingSimulationResponse;
import com.albaraka.backend.dto.BusinessFactRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Portal financing simulator: the rate is governed data (business_facts),
 * the math is the linear Islamic margin, and a missing rate yields an honest
 * "not available" state instead of a guessed number (AGENTS.md §10).
 */
@SpringBootTest
@Transactional
class FinancingSimulationServiceTests {

    @Autowired FinancingSimulationService simulation;
    @Autowired GovernedFactsService facts;

    private void putRate(String key, String topic, String value) {
        facts.create(new BusinessFactRequest("financement", key, value, "rate", topic, "fr",
                "grille 2026", null, "test"), "tester");
    }

    @Test
    void productListOnlyContainsProductsWithAnActiveParseableRate() {
        putRate("rate_sayarat", "Sayarat Al Baraka", "5,99 %");
        putRate("rate_mourabaha_tmm", "Mourabaha", "TMM + 3"); // not a fixed number
        facts.setActive(idOf("rate_mourabaha_tmm"), false, "test", "tester");
        putRate("rate_mourabaha", "Mourabaha", "6,5 %");

        List<String> products = simulation.simulationProducts();
        assertThat(products).containsExactlyInAnyOrder("Mourabaha", "Sayarat Al Baraka");
    }

    @Test
    void simulateAppliesTheLinearIslamicMargin() {
        putRate("rate_sayarat", "Sayarat Al Baraka", "5,99 %");

        FinancingSimulationResponse r = simulation.simulate(
                new FinancingSimulationRequest("Sayarat Al Baraka", new BigDecimal("120000"), 60));

        // 120000 × 5.99% × (60/12) = 35940.00 ; (120000 + 35940) / 60 = 2599.00
        assertThat(r.rateAvailable()).isTrue();
        assertThat(r.ratePercent()).isEqualTo(5.99);
        assertThat(r.totalProfit()).isEqualByComparingTo("35940.00");
        assertThat(r.monthlyPayment()).isEqualByComparingTo("2599.00");
        assertThat(r.totalRepayment()).isEqualByComparingTo("155940.00");
        assertThat(r.currency()).isEqualTo("TND");
    }

    @Test
    void missingRateYieldsAnHonestUnavailableState() {
        FinancingSimulationResponse r = simulation.simulate(
                new FinancingSimulationRequest("Ijara", new BigDecimal("120000"), 60));

        assertThat(r.rateAvailable()).isFalse();
        assertThat(r.ratePercent()).isNull();
        assertThat(r.monthlyPayment()).isNull();
        assertThat(r.totalProfit()).isNull();
    }

    private Long idOf(String key) {
        return facts.list("financement").stream()
                .filter(f -> f.getKey().equals(key)).findFirst().orElseThrow().getId();
    }

    @Test
    void deactivatedRateDisappearsFromTheSimulator() {
        putRate("rate_sayarat", "Sayarat Al Baraka", "5,99 %");
        facts.setActive(idOf("rate_sayarat"), false, "grille révisée", "tester");

        assertThat(simulation.simulationProducts()).doesNotContain("Sayarat Al Baraka");
        assertThat(simulation.simulate(
                new FinancingSimulationRequest("Sayarat Al Baraka", new BigDecimal("120000"), 60))
                .rateAvailable()).isFalse();
    }

    @Test
    void rejectsAmountsOutsideTheBounds() {
        assertThatThrownBy(() -> simulation.simulate(
                new FinancingSimulationRequest("X", new BigDecimal("100"), 12)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> simulation.simulate(
                new FinancingSimulationRequest("X", new BigDecimal("9999999"), 12)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> simulation.simulate(
                new FinancingSimulationRequest("X", new BigDecimal("120000"), 200)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseRateAcceptsFrenchAndDotFormatsAndRejectsNonNumeric() {
        assertThat(FinancingSimulationService.parseRate("Marge de profit : 5,99 %")).isEqualTo(5.99);
        assertThat(FinancingSimulationService.parseRate("6.5% annuel")).isEqualTo(6.5);
        assertThat(FinancingSimulationService.parseRate("TMM + 3")).isNull();
        assertThat(FinancingSimulationService.parseRate("à discuter avec l'agence")).isNull();
        assertThat(FinancingSimulationService.parseRate(null)).isNull();
    }
}
