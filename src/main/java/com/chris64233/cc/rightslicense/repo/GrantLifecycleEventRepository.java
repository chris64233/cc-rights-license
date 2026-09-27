package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.GrantLifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GrantLifecycleEventRepository extends JpaRepository<GrantLifecycleEvent, Long> {

    Optional<GrantLifecycleEvent> findByEventNo(String eventNo);
}
