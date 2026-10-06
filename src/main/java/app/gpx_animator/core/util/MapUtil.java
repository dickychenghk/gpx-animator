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

import app.gpx_animator.core.data.MapTemplate;
import app.gpx_animator.core.preferences.Preferences;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.xml.XMLConstants;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MapUtil {

    private static final String MAPS_UPDATE_URL = "https://josm.openstreetmap.de/maps";

    private static final String LANDSD_TILE_URL = "https://mapapi.geodata.gov.hk/gs/api/v1.0.0/xyz/%s/WGS84/{zoom}/{x}/{y}.png"; //NON-NLS
    private static final String LANDSD_ATTRIBUTION = "© Map from Lands Department"; //NON-NLS
    private static final int LANDSD_MAX_ZOOM = 20;

    private static final List<MapTemplate> BUILT_IN_MAPS = List.of(
            landsDepartmentMap("hk-landsd-imagery-tc", "Lands Department Imagery (Chinese labels)", "imagery", "label/hk/tc"), //NON-NLS
            landsDepartmentMap("hk-landsd-basemap-tc", "Lands Department Basemap (Chinese labels)", "basemap", "label/hk/tc"), //NON-NLS
            landsDepartmentMap("hk-landsd-imagery-en", "Lands Department Imagery (English labels)", "imagery", "label/hk/en"), //NON-NLS
            landsDepartmentMap("hk-landsd-basemap-en", "Lands Department Basemap (English labels)", "basemap", "label/hk/en")); //NON-NLS

    private MapUtil() throws InstantiationException {
        throw new InstantiationException("MapUtil is a utility class and can't be instantiated!");
    }

    private static MapTemplate landsDepartmentMap(final String id, final String name, final String baseLayer, final String labelLayer) {
        final var url = LANDSD_TILE_URL.formatted(baseLayer) + "|" + LANDSD_TILE_URL.formatted(labelLayer);
        return new MapTemplate(id, name, "tms", url, LANDSD_ATTRIBUTION, true, LANDSD_MAX_ZOOM, "HK"); //NON-NLS
    }

    private static Path getMapPath() {
        return Path.of(Preferences.getConfigurationDir(), "maps.xml");
    }

    public static boolean hasNoMaps() {
        return !getMapPath().toFile().exists();
    }

    @SuppressWarnings("PMD.CloseResource") // false positive
    public static List<MapTemplate> updateMaps() {
        try {
            final var client = HttpClient.newHttpClient();
            final var request = HttpRequest.newBuilder()
                    .uri(URI.create(MAPS_UPDATE_URL))
                    .GET()
                    .build();

            final var response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            final var xml = response.body();

            Files.writeString(getMapPath(), xml, StandardCharsets.UTF_8);

            return readMaps();
        } catch (final InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void touchMapFile() {
        if (hasNoMaps()) {
            try {
                Files.writeString(getMapPath(), "", StandardCharsets.UTF_8);
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Reads the available map templates: the built-in maps first, followed by the downloaded maps sorted by country and name.
     *
     * @return the map templates
     */
    public static List<MapTemplate> readMaps() {
        if (hasNoMaps() || getMapPath().toFile().length() == 0) {
            return BUILT_IN_MAPS;
        }

        final var factory = SAXParserFactory.newInstance();
        final SAXParser saxParser;
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            saxParser = factory.newSAXParser();
            saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (final ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }

        final List<MapTemplate> labeledItems = new ArrayList<>();

        try {
            try (var is = new FileInputStream(getMapPath().toFile())) {
                saxParser.parse(is, new DefaultHandler() {
                    private final StringBuilder sb = new StringBuilder();
                    private String id;
                    private String name;
                    private String type;
                    private String url;
                    private String attributionText;
                    private boolean attributionTextMandatory = false;
                    private Integer maxZoom;
                    private String countryCode = "";

                    @Override
                    public void startElement(@Nullable final String uri,
                                             @Nullable final String localName,
                                             @Nullable final String qName,
                                             @Nullable final Attributes attributes) {
                        if (qName != null && qName.equals("attribution-text")) {
                            attributionTextMandatory = attributes != null && Boolean.parseBoolean(attributes.getValue("mandatory"));
                        }
                    }

                    @Override
                    @SuppressWarnings({
                            "checkstyle:MissingSwitchDefault", // Every other case can be ignored!
                            "checkstyle:InnerAssignment",      // Checkstyle 8.37 can't handle the enhanced switch properly
                            "PMD.AssignmentInOperand",         // assignment in operand is intentional
                            "PMD.NullAssignment"               // inside the parser element handling this is acceptable
                    })
                    @SuppressFBWarnings(value = "SF_SWITCH_NO_DEFAULT", justification = "Every other case can be ignored!") //NON-NLS NON-NLS
                    public void endElement(@Nullable final String uri,
                                           @Nullable final String localName,
                                           @NotNull @NonNls final String qName) {
                        switch (qName) {
                            case "id" -> id = sb.toString().trim();
                            case "name" -> name = sb.toString().trim();
                            case "type" -> type = sb.toString().trim();
                            case "url" -> url = sb.toString().trim();
                            case "attribution-text" -> attributionText = sb.toString().trim();
                            case "max-zoom" -> maxZoom = Integer.parseInt(sb.toString().trim());
                            case "country-code" -> countryCode = sb.toString().trim();
                            case "entry" -> {
                                if (type.equalsIgnoreCase("tms")) {
                                    labeledItems.add(new MapTemplate(id, name, type, url, attributionText, attributionTextMandatory, maxZoom,
                                            countryCode));
                                }
                                id = null; name = null; type = null; url = null; attributionText = null; maxZoom = null; countryCode = "";
                                attributionTextMandatory = false;
                            }
                        }
                        sb.setLength(0);
                    }

                    @Override
                    public void characters(final char[] ch, final int start, final int length) {
                        sb.append(ch, start, length);
                    }
                });
            } catch (final SAXException e) {
                throw new RuntimeException(e);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        labeledItems.sort(Comparator.comparing(MapTemplate::countryCode)
                .thenComparing(MapTemplate::name));

        final List<MapTemplate> mapTemplates = new ArrayList<>(BUILT_IN_MAPS);
        mapTemplates.addAll(labeledItems);
        return mapTemplates;
    }

    public static MapTemplate getMapTemplate(final String tmsUrlTemplate) {
        if (tmsUrlTemplate == null || tmsUrlTemplate.isBlank()) {
            return null;
        }

        return BUILT_IN_MAPS.stream()
                .filter(m -> tmsUrlTemplate.equals(m.url()))
                .findFirst()
                .orElseGet(() -> readMaps().stream()
                        .filter(m -> tmsUrlTemplate.equals(m.url()))
                        .findFirst()
                        .orElse(null));
    }
}
