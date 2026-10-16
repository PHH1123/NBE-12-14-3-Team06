package com.back.nbe12142team06.domain.user.repository;

import com.back.nbe12142team06.domain.user.entity.EscortGradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EscortGradeHistoryRepository extends JpaRepository<EscortGradeHistory, Long> {
    List<EscortGradeHistory> findAllByEscortProfileUserId(Long userId);
}
