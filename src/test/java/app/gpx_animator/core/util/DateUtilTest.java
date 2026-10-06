package app.gpx_animator.core.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateUtilTest {

    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");
    private static final long MAY_FIRST_MIDNIGHT_UTC = Instant.parse("2024-05-01T00:00:00Z").toEpochMilli();

    @Test
    void parseZonedDateTime() {
        final var expected = ZonedDateTime.of(2020, 12, 3, 12, 20, 59, 0,
                ZoneId.of("+2"));
        assertEquals(expected, DateUtil.parseZonedDateTime("2020:12:03 12:20:59 +02:00"));
        assertEquals(expected, DateUtil.parseZonedDateTime("2020:12:03 12:20:59 +0200"));
        assertEquals(expected, DateUtil.parseZonedDateTime("2020:12:03 12:20:59 +02"));
        assertEquals(expected, DateUtil.parseZonedDateTime("2020-12-03 12:20:59 +02:00"));
        assertEquals(expected, DateUtil.parseZonedDateTime("2020-12-03 12:20:59 +0200"));
        assertEquals(expected, DateUtil.parseZonedDateTime("2020-12-03 12:20:59 +02"));
    }

    @Test
    void parseZonedDateTimeException() {
        assertThrows(DateTimeParseException.class, () -> DateUtil.parseZonedDateTime("2020-12-03 12:20:59"));
    }

    @Test
    void parseDateTimeWithOffsetIsAbsolute() {
        assertEquals(MAY_FIRST_MIDNIGHT_UTC, DateUtil.parseDateTime("2024-05-01T08:00:00+08:00", ZoneOffset.UTC));
        assertEquals(MAY_FIRST_MIDNIGHT_UTC, DateUtil.parseDateTime("2024-05-01 08:00:00+08:00", ZoneOffset.UTC));
        assertEquals(MAY_FIRST_MIDNIGHT_UTC, DateUtil.parseDateTime("2024-05-01T00:00:00Z", HONG_KONG));
        assertEquals(MAY_FIRST_MIDNIGHT_UTC, DateUtil.parseDateTime("2024-05-01T08:00+08:00[Asia/Hong_Kong]", ZoneOffset.UTC));
    }

    @Test
    void parseDateTimeWithoutOffsetUsesDefaultZone() {
        assertEquals(MAY_FIRST_MIDNIGHT_UTC, DateUtil.parseDateTime("2024-05-01T08:00:00", HONG_KONG));
        assertEquals(MAY_FIRST_MIDNIGHT_UTC, DateUtil.parseDateTime("2024-05-01 08:00:00", HONG_KONG));
        assertEquals(MAY_FIRST_MIDNIGHT_UTC, DateUtil.parseDateTime("2024-05-01 08:00", HONG_KONG));
        assertEquals(MAY_FIRST_MIDNIGHT_UTC, DateUtil.parseDateTime(" 2024-05-01T00:00 ", ZoneOffset.UTC));
    }

    @Test
    void parseDateTimeException() {
        assertThrows(DateTimeParseException.class, () -> DateUtil.parseDateTime("01.05.2024 08:00", HONG_KONG));
        assertThrows(DateTimeParseException.class, () -> DateUtil.parseDateTime("2024-05-01", HONG_KONG));
        assertThrows(DateTimeParseException.class, () -> DateUtil.parseDateTime("yesterday", HONG_KONG));
    }

    @Test
    void convertBetweenEpochMillisAndLocalDateTime() {
        final var localDateTime = LocalDateTime.of(2024, 5, 1, 8, 0);
        final var epochMillis = DateUtil.toEpochMillis(localDateTime, HONG_KONG);

        assertEquals(MAY_FIRST_MIDNIGHT_UTC, epochMillis);
        assertEquals(localDateTime, DateUtil.toLocalDateTime(epochMillis, HONG_KONG));
        assertEquals(LocalDateTime.of(2024, 5, 1, 0, 0), DateUtil.toLocalDateTime(epochMillis, ZoneOffset.UTC));
    }

    @Test
    void toZoneIdOrDefault() {
        assertEquals(HONG_KONG, DateUtil.toZoneIdOrDefault("Asia/Hong_Kong"));
        assertEquals(ZoneId.systemDefault(), DateUtil.toZoneIdOrDefault(null));
        assertEquals(ZoneId.systemDefault(), DateUtil.toZoneIdOrDefault(" "));
        assertEquals(ZoneId.systemDefault(), DateUtil.toZoneIdOrDefault("Mars/Olympus_Mons"));
    }

}
