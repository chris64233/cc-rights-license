package com.chris64233.cc.rightslicense.repo;

import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SublicenseApplicationRepository extends JpaRepository<SublicenseApplication, Long> {

    Optional<SublicenseApplication> findBySublicenseNo(String sublicenseNo);

    /** 转授权号幂等：并发重复提交时通过悲观锁串行化，唯一约束兜底 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SublicenseApplication a where a.sublicenseNo = :no")
    Optional<SublicenseApplication> findBySublicenseNoForUpdate(@Param("no") String sublicenseNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SublicenseApplication a where a.id = :id")
    Optional<SublicenseApplication> findByIdForUpdate(@Param("id") Long id);

    List<SublicenseApplication> findByParentGrantIdOrderById(Long parentGrantId);

    @org.springframework.data.jpa.repository.Query("select distinct a from SublicenseApplication a "
            + "left join fetch a.parentGrant "
            + "left join fetch a.childGrant "
            + "left join fetch a.territories "
            + "left join fetch a.media "
            + "left join fetch a.subTerritories "
            + "left join fetch a.subMedia "
            + "where a.id = :id")
    java.util.Optional<SublicenseApplication> findDetailById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select distinct a from SublicenseApplication a "
            + "left join fetch a.parentGrant "
            + "left join fetch a.childGrant "
            + "left join fetch a.territories "
            + "left join fetch a.media "
            + "left join fetch a.subTerritories "
            + "left join fetch a.subMedia "
            + "where a.sublicenseNo = :no")
    java.util.Optional<SublicenseApplication> findDetailByNo(@org.springframework.data.repository.query.Param("no") String sublicenseNo);
}
