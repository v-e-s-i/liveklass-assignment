package com.example.liveklass.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class TimeUtils {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private TimeUtils() {
    }

    public static OffsetDateTime toKst(Instant instant) {
        return instant.atZone(KST).toOffsetDateTime();
    }

    public static Instant startOfDayKst(LocalDate date) {
        return date.atStartOfDay(KST).toInstant();
    }

    public static Instant endOfDayKst(LocalDate date) {
        return date.atTime(23, 59, 59).atZone(KST).toInstant();
    }

    public static Instant startOfNextDayKst(LocalDate date) {
        return date.plusDays(1).atStartOfDay(KST).toInstant();
    }

    public static ZonedDateTime nowKst() {
        return ZonedDateTime.now(KST);
    }
}
