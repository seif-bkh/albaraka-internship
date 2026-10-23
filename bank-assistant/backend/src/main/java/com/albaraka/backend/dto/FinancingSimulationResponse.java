package com.albaraka.backend.dto;

import java.math.BigDecimal;

/**
 * Result of a portal financing simulation.
 *
 * <p>When {@code rateAvailable} is false, the monetary fields are null: no
 * governed rate fact exists for the product yet, and the UI shows an honest
 * "not available" state instead of a guessed number (governance rule,
 * AGENTS.md §10).
 */
public record FinancingSimulationResponse(
        String product,
        boolean rateAvailable,
        Double ratePercent,
        BigDecimal amount,
        Integer months,
        BigDecimal monthlyPayment,
        BigDecimal totalProfit,
        BigDecimal totalRepayment,
        String currency) {

    public static FinancingSimulationResponse unavailable(String product, BigDecimal amount, int months) {
        return new FinancingSimulationResponse(product, false, null,
                amount, months, null, null, null, "TND");
    }
}
