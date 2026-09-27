package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.GrantPropagation;
import com.chris64233.cc.rightslicense.domain.PropagationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GrantPropagationRepository extends JpaRepository<GrantPropagation, Long> {

    List<GrantPropagation> findByStatusOrderById(PropagationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GrantPropagation p where p.id = :id")
    Optional<GrantPropagation> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(PropagationStatus status);
}
