package app.gpx_animator.core.data.gpx;

import app.gpx_animator.core.UserException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpxParserTest {

    @Test
    void readTrackTimeRange() throws UserException, URISyntaxException {
        final var timeRange = GpxParser.readTrackTimeRange(resourceFile("/gpx/comment.gpx"));

        assertEquals(Optional.of(new GpxParser.TimeRange(
                Instant.parse("2019-10-22T14:36:00Z").toEpochMilli(),
                Instant.parse("2019-10-22T14:36:07Z").toEpochMilli())), timeRange);
    }

    @Test
    void readTrackTimeRangeWithoutTimestamps() throws UserException, URISyntaxException {
        assertEquals(Optional.empty(), GpxParser.readTrackTimeRange(resourceFile("/gpx/notime.gpx")));
    }

    @Test
    void readTrackTimeRangeOfMissingFile() {
        assertThrows(UserException.class, () -> GpxParser.readTrackTimeRange(new File("does-not-exist.gpx")));
    }

    private File resourceFile(final String name) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(getClass().getResource(name)).toURI()).toFile();
    }

}
