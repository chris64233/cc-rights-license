package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.Work;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkRepository extends JpaRepository<Work, Long> {

    Optional<Work> findByCode(String code);

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Work w where w.id = :id")
    Optional<Work> findByIdForUpdate(@Param("id") Long id);
}
