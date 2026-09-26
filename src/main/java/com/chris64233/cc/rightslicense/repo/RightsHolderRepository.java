package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.RightsHolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface RightsHolderRepository extends JpaRepository<RightsHolder, Long> {

    List<RightsHolder> findByWorkIdOrderById(Long workId);

    boolean existsByWorkIdAndName(Long workId, String name);

    @Query("select coalesce(sum(h.sharePercent), 0) from RightsHolder h where h.work.id = :workId")
    BigDecimal sumSharesByWorkId(@Param("workId") Long workId);
}
