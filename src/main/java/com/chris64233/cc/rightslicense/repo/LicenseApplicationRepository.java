package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LicenseApplicationRepository extends JpaRepository<LicenseApplication, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LicenseApplication a where a.id = :id")
    Optional<LicenseApplication> findByIdForUpdate(@Param("id") Long id);

    List<LicenseApplication> findByWorkIdOrderById(Long workId);
}
