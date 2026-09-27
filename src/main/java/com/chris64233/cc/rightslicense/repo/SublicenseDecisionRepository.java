package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.SublicenseDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SublicenseDecisionRepository extends JpaRepository<SublicenseDecision, Long> {

    Optional<SublicenseDecision> findByApplicationIdAndEventNumber(Long applicationId, String eventNumber);

    boolean existsByApplicationIdAndApplicant(Long applicationId, String applicant);

    List<SublicenseDecision> findByApplicationIdOrderById(Long applicationId);
}
