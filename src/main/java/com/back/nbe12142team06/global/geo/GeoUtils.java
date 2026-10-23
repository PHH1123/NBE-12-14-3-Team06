package com.back.nbe12142team06.global.geo;

import java.math.BigDecimal;

public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private GeoUtils() {
    }

    // 두 좌표 사이의 거리(미터). 하버사인 공식
    public static double distanceMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = phi2 - phi1;
        double deltaLambda = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);

        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
