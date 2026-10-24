package com.back.nbe12142team06.domain.ride.repository

import com.back.nbe12142team06.domain.ride.entity.Ride
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RideRepository : JpaRepository<Ride, Long> {
    @Query("select r from Ride r join fetch r.post p where p.id=:postId")
    fun findAllByPostId(@Param("postId") postId: Long): List<Ride>
}
