package com.albaraka.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Portal financing-simulator input. The product must have a governed rate. */
public record FinancingSimulationRequest(
        @NotBlank @Size(max = 120) String product,
        @NotNull @DecimalMin(value = "500.0") @DecimalMax(value = "5000000.0") BigDecimal amount,
        @NotNull @Min(1) @Max(180) Integer months) {
}
