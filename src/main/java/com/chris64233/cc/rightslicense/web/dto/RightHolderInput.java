package com.chris64233.cc.rightslicense.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RightHolderInput(
        @NotBlank String holderName,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal sharePercent) {
}
