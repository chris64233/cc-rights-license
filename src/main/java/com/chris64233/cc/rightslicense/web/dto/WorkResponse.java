package com.chris64233.cc.rightslicense.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record WorkResponse(
        Long id,
        String workCode,
        String title,
        List<RightHolderInput> rightHolders,
        BigDecimal totalSharePercent) {
}
