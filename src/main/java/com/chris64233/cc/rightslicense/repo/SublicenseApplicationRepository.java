package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SublicenseApplicationRepository extends JpaRepository<SublicenseApplication, Long> {

    Optional<SublicenseApplication> findBySublicenseNumber(String sublicenseNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SublicenseApplication a where a.id = :id")
    Optional<SublicenseApplication> findByIdForUpdate(@Param("id") Long id);
}
