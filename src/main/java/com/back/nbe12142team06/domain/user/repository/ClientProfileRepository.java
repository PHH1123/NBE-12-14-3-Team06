package com.back.nbe12142team06.domain.user.repository;

import com.back.nbe12142team06.domain.user.entity.ClientProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientProfileRepository extends JpaRepository<ClientProfile, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from ClientProfile p where p.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
