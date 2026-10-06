package app.gpx_animator.core.renderer.plugins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackgroundMapPluginTest {

    @Test
    void splitSingleLayer() {
        assertEquals(List.of("https://tile.example/{zoom}/{x}/{y}.png"),
                BackgroundMapPlugin.splitLayers("https://tile.example/{zoom}/{x}/{y}.png"));
    }

    @Test
    void splitMultipleLayers() {
        assertEquals(List.of("https://base.example/{zoom}/{x}/{y}.png", "https://labels.example/{zoom}/{x}/{y}.png"),
                BackgroundMapPlugin.splitLayers("https://base.example/{zoom}/{x}/{y}.png | https://labels.example/{zoom}/{x}/{y}.png|"));
    }

    @Test
    void buildTileUrl() {
        assertEquals("https://tile.example/12/3416/1789.png?key=secret",
                BackgroundMapPlugin.buildTileUrl("https://tile.example/{zoom}/{x}/{y}.png?key={apikey}", 12, 3416, 1789, "secret", 1));
        assertEquals("https://tile.example/12/3416/1789.png?access_token=",
                BackgroundMapPlugin.buildTileUrl("https://tile.example/{zoom}/{x}/{y}.png?access_token={access_token}", 12, 3416, 1789, null, 1));
    }

    @Test
    void buildTileUrlRotatesSwitchOptions() {
        final var template = "https://{switch:a,b,c}.tile.example/{zoom}/{x}/{y}.png";

        assertEquals("https://b.tile.example/1/2/3.png", BackgroundMapPlugin.buildTileUrl(template, 1, 2, 3, "", 1));
        assertEquals("https://c.tile.example/1/2/3.png", BackgroundMapPlugin.buildTileUrl(template, 1, 2, 3, "", 2));
        assertEquals("https://a.tile.example/1/2/3.png", BackgroundMapPlugin.buildTileUrl(template, 1, 2, 3, "", 3));
    }

}
