package app.gpx_animator.core.renderer;

import app.gpx_animator.core.UserException;
import app.gpx_animator.core.configuration.Configuration;
import app.gpx_animator.core.configuration.TrackConfiguration;
import app.gpx_animator.ui.UIMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RendererTimeRangeTest {

    // the track points of comment.gpx were recorded at 14:36:00, :01, :02, :04, :05 and :07 UTC;
    // rendered with 1 frame per second in real time, every second of the used part results in one frame
    private static final long FROM = Instant.parse("2019-10-22T14:36:01Z").toEpochMilli();
    private static final long TO = Instant.parse("2019-10-22T14:36:05Z").toEpochMilli();
    private static final long NEXT_YEAR = Instant.parse("2020-10-22T14:36:00Z").toEpochMilli();

    @TempDir
    private Path outputDirectory;

    @BeforeAll
    static void useCommandLineMode() {
        UIMode.setMode(UIMode.CLI);
    }

    @Test
    void rendersTheWholeTrackWithoutTimeRange() throws Exception {
        assertEquals(7, render(trackConfiguration(null, null)));
    }

    @Test
    void rendersOnlyThePartInsideTheTimeRange() throws Exception {
        assertEquals(4, render(trackConfiguration(FROM, TO)));
    }

    @Test
    void usesTheOverlapIfTheTimeRangeStartsBeforeTheTrack() throws Exception {
        final var midnight = Instant.parse("2019-10-22T00:00:00Z").toEpochMilli();
        assertEquals(5, render(trackConfiguration(midnight, TO)));
    }

    @Test
    void skipsTracksWithoutPointsInsideTheTimeRange() throws Exception {
        assertEquals(4, render(trackConfiguration(FROM, TO), trackConfiguration(NEXT_YEAR, null)));
    }

    @Test
    void failsIfNoTrackHasPointsInsideTheTimeRange() throws Exception {
        final var trackConfiguration = trackConfiguration(NEXT_YEAR, null);
        assertThrows(UserException.class, () -> render(trackConfiguration));
    }

    private TrackConfiguration trackConfiguration(final Long from, final Long to) throws URISyntaxException {
        return TrackConfiguration.createBuilder()
                .inputGpx(Path.of(Objects.requireNonNull(getClass().getResource("/gpx/comment.gpx")).toURI()).toFile())
                .label("")
                .timeRangeFrom(from)
                .timeRangeTo(to)
                .build();
    }

    private long render(final TrackConfiguration... trackConfigurations) throws UserException, IOException {
        final var builder = Configuration.createBuilder()
                .output(outputDirectory.resolve("frame%08d.png").toFile())
                .fps(1.0)
                .speedup(1.0)
                .tailColorFadeout(false);
        for (final var trackConfiguration : trackConfigurations) {
            builder.addTrackConfiguration(trackConfiguration);
        }

        new Renderer(builder.build()).render(new RenderingContext() {
            @Override
            public void setProgress1(final int pct, final String message) {
                // progress is not checked by these tests
            }

            @Override
            public boolean isCancelled1() {
                return false;
            }
        });

        try (var frames = Files.list(outputDirectory)) {
            return frames.count();
        }
    }

}
