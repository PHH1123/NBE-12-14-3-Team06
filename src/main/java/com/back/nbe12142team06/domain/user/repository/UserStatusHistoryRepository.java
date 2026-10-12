package com.back.nbe12142team06.domain.user.repository;

import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.entity.UserStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, Long> {
    List<UserStatusHistory> findAllByUserId(Long userId);

    Optional<UserStatusHistory> findTopByUserOrderByIdDesc(User user);
}
