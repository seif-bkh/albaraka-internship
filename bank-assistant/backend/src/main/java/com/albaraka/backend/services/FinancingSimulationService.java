package com.albaraka.backend.services;

import com.albaraka.backend.dto.FinancingSimulationRequest;
import com.albaraka.backend.dto.FinancingSimulationResponse;
import com.albaraka.backend.model.BusinessFact;
import com.albaraka.backend.repository.BusinessFactRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Portal financing simulator (P0, AGENTS.md §10).
 *
 * <p>Governance-first: the profit rate is <b>never hardcoded</b>. It comes from
 * an active governed fact ({@code domain=financement, fact_type=rate,
 * topic=<product>}). A product appears in the simulator only while such a fact
 * exists and parses to a percentage; otherwise the client sees an honest
 * "not available yet" state (refuse rather than guess).
 *
 * <p>Math — linear (non-compounding) Islamic margin, single source of truth:
 * <pre>
 *   totalProfit = amount × rate × (months / 12)
 *   monthly     = (amount + totalProfit) / months
 * </pre>
 */
@Service
public class FinancingSimulationService {

    private static final String RATE_DOMAIN = "financement";
    private static final String RATE_FACT_TYPE = "rate";
    private static final String CURRENCY = "TND";

    static final BigDecimal MIN_AMOUNT = new BigDecimal("500");
    static final BigDecimal MAX_AMOUNT = new BigDecimal("5000000");
    static final int MIN_MONTHS = 1;
    static final int MAX_MONTHS = 180;

    /** Accepts "5,99 %", "5.99%", "5 %" — the first numeric percentage in the fact value. */
    private static final java.util.regex.Pattern RATE_PATTERN =
            java.util.regex.Pattern.compile("(\\d{1,2}(?:[.,]\\d{1,4})?)\\s*%");

    private final BusinessFactRepository facts;

    public FinancingSimulationService(BusinessFactRepository facts) {
        this.facts = facts;
    }

    /** Product display names that currently have an active, parseable rate fact. */
    public List<String> simulationProducts() {
        return facts.findByDomainAndFactTypeAndActiveTrueOrderByKeyAsc(RATE_DOMAIN, RATE_FACT_TYPE)
                .stream()
                .filter(f -> parseRate(f.getValue()) != null)
                .map(BusinessFact::getTopic)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public FinancingSimulationResponse simulate(FinancingSimulationRequest req) {
        BigDecimal amount = req.amount();
        int months = req.months();
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Le montant doit être compris entre 500 et 5 000 000 TND.");
        }
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new IllegalArgumentException("La durée doit être comprise entre 1 et 180 mois.");
        }

        Double rate = findRate(req.product());
        if (rate == null) {
            return FinancingSimulationResponse.unavailable(req.product(), amount, months);
        }

        BigDecimal years = BigDecimal.valueOf(months)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal totalProfit = amount
                .multiply(BigDecimal.valueOf(rate))
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .multiply(years)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthly = amount.add(totalProfit)
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

        return new FinancingSimulationResponse(
                req.product(), true, rate, amount, months,
                monthly, totalProfit, amount.add(totalProfit), CURRENCY);
    }

    /** First active rate fact whose topic matches the product (case-insensitive). */
    private Double findRate(String product) {
        if (product == null || product.isBlank()) {
            return null;
        }
        for (BusinessFact f : facts.findByDomainAndFactTypeAndActiveTrueOrderByKeyAsc(RATE_DOMAIN, RATE_FACT_TYPE)) {
            if (f.getTopic() != null && f.getTopic().equalsIgnoreCase(product.trim())) {
                Double r = parseRate(f.getValue());
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /** Extracts the annual rate in percent from a human-readable fact value. */
    static Double parseRate(String value) {
        if (value == null) {
            return null;
        }
        java.util.regex.Matcher m = RATE_PATTERN.matcher(value);
        if (m.find()) {
            try {
                double r = Double.parseDouble(m.group(1).replace(',', '.'));
                if (r > 0 && r < 100) {
                    return r;
                }
            } catch (NumberFormatException ignored) {
                // Not a plain-number rate (e.g. "TMM + 3") — the simulator needs a fixed figure.
            }
        }
        return null;
    }
}
