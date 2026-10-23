package com.back.nbe12142team06.domain.ride.dto

import com.back.nbe12142team06.domain.ride.entity.Ride
import com.back.nbe12142team06.domain.ride.entity.RideDirection
import com.back.nbe12142team06.domain.ride.entity.RideSelect
import com.back.nbe12142team06.domain.ride.entity.RideStatus
import java.math.BigDecimal

data class RideResponse (
    val id: Long?,
    val direction: RideDirection,
    val status: RideStatus,
    val selected: RideSelect,
    val originLat: BigDecimal,
    val originLng: BigDecimal,
    val originAddr: String,
    val destLat: BigDecimal,
    val destLng: BigDecimal,
    val destAddr: String,
) {
    private data class Location(
        val lat: BigDecimal,
        val lng: BigDecimal,
        val address: String
    )

    companion object {
        @JvmStatic
        fun from(ride: Ride): RideResponse {
            val post = ride.post
            val pickup = Location(post.pickupLat, post.pickupLng, post.pickupAddress)
            val hospital = Location(post.hospitalLat, post.hospitalLat, post.hospitalAddress)

            val (origin, dest) = when (ride.direction) {
                RideDirection.TO_HOSPITAL -> pickup to hospital
                RideDirection.TO_HOME -> hospital to pickup
            }

            return RideResponse(
                id = ride.id,
                direction = ride.direction,
                status = ride.rideStatus,
                selected = ride.selected,
                originLat = origin.lat,
                originLng = origin.lng,
                originAddr = origin.address,
                destLat = dest.lat,
                destLng = dest.lng,
                destAddr = dest.address,
            )
        }
    }
}
