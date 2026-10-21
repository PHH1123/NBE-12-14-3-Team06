package com.back.nbe12142team06.domain.user.repository;

import com.back.nbe12142team06.domain.user.entity.EscortGradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EscortGradeHistoryRepository extends JpaRepository<EscortGradeHistory, Long> {
    List<EscortGradeHistory> findAllByEscortProfileUserId(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("""
        delete from EscortGradeHistory h
        where h.escortProfile.userId = :userId
    """)
    void deleteAllByEscortUserId(@Param("userId") Long userId);
}
