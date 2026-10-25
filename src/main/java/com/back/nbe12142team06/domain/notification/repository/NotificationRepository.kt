package com.back.nbe12142team06.domain.notification.repository

import com.back.nbe12142team06.domain.notification.entity.Notification
import com.back.nbe12142team06.domain.user.entity.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationRepository : JpaRepository<Notification, Long> {
    @Query(
        "select n from Notification n " +
                "join fetch n.receiver r " +
                "where r.id=:receiverId and n.deletedAt is null"
    )
    fun findByReceiverId(
        @Param("receiverId") receiverId: Long,
        pageable: Pageable
    ): Page<Notification>

    fun findByIdAndDeletedAtIsNull(id: Long): Notification?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update Notification n set n.isRead=true " +
                "where n.receiver.id=:receiverId and n.deletedAt is null"
    )
    fun readAllByUserId(@Param("receiverId")receiverId: Long) : Int
}
