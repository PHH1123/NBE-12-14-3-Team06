package com.back.nbe12142team06.domain.education.repository;

import com.back.nbe12142team06.domain.education.entity.WatchProgressLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchProgressLogRepository extends JpaRepository<WatchProgressLog, Long> {



    @Modifying(flushAutomatically = true)
    @Query("""
        delete from WatchProgressLog w
            where w.educationProgress.id in (
                select p.id from EducationProgress p where p.escortProfile.userId = :userId
                )
    """)
    void deleteAllByEscortUserId(@Param("userId") Long userId);
}
