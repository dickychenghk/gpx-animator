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
package app.gpx_animator.core.renderer.plugins;

import app.gpx_animator.core.UserException;
import app.gpx_animator.core.configuration.Configuration;
import app.gpx_animator.core.preferences.Preferences;
import app.gpx_animator.core.renderer.Metadata;
import app.gpx_animator.core.renderer.RenderingContext;
import app.gpx_animator.core.renderer.cache.TileCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Plugins are loaded using reflection
@SuppressWarnings("unused")
public final class BackgroundMapPlugin implements RendererPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundMapPlugin.class);

    @SuppressWarnings({"RegExpAnonymousGroup", "RegExpRedundantEscape"})
    // This regex is tested, and I don't want to rewrite it which may potentionally break it.
    private static final Pattern SWITCH_PATTERN = Pattern.compile("\\{switch:([^}]*)\\}");

    private static final Pattern LAYER_SEPARATOR = Pattern.compile("\\|");

    private final ResourceBundle resourceBundle = Preferences.getResourceBundle();

    private final String tmsUrlTemplate;
    private final String tmsApiKey;
    private final String tmsUserAgent;
    private final float backgroundMapVisibility;

    private Integer zoom;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;

    private RenderingContext context;

    public BackgroundMapPlugin(@NonNull final Configuration configuration) {
        tmsUrlTemplate = configuration.getTmsUrlTemplate();
        tmsApiKey = configuration.getTmsApiKey();
        tmsUserAgent = configuration.getTmsUserAgent();
        backgroundMapVisibility = configuration.getBackgroundMapVisibility();
    }

    @Override
    public void setMetadata(@NotNull final Metadata metadata) {
        zoom = metadata.zoom();
        minX = metadata.minX();
        maxX = metadata.maxX();
        minY = metadata.minY();
        maxY = metadata.maxY();
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "RenderingContext is used for callbacks")
    public void setRenderingContext(@NotNull final RenderingContext renderingContext) {
        this.context = renderingContext;
    }

    @Override
    public int getOrder() {
        return -1_000;
    }

    @Override
    public void renderBackground(@NonNull final BufferedImage image) throws UserException {
        if (tmsUrlTemplate == null || tmsUrlTemplate.isBlank() || backgroundMapVisibility <= 0.0 || zoom == null) {
            // no map defined or map should not be visible
            return;
        }

        final var ga = (Graphics2D) image.getGraphics();

        final var tileDblX = xToTileX(zoom, minX);
        final var tileX = (int) Math.floor(tileDblX);
        final var offsetX = (int) Math.floor(256.0 * (tileX - tileDblX));

        final var tileDblY = yToTileY(zoom, minY);
        final var tileY = (int) Math.floor(tileDblY);
        final var offsetY = (int) Math.floor(256.0 * (tileDblY - tileY));

        final var maxXtile = (int) Math.floor(xToTileX(zoom, maxX));
        final var maxYtile = (int) Math.floor(yToTileY(zoom, maxY));

        final var total = (maxXtile - tileX + 1) * (tileY - maxYtile + 1);
        var i = 0;

        final var layerTemplates = splitLayers(tmsUrlTemplate);
        final var tileCacheDir = Preferences.getTileCacheDir();
        final var tileCacheTimeLimit = Preferences.getTileCacheTimeLimit();
        final var visibilityOp = new RescaleOp(backgroundMapVisibility, (1f - backgroundMapVisibility) * 255f, null);

        var drawnTiles = 0;
        UserException firstError = null;

        for (var x = tileX; x <= maxXtile; x++) {
            for (var y = tileY; y >= maxYtile; y--) {
                if (context.isCancelled1()) {
                    return;
                }

                i++;

                context.setProgress1((int) (100.0 * i / total), String.format(resourceBundle.getString("map.loadingtiles.progress"), i, total));

                BufferedImage tile = null;
                for (final var layerTemplate : layerTemplates) {
                    final var url = buildTileUrl(layerTemplate, zoom, x, y, tmsApiKey, i);
                    final BufferedImage layerTile;
                    try {
                        layerTile = TileCache.getTile(url, tmsUserAgent, tileCacheDir, tileCacheTimeLimit);
                    } catch (final UserException e) {
                        LOGGER.warn("Skipping map tile {}: {}", url, e.getMessage());
                        if (firstError == null) {
                            firstError = e;
                        }
                        continue;
                    }
                    if (layerTile == null) {
                        continue;
                    }
                    if (tile == null) {
                        tile = new BufferedImage(layerTile.getWidth(), layerTile.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    }
                    final var tileGraphics = tile.createGraphics();
                    tileGraphics.drawImage(layerTile, 0, 0, null);
                    tileGraphics.dispose();
                }

                if (tile != null) {
                    // a single scale factor leaves the alpha channel untouched, so transparent parts show the background
                    ga.drawImage(tile, visibilityOp,
                            256 * (x - tileX) + offsetX,
                            image.getHeight() - (256 * (tileY - y) + offsetY));
                    drawnTiles++;
                }
            }
        }

        context.setProgress1(100, String.format(resourceBundle.getString("map.loadingtiles.progress"), i, total));

        if (drawnTiles == 0) {
            throw firstError != null ? firstError
                    : new UserException(resourceBundle.getString("map.error.notiles").formatted(zoom));
        }
    }

    /**
     * Splits a TMS URL template into its layers, which are separated by {@code |} and drawn bottom to top.
     *
     * @param urlTemplate the TMS URL template
     * @return the URL templates of the layers
     */
    static List<String> splitLayers(@NonNull final String urlTemplate) {
        return Arrays.stream(LAYER_SEPARATOR.split(urlTemplate))
                .map(String::trim)
                .filter(layer -> !layer.isEmpty())
                .toList();
    }

    /**
     * Builds the URL of a single map tile for one layer.
     *
     * @param layerTemplate the URL template of the layer
     * @param zoomLevel the zoom level
     * @param tileX the x coordinate of the tile
     * @param tileY the y coordinate of the tile
     * @param apiKey the API key, may be {@code null}
     * @param counter the running tile counter, used to rotate through the {@code {switch:…}} options
     * @return the URL of the map tile
     */
    static String buildTileUrl(@NonNull final String layerTemplate, final int zoomLevel, final int tileX, final int tileY,
                               @Nullable final String apiKey, final int counter) {
        final var key = apiKey == null ? "" : apiKey;
        final var url = layerTemplate
                .replace("{zoom}", Integer.toString(zoomLevel)) //NON-NLS
                .replace("{x}", Integer.toString(tileX)) //NON-NLS
                .replace("{y}", Integer.toString(tileY)) //NON-NLS
                .replace("{apikey}", key) //NON-NLS
                .replace("{access_token}", key); //NON-NLS

        final var matcher = SWITCH_PATTERN.matcher(url); // note that only one switch per layer is supported
        if (!matcher.find()) {
            return url;
        }
        final var options = matcher.group(1).split(",");
        final var sb = new StringBuilder();
        matcher.appendReplacement(sb, Matcher.quoteReplacement(options[counter % options.length]));
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static double yToTileY(final int zoom, final double minY) {
        return latToTileY(zoom, yToLat(minY));
    }


    private static double xToTileX(final int zoom, final double minX) {
        return lonToTileX(zoom, xToLon(minX));
    }


    private static double lonToTileX(final int zoom, final double lon) {
        return (lon + 180.0) / 360.0 * (1 << zoom);
    }


    private static double latToTileY(final int zoom, final double lat) {
        return (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom);
    }


    private static double xToLon(final double x) {
        return Math.toDegrees(x);
    }


    private static double yToLat(final double y) {
        return Math.toDegrees(2.0 * (Math.atan(Math.exp(y)) - Math.PI / 4.0));
    }

}
