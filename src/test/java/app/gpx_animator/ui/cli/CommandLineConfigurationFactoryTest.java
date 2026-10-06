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
package app.gpx_animator.ui.cli;

import app.gpx_animator.core.Option;
import app.gpx_animator.core.UserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineConfigurationFactoryTest {

    public static final String TEST_COLOR_FF_0096 = "#FF0096";
    public static final String TEST_FONT_MONOSPACED_8 = "Monospaced 8";
    private static final String TEST_GPX = "hike.gpx";

    @ParameterizedTest
    @EnumSource(Option.class)
    void checkInputParamsByOptionParams(final Option option) throws UserException {
        if (option.equals(Option.GUI)) {
            return;
        }
        final var op = OptionParam.ofOption(option);
        final var supplierParam = op.getSupplierParam();
        assertNotNull(supplierParam);
        final var optArgument = supplierParam.get();
        final var args = new ArrayList<>();
        args.add("--".concat(option.getName()));
        optArgument.ifPresent(args::add);
        if (op.isNeedTrackConfiguration()) {
            args.add("--".concat(OptionParam.INPUT.getOption().getName()));
            OptionParam.INPUT.getSupplierParam().get().ifPresent(args::add);
        }
        CommandLineConfigurationFactory factory = new CommandLineConfigurationFactory(args.toArray(String[]::new));
        assertTrue(op.getCheckResult().apply(factory));
    }

    @Test
    void testOutputWhenInputIsSet() throws UserException {
        final var args = new String[] {
                "--input", "journey.gpx",
                "--input", "anotherJourney.gpx",
        };

        CommandLineConfigurationFactory factory = new CommandLineConfigurationFactory(args);
        assertNotNull(factory.getConfiguration().getOutput());
        assertEquals("journey.mp4", factory.getConfiguration().getOutput().getName());
    }

    @Test
    void testMultipleInputParams() throws UserException {
        // given --input with multiple input files
        final var args = new String[]{"--input", "input1.gpx", "input2.gpx", "input3.gpx", "--output", "dummyOutput.mp4"};

        // when creating the configuration
        var factory = new CommandLineConfigurationFactory(args);

        // then the configuration contains all input files
        assertEquals(3, factory.getConfiguration().getTrackConfigurationList().size());
        assertEquals("input1.gpx", factory.getConfiguration().getTrackConfigurationList().get(0).getInputGpx().getName());
        assertEquals("input2.gpx", factory.getConfiguration().getTrackConfigurationList().get(1).getInputGpx().getName());
        assertEquals("input3.gpx", factory.getConfiguration().getTrackConfigurationList().get(2).getInputGpx().getName());
    }

    @Test
    void testTimeRangeIsInterpretedInTimeRangeZone() throws UserException {
        final var args = new String[]{
                optionArgument(Option.INPUT), TEST_GPX,
                optionArgument(Option.TIME_RANGE_ZONE), "Asia/Hong_Kong",
                optionArgument(Option.TIME_RANGE_FROM), "2024-05-01 08:00",
                optionArgument(Option.TIME_RANGE_TO), "2024-05-01T12:30:00"
        };

        final var trackConfiguration = new CommandLineConfigurationFactory(args).getConfiguration().getTrackConfigurationList().getFirst();

        assertEquals("Asia/Hong_Kong", trackConfiguration.getTimeRangeZone());
        assertEquals(Instant.parse("2024-05-01T00:00:00Z").toEpochMilli(), trackConfiguration.getTimeRangeFrom());
        assertEquals(Instant.parse("2024-05-01T04:30:00Z").toEpochMilli(), trackConfiguration.getTimeRangeTo());
    }

    @Test
    void testWithoutTimeRange() throws UserException {
        final var args = new String[]{optionArgument(Option.INPUT), TEST_GPX};

        final var trackConfiguration = new CommandLineConfigurationFactory(args).getConfiguration().getTrackConfigurationList().getFirst();

        assertFalse(trackConfiguration.hasTimeRange());
        assertNull(trackConfiguration.getTimeRangeZone());
    }

    @Test
    void testInvalidTimeRangeValues() {
        final var invalidDateTime = new String[]{optionArgument(Option.INPUT), TEST_GPX, optionArgument(Option.TIME_RANGE_FROM), "yesterday"};
        assertThrows(UserException.class, () -> new CommandLineConfigurationFactory(invalidDateTime));

        final var invalidZone = new String[]{optionArgument(Option.INPUT), TEST_GPX, optionArgument(Option.TIME_RANGE_ZONE), "Mars/Olympus_Mons"};
        assertThrows(UserException.class, () -> new CommandLineConfigurationFactory(invalidZone));
    }

    private static String optionArgument(final Option option) {
        return "--".concat(option.getName());
    }
}
