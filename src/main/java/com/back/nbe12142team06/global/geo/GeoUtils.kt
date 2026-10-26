package com.back.nbe12142team06.global.geo

import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    // 두 좌표 사이의 거리(미터). 하버사인 공식
    @JvmStatic
    fun distanceMeters(lat1: BigDecimal, lng1: BigDecimal, lat2: BigDecimal, lng2: BigDecimal): Double {
        val phi1 = Math.toRadians(lat1.toDouble())
        val phi2 = Math.toRadians(lat2.toDouble())
        val deltaPhi = phi2 - phi1
        val deltaLambda = Math.toRadians(lng2.toDouble() - lng1.toDouble())

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)

        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
