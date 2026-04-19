package com.cloud.guardrails.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class TimeUtils {

    private static final DateTimeFormatter ISO_UTC_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private TimeUtils() {
    }

    public static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static LocalDateTime fromInstantUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static String formatUtc(LocalDateTime value) {
        if (value == null) {
            return null;
        }

        return value.atOffset(ZoneOffset.UTC).format(ISO_UTC_FORMATTER);
    }
}
