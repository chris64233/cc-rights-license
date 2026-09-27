package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.CascadeEventType;
import com.chris64233.cc.rightslicense.domain.CascadeTask;
import com.chris64233.cc.rightslicense.domain.CascadeTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CascadeTaskRepository extends JpaRepository<CascadeTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CascadeTask t where t.id = :id")
    Optional<CascadeTask> findByIdForUpdate(@Param("id") Long id);

    boolean existsByTargetGrantIdAndEventTypeAndSourceGrantId(Long targetGrantId,
                                                              CascadeEventType eventType,
                                                              Long sourceGrantId);

    List<CascadeTask> findByStatusOrderById(CascadeTaskStatus status);

    List<CascadeTask> findByTargetGrantIdOrderById(Long targetGrantId);
}
