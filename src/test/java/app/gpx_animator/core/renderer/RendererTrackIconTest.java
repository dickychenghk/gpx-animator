package app.gpx_animator.core.renderer;

import app.gpx_animator.core.configuration.Configuration;
import app.gpx_animator.core.configuration.TrackConfiguration;
import app.gpx_animator.core.data.TrackIcon;
import app.gpx_animator.ui.UIMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RendererTrackIconTest {

    @TempDir
    private Path outputDirectory;

    @BeforeAll
    static void useCommandLineMode() {
        UIMode.setMode(UIMode.CLI);
    }

    @Test
    void usesTheTrackIconIfNoIconFileIsSelected() throws URISyntaxException {
        // the GUI passes an empty path if no icon file is selected, which newer JDKs resolve to the working directory
        final var trackConfiguration = TrackConfiguration.createBuilder()
                .inputGpx(Path.of(Objects.requireNonNull(getClass().getResource("/gpx/comment.gpx")).toURI()).toFile())
                .label("")
                .trackIcon(new TrackIcon("jogging"))
                .inputIcon(new File(""))
                .build();
        final var configuration = Configuration.createBuilder()
                .output(outputDirectory.resolve("frame%08d.png").toFile())
                .fps(1.0)
                .speedup(1.0)
                .tailColorFadeout(false)
                .addTrackConfiguration(trackConfiguration)
                .build();

        assertDoesNotThrow(() -> new Renderer(configuration).render(new RenderingContext() {
            @Override
            public void setProgress1(final int pct, final String message) {
                // progress is not checked by this test
            }

            @Override
            public boolean isCancelled1() {
                return false;
            }
        }));
    }

}
