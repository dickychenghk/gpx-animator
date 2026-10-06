/*
 *  Copyright Contributors to the GPX Animator project.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package app.gpx_animator.core.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class DateUtil {

    private static final DateTimeFormatter[] ZONED_DATE_TIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss xxx"),
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss xx"),
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss x"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xx"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss x")
    };

    private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendPattern("['T'][' ']")
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .optionalStart().appendOffsetId().optionalEnd()
            .optionalStart().appendLiteral('[').parseCaseSensitive().appendZoneRegionId().appendLiteral(']').optionalEnd()
            .toFormatter(Locale.ROOT);

    private DateUtil() throws InstantiationException {
        throw new InstantiationException("Utility classes can't be instantiated!");
    }

    public static @NonNull ZonedDateTime parseZonedDateTime(@NonNull final String text) {
        DateTimeParseException lastException = null;
        for (final DateTimeFormatter dateTimeFormatter : ZONED_DATE_TIME_FORMATTERS) {
            try {
                return ZonedDateTime.parse(text, dateTimeFormatter);
            } catch (final DateTimeParseException e) {
                lastException = e;
            }
        }
        throw lastException != null ? lastException
                : new DateTimeParseException("Can't parse ZonedDateTime object!", text, -1, null);
    }

    /**
     * Parses an ISO-8601 like date and time to epoch milliseconds.
     *
     * <p>Values with an offset or zone (e.g. {@code 2024-05-01T08:00:00+08:00}) are absolute. Values without
     * (e.g. {@code 2024-05-01T08:00:00} or {@code 2024-05-01 08:00}) are interpreted in the given default zone.</p>
     *
     * @param text the date and time to parse
     * @param defaultZone the zone used when the text does not contain an offset or zone
     * @return the epoch milliseconds
     * @throws DateTimeParseException if the text can't be parsed
     */
    public static long parseDateTime(@NonNull final String text, @NonNull final ZoneId defaultZone) {
        final var parsed = DATE_TIME_FORMATTER.parseBest(text.trim(), ZonedDateTime::from, LocalDateTime::from);
        final var zonedDateTime = parsed instanceof final LocalDateTime localDateTime
                ? localDateTime.atZone(defaultZone)
                : (ZonedDateTime) parsed;
        return zonedDateTime.toInstant().toEpochMilli();
    }

    public static long toEpochMillis(@NonNull final LocalDateTime localDateTime, @NonNull final ZoneId zone) {
        return localDateTime.atZone(zone).toInstant().toEpochMilli();
    }

    public static @NonNull LocalDateTime toLocalDateTime(final long epochMillis, @NonNull final ZoneId zone) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone);
    }

    /**
     * Resolves a time zone ID, falling back to the system default for missing or unknown IDs.
     *
     * @param zoneId the time zone ID, may be {@code null}
     * @return the matching time zone or the system default
     */
    public static @NonNull ZoneId toZoneIdOrDefault(@Nullable final String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(zoneId.trim());
        } catch (final DateTimeException ignored) {
            return ZoneId.systemDefault();
        }
    }
}
