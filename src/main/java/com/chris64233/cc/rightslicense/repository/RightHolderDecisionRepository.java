package com.chris64233.cc.rightslicense.repository;

import com.chris64233.cc.rightslicense.domain.RightHolderDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RightHolderDecisionRepository extends JpaRepository<RightHolderDecision, Long> {

    Optional<RightHolderDecision> findByEventId(String eventId);

    List<RightHolderDecision> findByApplicationIdOrderByIdAsc(Long applicationId);

    boolean existsByApplicationIdAndHolderName(Long applicationId, String holderName);
}
