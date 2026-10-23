package com.back.nbe12142team06.domain.ride.entity

enum class RideSelect(
    val description: String
) {
    WALK("도보"),
    BUS("버스"),
    TAXI("택시"),
    OWN_CAR("자차");
}
