package com.chris64233.cc.rightslicense.repository;

import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LicenseGrantRepository extends JpaRepository<LicenseGrant, Long> {

    List<LicenseGrant> findByWorkIdOrderByStartDateAscIdAsc(Long workId);
}
