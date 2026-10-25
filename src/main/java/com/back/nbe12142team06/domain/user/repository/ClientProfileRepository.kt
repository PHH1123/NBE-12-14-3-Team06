package com.back.nbe12142team06.domain.user.repository

import com.back.nbe12142team06.domain.user.entity.ClientProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ClientProfileRepository : JpaRepository<ClientProfile, Long> {

    // 회원 탈퇴 시 의뢰인 프로필 삭제
    @Modifying(flushAutomatically = true)
    @Query("delete from ClientProfile p where p.user.id = :userId")
    fun deleteByUserId(@Param("userId") userId: Long)
}