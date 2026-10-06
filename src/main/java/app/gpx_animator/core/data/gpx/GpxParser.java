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
package app.gpx_animator.core.data.gpx;

import app.gpx_animator.core.UserException;
import app.gpx_animator.core.data.entity.TrackPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

public final class GpxParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(GpxParser.class);

    private GpxParser() throws InstantiationException {
        throw new InstantiationException("GpxParser is a utility class and can't be instantiated!");
    }

    /**
     * Reads the timestamps of the first and the last track point of a GPX file.
     *
     * @param inputGpx the GPX file
     * @return the time range in epoch milliseconds or empty, if the track points have no timestamps
     * @throws UserException if the file can't be read or parsed
     */
    public static Optional<TimeRange> readTrackTimeRange(final File inputGpx) throws UserException {
        final var handler = new GpxContentHandler();
        parseGpx(inputGpx, handler);

        final var track = handler.getTrack();
        if (track == null) {
            return Optional.empty();
        }

        final var statistics = track.getTrackSegments().stream()
                .flatMap(trackSegment -> trackSegment.getTrackPoints().stream())
                .map(TrackPoint::getTime)
                .filter(Objects::nonNull)
                .filter(time -> time != Long.MIN_VALUE)
                .mapToLong(Long::longValue)
                .summaryStatistics();

        return statistics.getCount() == 0
                ? Optional.empty()
                : Optional.of(new TimeRange(statistics.getMin(), statistics.getMax()));
    }

    public static void parseGpx(final File inputGpx, final DefaultHandler dh) throws UserException {
        try (InputStream is = new FileInputStream(inputGpx)) {
            LOGGER.info("Parsing GPX file \"{}\"...", inputGpx.getName());
            parseGpx(is, dh);
        } catch (final IOException e) {
            throw new UserException("error reading input file", e); // TODO translate all user exceptions
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException") // Need to catch possible wraps of UserException
    public static void parseGpx(final InputStream is, final DefaultHandler dh) throws UserException {
        final SAXParser saxParser;
        try {
            final var factory = SAXParserFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            saxParser = factory.newSAXParser();
            saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (final ParserConfigurationException | SAXException e) {
            throw new RuntimeException("can't create XML parser", e);
        }

        try (var dis = decompressStream(is)) {
            saxParser.parse(dis, dh);
        } catch (final SAXException | IOException e) {
            throw new UserException("error parsing input GPX file", e);
        } catch (final RuntimeException e) {
            if (e.getCause() != null && e.getCause() instanceof UserException userException) {
                throw userException;
            }
            throw new RuntimeException("internal error when parsing GPX file", e);
        }
    }

    @SuppressWarnings("PMD.CloseResource") // The returned stream will be used and closed in a try-with-resources block
    public static InputStream decompressStream(final InputStream input) throws IOException {
        final var pb = new PushbackInputStream(input, 2);
        final var signature = new byte[2];
        final var numBytesRead = pb.read(signature);
        pb.unread(signature, 0, numBytesRead);
        return signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b ? new GZIPInputStream(pb) : pb;
    }

    public record TimeRange(long first, long last) { }

}
