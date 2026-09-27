package com.chris64233.cc.rightslicense.web;

import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
import com.chris64233.cc.rightslicense.web.dto.Requests.SublicensePolicyRequest;

final class PolicyMapper {

    private PolicyMapper() {
    }

    static SublicensePolicy toDomain(SublicensePolicyRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.allowed())) {
            return SublicensePolicy.forbidden();
        }
        return new SublicensePolicy(true, request.maxDepth(),
                request.territories(), request.media(),
                request.startDate(), request.endDate());
    }
}
