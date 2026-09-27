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

    @Query("select g from LicenseGrant g where g.work.id = :workId "
            + "and g.startDate <= :endDate and g.endDate >= :startDate "
            + "and g.type in :types and g.status = com.chris64233.cc.rightslicense.domain.GrantStatus.ACTIVE")
    List<LicenseGrant> findOverlapping(@Param("workId") Long workId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("types") Collection<LicenseType> types);

    @Query("select g from LicenseGrant g where g.work.id = :workId "
            + "and g.startDate <= :to and g.endDate >= :from order by g.startDate, g.id")
    List<LicenseGrant> findCalendarEntries(@Param("workId") Long workId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from LicenseGrant g where g.id = :id")
    Optional<LicenseGrant> findByIdForUpdate(@Param("id") Long id);

    Optional<LicenseGrant> findByGrantNo(String grantNo);

    /** 一次性初始化详情接口/序列化所需的全部懒加载集合与上级，避免游离对象懒加载异常 */
    @Query("select distinct g from LicenseGrant g "
            + "left join fetch g.parent "
            + "left join fetch g.territories "
            + "left join fetch g.media "
            + "left join fetch g.subTerritories "
            + "left join fetch g.subMedia "
            + "left join fetch g.chain "
            + "where g.grantNo = :grantNo")
    Optional<LicenseGrant> findDetailByGrantNo(@Param("grantNo") String grantNo);

    @Query("select distinct g from LicenseGrant g "
            + "left join fetch g.parent "
            + "left join fetch g.territories "
            + "left join fetch g.media "
            + "left join fetch g.subTerritories "
            + "left join fetch g.subMedia "
            + "left join fetch g.chain "
            + "where g.id = :id")
    Optional<LicenseGrant> findDetailById(@Param("id") Long id);

    /** 以悲观写锁按授权号加载，串行化"状态变化"与"新下级申请"两条路径 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from LicenseGrant g where g.grantNo = :grantNo")
    Optional<LicenseGrant> findByGrantNoForUpdate(@Param("grantNo") String grantNo);

    /** 直接下级（权利树） */
    List<LicenseGrant> findByParentIdOrderById(Long parentId);

    /** 一棵子树的全部节点（物化路径前缀匹配），按层级链排序 */
    @Query("select g from LicenseGrant g where g.ancestorPath like :prefix "
            + "order by length(g.ancestorPath), g.id")
    List<LicenseGrant> findSubtree(@Param("prefix") String ancestorPathPrefix);

    /** 一棵子树中当前处于某状态的节点（用于级联后核对，保证不遗漏） */
    @Query("select g from LicenseGrant g where g.ancestorPath like :prefix and g.status = :status")
    List<LicenseGrant> findSubtreeByStatus(@Param("prefix") String ancestorPathPrefix,
                                           @Param("status") GrantStatus status);

    /**
     * 某上级授权下、与其日期重叠且当前有效的下级授权（独占兄弟冲突检测）。
     * 不含被终止/暂停的下级，避免失效授权阻塞新申请。
     */
    @Query("select g from LicenseGrant g where g.parent.id = :parentId "
            + "and g.status = com.chris64233.cc.rightslicense.domain.GrantStatus.ACTIVE "
            + "and g.startDate <= :endDate and g.endDate >= :startDate")
    List<LicenseGrant> findActiveSiblingsOverlapping(@Param("parentId") Long parentId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    @Query("select count(g) from LicenseGrant g where g.ancestorPath like :prefix")
    long countSubtree(@Param("prefix") String ancestorPathPrefix);
}
