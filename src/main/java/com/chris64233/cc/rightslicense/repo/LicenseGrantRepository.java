package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LicenseGrantRepository extends JpaRepository<LicenseGrant, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from LicenseGrant g where g.id = :id")
    Optional<LicenseGrant> findByIdForUpdate(@Param("id") Long id);

    /** 只取根授权 id，不把授权实体装入持久化上下文（避免加锁等待后读到缓存旧值）。 */
    @Query("select g.rootGrantId from LicenseGrant g where g.id = :id")
    Optional<Long> findRootIdById(@Param("id") Long id);

    Optional<LicenseGrant> findByApplicationId(Long applicationId);

    Optional<LicenseGrant> findBySublicenseApplicationId(Long sublicenseApplicationId);

    List<LicenseGrant> findByParentGrantIdOrderById(Long parentGrantId);

    @Query("select g from LicenseGrant g where g.work.id = :workId "
            + "and g.status = :status "
            + "and g.startDate <= :endDate and g.endDate >= :startDate "
            + "and g.type in :types")
    List<LicenseGrant> findOverlapping(@Param("workId") Long workId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("types") Collection<LicenseType> types,
                                       @Param("status") GrantStatus status);

    @Query("select g from LicenseGrant g where g.work.id = :workId "
            + "and g.startDate <= :to and g.endDate >= :from order by g.startDate, g.id")
    List<LicenseGrant> findCalendarEntries(@Param("workId") Long workId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    @Query("select g from LicenseGrant g where g.status in :statuses and g.endDate < :today "
            + "order by g.id")
    List<LicenseGrant> findExpired(@Param("statuses") Collection<GrantStatus> statuses,
                                   @Param("today") LocalDate today);

    /** 某授权及其全部下级（按层级、id 排序，父先于子）。 */
    @Query("select g from LicenseGrant g where g.chainPath like concat(:chainPrefix, '%') "
            + "order by g.depth, g.id")
    List<LicenseGrant> findSubtree(@Param("chainPrefix") String chainPrefix);
}
