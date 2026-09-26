package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LicenseGrantRepository extends JpaRepository<LicenseGrant, Long> {

    @Query("select g from LicenseGrant g where g.work.id = :workId "
            + "and g.startDate <= :endDate and g.endDate >= :startDate "
            + "and g.type in :types")
    List<LicenseGrant> findOverlapping(@Param("workId") Long workId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("types") Collection<LicenseType> types);

    @Query("select g from LicenseGrant g where g.work.id = :workId "
            + "and g.startDate <= :to and g.endDate >= :from order by g.startDate, g.id")
    List<LicenseGrant> findCalendarEntries(@Param("workId") Long workId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);
}
