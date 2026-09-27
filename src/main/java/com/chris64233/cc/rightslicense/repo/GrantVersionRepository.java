package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.GrantVersion;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GrantVersionRepository extends JpaRepository<GrantVersion, Long> {

    List<GrantVersion> findByGrantIdOrderByVersion(Long grantId);

    Optional<GrantVersion> findByGrantIdAndVersion(Long grantId, int version);
}
