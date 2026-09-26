package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.RightsDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RightsDecisionRepository extends JpaRepository<RightsDecision, Long> {

    Optional<RightsDecision> findByApplicationIdAndEventNumber(Long applicationId, String eventNumber);

    boolean existsByApplicationIdAndHolderId(Long applicationId, Long holderId);

    List<RightsDecision> findByApplicationIdOrderById(Long applicationId);

    @Query("select coalesce(sum(d.holder.sharePercent), 0) from RightsDecision d "
            + "where d.application.id = :applicationId and d.decision = :decision")
    BigDecimal sumSharesByApplicationIdAndDecision(@Param("applicationId") Long applicationId,
                                                   @Param("decision") DecisionValue decision);
}
