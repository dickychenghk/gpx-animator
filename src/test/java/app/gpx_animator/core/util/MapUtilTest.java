package app.gpx_animator.core.util;

import app.gpx_animator.core.data.MapTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapUtilTest {

    @Test
    void readMapsStartsWithBuiltInMaps() {
        final var mapIds = MapUtil.readMaps().stream().map(MapTemplate::id).limit(4).toList();

        assertEquals(List.of("hk-landsd-imagery-tc", "hk-landsd-basemap-tc", "hk-landsd-imagery-en", "hk-landsd-basemap-en"), mapIds);
    }

    @Test
    void getMapTemplateFindsBuiltInMap() {
        final var url = "https://mapapi.geodata.gov.hk/gs/api/v1.0.0/xyz/imagery/WGS84/{zoom}/{x}/{y}.png"
                + "|https://mapapi.geodata.gov.hk/gs/api/v1.0.0/xyz/label/hk/tc/WGS84/{zoom}/{x}/{y}.png";

        final var mapTemplate = MapUtil.getMapTemplate(url);

        assertNotNull(mapTemplate);
        assertEquals("hk-landsd-imagery-tc", mapTemplate.id());
        assertEquals("HK: Lands Department Imagery (Chinese labels)", mapTemplate.toString());
        assertEquals("© Map from Lands Department", mapTemplate.attributionText());
        assertTrue(mapTemplate.attributionTextMandatory());
        assertEquals(20, mapTemplate.maxZoom());
    }

    @Test
    void getMapTemplateWithoutUrl() {
        assertNull(MapUtil.getMapTemplate(null));
        assertNull(MapUtil.getMapTemplate(" "));
    }

}
