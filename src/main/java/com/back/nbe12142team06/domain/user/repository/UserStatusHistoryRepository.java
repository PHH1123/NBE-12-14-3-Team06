package com.back.nbe12142team06.domain.user.repository;

import com.back.nbe12142team06.domain.user.entity.UserStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, Long> {
    List<UserStatusHistory> findAllByUserId(Long userId);
}
