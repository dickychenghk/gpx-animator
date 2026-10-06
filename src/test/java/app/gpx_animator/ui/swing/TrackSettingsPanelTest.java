package app.gpx_animator.ui.swing;

import app.gpx_animator.core.configuration.TrackConfiguration;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TrackSettingsPanelTest {

    @Test
    void keepsTheTimeRangeOfTheSelectedZone() throws InterruptedException, InvocationTargetException {
        final var from = Instant.parse("2024-05-01T00:00:00Z").toEpochMilli();
        final var to = Instant.parse("2024-05-01T04:30:00Z").toEpochMilli();
        final var configuration = TrackConfiguration.createBuilder()
                .inputGpx(new File("hike.gpx"))
                .label("")
                .timeRangeZone("Asia/Tokyo")
                .timeRangeFrom(from)
                .timeRangeTo(to)
                .build();

        final var result = roundTrip(configuration);

        assertEquals("Asia/Tokyo", result.getTimeRangeZone());
        assertEquals(from, result.getTimeRangeFrom());
        assertEquals(to, result.getTimeRangeTo());
    }

    @Test
    void usesTheSystemZoneWithoutTimeRange() throws InterruptedException, InvocationTargetException {
        final var configuration = TrackConfiguration.createBuilder()
                .inputGpx(new File("hike.gpx"))
                .label("")
                .build();

        final var result = roundTrip(configuration);

        assertFalse(result.hasTimeRange());
        assertEquals(ZoneId.systemDefault().getId(), result.getTimeRangeZone());
    }

    private static TrackConfiguration roundTrip(final TrackConfiguration configuration)
            throws InterruptedException, InvocationTargetException {
        final var result = new AtomicReference<TrackConfiguration>();
        SwingUtilities.invokeAndWait(() -> {
            final var panel = new TrackSettingsPanel() {
                @Serial
                private static final long serialVersionUID = 1L;

                @Override
                protected void labelChanged(final String label) {
                    // not needed for this test
                }

                @Override
                protected void remove() {
                    // not needed for this test
                }

                @Override
                protected void configurationChanged() {
                    // not needed for this test
                }
            };
            panel.setConfiguration(configuration);
            result.set(panel.createConfiguration());
        });
        return result.get();
    }

}
