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
package app.gpx_animator.ui.swing;

import app.gpx_animator.core.Option;
import app.gpx_animator.core.UserException;
import app.gpx_animator.core.configuration.TrackConfiguration;
import app.gpx_animator.core.data.TrackIcon;
import app.gpx_animator.core.data.gpx.GpxParser;
import app.gpx_animator.core.preferences.Preferences;
import app.gpx_animator.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.Serial;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

import static javax.swing.JFileChooser.FILES_ONLY;

abstract class TrackSettingsPanel extends JPanel {

    @Serial
    private static final long serialVersionUID = 2492074184123083022L;

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackSettingsPanel.class);

    private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone(ZoneOffset.UTC);
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"; //NON-NLS
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN, Locale.ROOT);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT); //NON-NLS
    private static final int TIME_ZONE_POPUP_ROWS = 20;

    private final transient ResourceBundle resourceBundle = Preferences.getResourceBundle();

    private final JTextArea labelTextField;
    private final JSpinner forcedPointTimeIntervalSpinner;
    private final JSpinner timeOffsetSpinner;
    private final JSpinner lineWidthSpinner;
    private final JSpinner preDrawLineWidthSpinner;
    private final ColorSelector colorSelector;
    private final ColorSelector preDrawColorSelector;
    private final FileSelector inputGpxFileSelector;
    private final JSpinner trimGpxStartSpinner;
    private final JSpinner trimGpxEndSpinner;
    private final JComboBox<TrackIcon> travelIconComboBox;
    private final FileSelector travelIconFileSelector;
    private final JCheckBox travelMirrorCheckBox;

    private final JComboBox<TrackIcon> trackEndIconComboBox;
    private final FileSelector trackEndIconFileSelector;
    private final JCheckBox trackEndMirrorCheckBox;

    private final JLabel gpxTimeRangeLabel;
    private final JComboBox<TimeZoneItem> timeZoneComboBox;
    private final JCheckBox timeRangeFromCheckBox;
    private final JSpinner timeRangeFromSpinner;
    private final JCheckBox timeRangeToCheckBox;
    private final JSpinner timeRangeToSpinner;

    private boolean timeRangeFromValueSet;
    private boolean timeRangeToValueSet;
    private transient TimeRangeStatus timeRangeStatus = TimeRangeStatus.NO_FILE;
    private transient GpxParser.TimeRange detectedTimeRange;
    private transient SwingWorker<Optional<GpxParser.TimeRange>, Void> timeRangeReader;
    private transient int timeRangeReaderGeneration;

    @SuppressWarnings({
            "checkstyle:MethodLength", // TODO Refactor when doing the redesign task https://github.com/gpx-animator/gpx-animator/issues/60
            "PMD.AssignmentInOperand", // assignment in operand is intentional
            "PMD.ConstructorCallsOverridableMethod"
    })
    TrackSettingsPanel() {
        setBounds(100, 100, 595, 419);
        setBorder(new EmptyBorder(5, 5, 5, 5));
        final var gblContentPane = new GridBagLayout();
        gblContentPane.columnWidths = new int[]{0, 0, 0};
        gblContentPane.columnWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};
        gblContentPane.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        gblContentPane.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE};
        setLayout(gblContentPane);
        var rowCounter = 0;

        final var lblGpx = new JLabel(resourceBundle.getString("ui.panel.tracksettings.gpxfile.label"));
        final var gbcLabelGpx = new GridBagConstraints();
        gbcLabelGpx.insets = new Insets(0, 0, 5, 5);
        gbcLabelGpx.anchor = GridBagConstraints.LINE_END;
        gbcLabelGpx.gridx = 0;
        gbcLabelGpx.gridy = rowCounter;
        add(lblGpx, gbcLabelGpx);

        inputGpxFileSelector = new FileSelector(FILES_ONLY) {
            @Serial
            private static final long serialVersionUID = -7085193817022374995L;

            @Override
            protected Type configure(final JFileChooser gpxFileChooser) {
                configureGpxFileChooser(resourceBundle, gpxFileChooser);
                return Type.OPEN;
            }
        };

        inputGpxFileSelector.setToolTipText(Option.INPUT.getHelp());
        lblGpx.setLabelFor(inputGpxFileSelector);
        final var gbcInputGpxFileSelector = new GridBagConstraints();
        gbcInputGpxFileSelector.insets = new Insets(0, 0, 5, 0);
        gbcInputGpxFileSelector.fill = GridBagConstraints.BOTH;
        gbcInputGpxFileSelector.gridx = 1;
        gbcInputGpxFileSelector.gridy = rowCounter;
        add(inputGpxFileSelector, gbcInputGpxFileSelector);

        final var lblGpxTimeRange = new JLabel(resourceBundle.getString("ui.panel.tracksettings.timerange.label"));
        final var gbcLabelGpxTimeRange = new GridBagConstraints();
        gbcLabelGpxTimeRange.anchor = GridBagConstraints.LINE_END;
        gbcLabelGpxTimeRange.insets = new Insets(0, 0, 5, 5);
        gbcLabelGpxTimeRange.gridx = 0;
        gbcLabelGpxTimeRange.gridy = ++rowCounter;
        add(lblGpxTimeRange, gbcLabelGpxTimeRange);

        gpxTimeRangeLabel = new JLabel();
        lblGpxTimeRange.setLabelFor(gpxTimeRangeLabel);
        final var gbcGpxTimeRangeLabel = new GridBagConstraints();
        gbcGpxTimeRangeLabel.anchor = GridBagConstraints.LINE_START;
        gbcGpxTimeRangeLabel.fill = GridBagConstraints.HORIZONTAL;
        gbcGpxTimeRangeLabel.insets = new Insets(0, 0, 5, 0);
        gbcGpxTimeRangeLabel.gridx = 1;
        gbcGpxTimeRangeLabel.gridy = rowCounter;
        add(gpxTimeRangeLabel, gbcGpxTimeRangeLabel);

        final var lblTimeZone = new JLabel(resourceBundle.getString("ui.panel.tracksettings.timezone.label"));
        final var gbcLabelTimeZone = new GridBagConstraints();
        gbcLabelTimeZone.anchor = GridBagConstraints.LINE_END;
        gbcLabelTimeZone.insets = new Insets(0, 0, 5, 5);
        gbcLabelTimeZone.gridx = 0;
        gbcLabelTimeZone.gridy = ++rowCounter;
        add(lblTimeZone, gbcLabelTimeZone);

        timeZoneComboBox = new JComboBox<>(createTimeZoneItems());
        timeZoneComboBox.setToolTipText(Option.TIME_RANGE_ZONE.getHelp());
        timeZoneComboBox.setMaximumRowCount(TIME_ZONE_POPUP_ROWS);
        lblTimeZone.setLabelFor(timeZoneComboBox);
        selectTimeZone(ZoneId.systemDefault());
        final var gbcTimeZoneComboBox = new GridBagConstraints();
        gbcTimeZoneComboBox.fill = GridBagConstraints.HORIZONTAL;
        gbcTimeZoneComboBox.insets = new Insets(0, 0, 5, 0);
        gbcTimeZoneComboBox.gridx = 1;
        gbcTimeZoneComboBox.gridy = rowCounter;
        add(timeZoneComboBox, gbcTimeZoneComboBox);
        timeZoneComboBox.setPreferredSize(new Dimension(10, timeZoneComboBox.getPreferredSize().height));

        timeRangeFromCheckBox = new JCheckBox(resourceBundle.getString("ui.panel.tracksettings.timerangefrom.label"));
        timeRangeFromCheckBox.setHorizontalTextPosition(SwingConstants.LEADING);
        timeRangeFromCheckBox.setToolTipText(Option.TIME_RANGE_FROM.getHelp());
        final var gbcTimeRangeFromCheckBox = new GridBagConstraints();
        gbcTimeRangeFromCheckBox.anchor = GridBagConstraints.LINE_END;
        gbcTimeRangeFromCheckBox.insets = new Insets(0, 0, 5, 5);
        gbcTimeRangeFromCheckBox.gridx = 0;
        gbcTimeRangeFromCheckBox.gridy = ++rowCounter;
        add(timeRangeFromCheckBox, gbcTimeRangeFromCheckBox);

        timeRangeFromSpinner = createDateTimeSpinner();
        timeRangeFromSpinner.setToolTipText(Option.TIME_RANGE_FROM.getHelp());
        final var gbcTimeRangeFromSpinner = new GridBagConstraints();
        gbcTimeRangeFromSpinner.fill = GridBagConstraints.HORIZONTAL;
        gbcTimeRangeFromSpinner.insets = new Insets(0, 0, 5, 0);
        gbcTimeRangeFromSpinner.gridx = 1;
        gbcTimeRangeFromSpinner.gridy = rowCounter;
        add(timeRangeFromSpinner, gbcTimeRangeFromSpinner);

        timeRangeToCheckBox = new JCheckBox(resourceBundle.getString("ui.panel.tracksettings.timerangeto.label"));
        timeRangeToCheckBox.setHorizontalTextPosition(SwingConstants.LEADING);
        timeRangeToCheckBox.setToolTipText(Option.TIME_RANGE_TO.getHelp());
        final var gbcTimeRangeToCheckBox = new GridBagConstraints();
        gbcTimeRangeToCheckBox.anchor = GridBagConstraints.LINE_END;
        gbcTimeRangeToCheckBox.insets = new Insets(0, 0, 5, 5);
        gbcTimeRangeToCheckBox.gridx = 0;
        gbcTimeRangeToCheckBox.gridy = ++rowCounter;
        add(timeRangeToCheckBox, gbcTimeRangeToCheckBox);

        timeRangeToSpinner = createDateTimeSpinner();
        timeRangeToSpinner.setToolTipText(Option.TIME_RANGE_TO.getHelp());
        final var gbcTimeRangeToSpinner = new GridBagConstraints();
        gbcTimeRangeToSpinner.fill = GridBagConstraints.HORIZONTAL;
        gbcTimeRangeToSpinner.insets = new Insets(0, 0, 5, 0);
        gbcTimeRangeToSpinner.gridx = 1;
        gbcTimeRangeToSpinner.gridy = rowCounter;
        add(timeRangeToSpinner, gbcTimeRangeToSpinner);

        final var lblLabel = new JLabel(resourceBundle.getString("ui.panel.tracksettings.label.label"));
        final var gbcLabelLabel = new GridBagConstraints();
        gbcLabelLabel.anchor = GridBagConstraints.LINE_END;
        gbcLabelLabel.insets = new Insets(0, 0, 5, 5);
        gbcLabelLabel.gridx = 0;
        gbcLabelLabel.gridy = ++rowCounter;
        add(lblLabel, gbcLabelLabel);

        labelTextField = new JTextArea();
        labelTextField.setToolTipText(Option.LABEL.getHelp());
        lblLabel.setLabelFor(labelTextField);
        final var gbcLabelTextField = new GridBagConstraints();
        gbcLabelTextField.insets = new Insets(0, 0, 5, 0);
        gbcLabelTextField.fill = GridBagConstraints.HORIZONTAL;
        gbcLabelTextField.gridx = 1;
        gbcLabelTextField.gridy = rowCounter;
        add(labelTextField, gbcLabelTextField);
        labelTextField.setColumns(10);
        labelTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(final DocumentEvent documentEvent) {
                labelChanged();
            }

            @Override
            public void insertUpdate(final DocumentEvent documentEvent) {
                labelChanged();
            }

            @Override
            public void changedUpdate(final DocumentEvent documentEvent) {
                labelChanged();
            }

            private void labelChanged() {
                TrackSettingsPanel.this.labelChanged(labelTextField.getText());
            }
        });

        final var lblColor = new JLabel(resourceBundle.getString("ui.panel.tracksettings.color.label"));
        final var gbcLabelColor = new GridBagConstraints();
        gbcLabelColor.anchor = GridBagConstraints.LINE_END;
        gbcLabelColor.insets = new Insets(0, 0, 5, 5);
        gbcLabelColor.gridx = 0;
        gbcLabelColor.gridy = ++rowCounter;
        add(lblColor, gbcLabelColor);

        colorSelector = new ColorSelector();
        colorSelector.setToolTipText(Option.COLOR.getHelp());
        final var gbcColorSelector = new GridBagConstraints();
        gbcColorSelector.insets = new Insets(0, 0, 5, 0);
        gbcColorSelector.fill = GridBagConstraints.BOTH;
        gbcColorSelector.gridx = 1;
        gbcColorSelector.gridy = rowCounter;
        add(colorSelector, gbcColorSelector);

        final var lblPreDrawColor = new JLabel(resourceBundle.getString("ui.panel.tracksettings.predrawcolor.label"));
        final var gbcLabelPreDrawColor = new GridBagConstraints();
        gbcLabelPreDrawColor.anchor = GridBagConstraints.LINE_END;
        gbcLabelPreDrawColor.insets = new Insets(0, 0, 5, 5);
        gbcLabelPreDrawColor.gridx = 0;
        gbcLabelPreDrawColor.gridy = ++rowCounter;
        add(lblPreDrawColor, gbcLabelPreDrawColor);

        preDrawColorSelector = new ColorSelector();
        preDrawColorSelector.setToolTipText(Option.PRE_DRAW_TRACK_COLOR.getHelp());
        final var gbcPreDrawColorSelector = new GridBagConstraints();
        gbcPreDrawColorSelector.insets = new Insets(0, 0, 5, 0);
        gbcPreDrawColorSelector.fill = GridBagConstraints.BOTH;
        gbcPreDrawColorSelector.gridx = 1;
        gbcPreDrawColorSelector.gridy = rowCounter;
        add(preDrawColorSelector, gbcPreDrawColorSelector);

        final var lblLineWidth = new JLabel(resourceBundle.getString("ui.panel.tracksettings.linewidth.label"));
        final var gbcLabelLineWidth = new GridBagConstraints();
        gbcLabelLineWidth.insets = new Insets(0, 0, 5, 5);
        gbcLabelLineWidth.anchor = GridBagConstraints.LINE_END;
        gbcLabelLineWidth.gridx = 0;
        gbcLabelLineWidth.gridy = ++rowCounter;
        add(lblLineWidth, gbcLabelLineWidth);

        lineWidthSpinner = new JSpinner();
        lineWidthSpinner.setToolTipText(Option.LINE_WIDTH.getHelp());
        lineWidthSpinner.setModel(new SpinnerNumberModel(0f, 0f, null, 0.5f));
        final var gbcLineWidthSpinner = new GridBagConstraints();
        gbcLineWidthSpinner.fill = GridBagConstraints.HORIZONTAL;
        gbcLineWidthSpinner.insets = new Insets(0, 0, 5, 0);
        gbcLineWidthSpinner.gridx = 1;
        gbcLineWidthSpinner.gridy = rowCounter;
        add(lineWidthSpinner, gbcLineWidthSpinner);

        final var lblPreDrawLineWidth = new JLabel(resourceBundle.getString("ui.panel.tracksettings.predrawlinewidth.label"));
        final var gbcLabelPreDrawLineWidth = new GridBagConstraints();
        gbcLabelPreDrawLineWidth.insets = new Insets(0, 0, 5, 5);
        gbcLabelPreDrawLineWidth.anchor = GridBagConstraints.LINE_END;
        gbcLabelPreDrawLineWidth.gridx = 0;
        gbcLabelPreDrawLineWidth.gridy = ++rowCounter;
        add(lblPreDrawLineWidth, gbcLabelPreDrawLineWidth);

        preDrawLineWidthSpinner = new JSpinner();
        preDrawLineWidthSpinner.setToolTipText(Option.PRE_DRAW_LINE_WIDTH.getHelp());
        preDrawLineWidthSpinner.setModel(new SpinnerNumberModel(0f, 0f, null, 0.5f));
        final var gbcPreDrawLineWidthSpinner = new GridBagConstraints();
        gbcPreDrawLineWidthSpinner.fill = GridBagConstraints.HORIZONTAL;
        gbcPreDrawLineWidthSpinner.insets = new Insets(0, 0, 5, 0);
        gbcPreDrawLineWidthSpinner.gridx = 1;
        gbcPreDrawLineWidthSpinner.gridy = rowCounter;
        add(preDrawLineWidthSpinner, gbcPreDrawLineWidthSpinner);

        final var lblTimeOffset = new JLabel(resourceBundle.getString("ui.panel.tracksettings.timeoffset.label"));
        final var gbcLabelTimeOffset = new GridBagConstraints();
        gbcLabelTimeOffset.anchor = GridBagConstraints.LINE_END;
        gbcLabelTimeOffset.insets = new Insets(0, 0, 5, 5);
        gbcLabelTimeOffset.gridx = 0;
        gbcLabelTimeOffset.gridy = ++rowCounter;
        add(lblTimeOffset, gbcLabelTimeOffset);

        timeOffsetSpinner = new JSpinner();
        timeOffsetSpinner.setToolTipText(Option.TIME_OFFSET.getHelp());
        timeOffsetSpinner.setModel(new DurationSpinnerModel());
        timeOffsetSpinner.setEditor(new DurationEditor(timeOffsetSpinner));
        final var gbcTimeOffsetSpinner = new GridBagConstraints();
        gbcTimeOffsetSpinner.fill = GridBagConstraints.HORIZONTAL;
        gbcTimeOffsetSpinner.insets = new Insets(0, 0, 5, 0);
        gbcTimeOffsetSpinner.gridx = 1;
        gbcTimeOffsetSpinner.gridy = rowCounter;
        add(timeOffsetSpinner, gbcTimeOffsetSpinner);

        final var lblForcedPointTime = new JLabel(resourceBundle.getString("ui.panel.tracksettings.timepointinterval.label"));
        final var gbcLabelForcedPointTime = new GridBagConstraints();
        gbcLabelForcedPointTime.anchor = GridBagConstraints.LINE_END;
        gbcLabelForcedPointTime.insets = new Insets(0, 0, 5, 5);
        gbcLabelForcedPointTime.gridx = 0;
        gbcLabelForcedPointTime.gridy = ++rowCounter;
        add(lblForcedPointTime, gbcLabelForcedPointTime);

        forcedPointTimeIntervalSpinner = new JSpinner();
        forcedPointTimeIntervalSpinner.setToolTipText(Option.FORCED_POINT_TIME_INTERVAL.getHelp());
        forcedPointTimeIntervalSpinner.setModel(new DurationSpinnerModel());
        forcedPointTimeIntervalSpinner.setEditor(new DurationEditor(forcedPointTimeIntervalSpinner));
        final var gbcForcedPointTimeIntervalSpinner = new GridBagConstraints();
        gbcForcedPointTimeIntervalSpinner.insets = new Insets(0, 0, 5, 0);
        gbcForcedPointTimeIntervalSpinner.fill = GridBagConstraints.HORIZONTAL;
        gbcForcedPointTimeIntervalSpinner.gridx = 1;
        gbcForcedPointTimeIntervalSpinner.gridy = rowCounter;
        add(forcedPointTimeIntervalSpinner, gbcForcedPointTimeIntervalSpinner);

        final var lblTrimGpxStart = new JLabel(resourceBundle.getString("ui.panel.tracksettings.trimstart.label"));
        final var gbcLabelTrimGpxStart = new GridBagConstraints();
        gbcLabelTrimGpxStart.anchor = GridBagConstraints.LINE_END;
        gbcLabelTrimGpxStart.insets = new Insets(0, 0, 5, 5);
        gbcLabelTrimGpxStart.gridx = 0;
        gbcLabelTrimGpxStart.gridy = ++rowCounter;
        add(lblTrimGpxStart, gbcLabelTrimGpxStart);

        trimGpxStartSpinner = new JSpinner();
        trimGpxStartSpinner.setToolTipText(Option.TRIM_GPX_START.getHelp());
        trimGpxStartSpinner.setModel(new DurationSpinnerModel());
        trimGpxStartSpinner.setEditor(new DurationEditor(trimGpxStartSpinner));
        final var gbcTrimGpxStartSpinner = new GridBagConstraints();
        gbcTrimGpxStartSpinner.fill = GridBagConstraints.HORIZONTAL;
        gbcTrimGpxStartSpinner.insets = new Insets(0, 0, 5, 0);
        gbcTrimGpxStartSpinner.gridx = 1;
        gbcTrimGpxStartSpinner.gridy = rowCounter;
        add(trimGpxStartSpinner, gbcTrimGpxStartSpinner);

        final var lblTrimGpxEnd = new JLabel(resourceBundle.getString("ui.panel.tracksettings.trimend.label"));
        final var gbcLabelTrimGpxEnd = new GridBagConstraints();
        gbcLabelTrimGpxEnd.anchor = GridBagConstraints.LINE_END;
        gbcLabelTrimGpxEnd.insets = new Insets(0, 0, 5, 5);
        gbcLabelTrimGpxEnd.gridx = 0;
        gbcLabelTrimGpxEnd.gridy = ++rowCounter;
        add(lblTrimGpxEnd, gbcLabelTrimGpxEnd);

        trimGpxEndSpinner = new JSpinner();
        trimGpxEndSpinner.setToolTipText(Option.TRIM_GPX_END.getHelp());
        trimGpxEndSpinner.setModel(new DurationSpinnerModel());
        trimGpxEndSpinner.setEditor(new DurationEditor(trimGpxEndSpinner));
        final var gbcTrimGpxEndSpinner = new GridBagConstraints();
        gbcTrimGpxEndSpinner.fill = GridBagConstraints.HORIZONTAL;
        gbcTrimGpxEndSpinner.insets = new Insets(0, 0, 5, 0);
        gbcTrimGpxEndSpinner.gridx = 1;
        gbcTrimGpxEndSpinner.gridy = rowCounter;
        add(trimGpxEndSpinner, gbcTrimGpxEndSpinner);

        travelIconComboBox = new JComboBox<>(TrackIcon.getAllTrackIcons());
        travelIconComboBox.setToolTipText(Option.TRACK_ICON.getHelp());
        travelIconComboBox.setEditable(false);
        final var gbcTrackIconComboBox = new GridBagConstraints();
        gbcTrackIconComboBox.fill = GridBagConstraints.HORIZONTAL;
        gbcTrackIconComboBox.insets = new Insets(0, 0, 5, 0);
        gbcTrackIconComboBox.gridx = 1;
        gbcTrackIconComboBox.gridy = ++rowCounter;
        add(travelIconComboBox, gbcTrackIconComboBox);
        travelIconComboBox.setPreferredSize(new Dimension(10, travelIconComboBox.getPreferredSize().height));

        final var lblSelectTravelIcon = new JLabel(resourceBundle.getString("ui.panel.tracksettings.icon.label"));
        final var gbcSelectTravelIcon = new GridBagConstraints();
        gbcSelectTravelIcon.anchor = GridBagConstraints.LINE_END;
        gbcSelectTravelIcon.insets = new Insets(0, 0, 5, 5);
        gbcSelectTravelIcon.gridx = 0;
        gbcSelectTravelIcon.gridy = rowCounter;
        add(lblSelectTravelIcon, gbcSelectTravelIcon);

        final var lblIcon = new JLabel(resourceBundle.getString("ui.panel.tracksettings.iconfile.label"));
        final var gbcLabelIcon = new GridBagConstraints();
        gbcLabelIcon.insets = new Insets(0, 0, 5, 5);
        gbcLabelIcon.anchor = GridBagConstraints.LINE_END;
        gbcLabelIcon.gridx = 0;
        gbcLabelIcon.gridy = ++rowCounter;
        add(lblIcon, gbcLabelIcon);

        travelIconFileSelector = new FileSelector(FILES_ONLY) {
            @Serial
            private static final long serialVersionUID = -7085193817022374995L;

            @Override
            protected Type configure(final JFileChooser iconFileChooser) {
                configureIconFileChooser(resourceBundle, iconFileChooser);
                return Type.OPEN;
            }
        };

        travelIconFileSelector.setToolTipText(Option.TRACK_ICON_FILE.getHelp());
        lblIcon.setLabelFor(travelIconFileSelector);
        final var gbcInputIconFileSelector = new GridBagConstraints();
        gbcInputIconFileSelector.insets = new Insets(0, 0, 5, 0);
        gbcInputIconFileSelector.fill = GridBagConstraints.BOTH;
        gbcInputIconFileSelector.gridx = 1;
        gbcInputIconFileSelector.gridy = rowCounter;
        add(travelIconFileSelector, gbcInputIconFileSelector);

        final var lblMirrorIcon = new JLabel(resourceBundle.getString("ui.panel.tracksettings.icon.mirror.label"));
        final var gbcLabelMirrorIcon = new GridBagConstraints();
        gbcLabelMirrorIcon.anchor = GridBagConstraints.LINE_END;
        gbcLabelMirrorIcon.insets = new Insets(0, 0, 5, 5);
        gbcLabelMirrorIcon.gridx = 0;
        gbcLabelMirrorIcon.gridy = ++rowCounter;
        add(lblMirrorIcon, gbcLabelMirrorIcon);

        travelMirrorCheckBox = new JCheckBox("");
        travelMirrorCheckBox.setToolTipText(Option.TRACK_ICON_MIRROR.getHelp());
        final var gbcMirrorIconCheckBox = new GridBagConstraints();
        gbcMirrorIconCheckBox.fill = GridBagConstraints.HORIZONTAL;
        gbcMirrorIconCheckBox.insets = new Insets(0, 0, 5, 5);
        gbcMirrorIconCheckBox.gridx = 1;
        gbcMirrorIconCheckBox.gridy = rowCounter;
        add(travelMirrorCheckBox, gbcMirrorIconCheckBox);

        trackEndIconComboBox = new JComboBox<>(TrackIcon.getAllTrackIcons());
        trackEndIconComboBox.setToolTipText(Option.TRACK_ICON.getHelp());
        trackEndIconComboBox.setEditable(false);
        final var gbcTrackEndIconComboBox = new GridBagConstraints();
        gbcTrackEndIconComboBox.fill = GridBagConstraints.HORIZONTAL;
        gbcTrackEndIconComboBox.insets = new Insets(0, 0, 5, 0);
        gbcTrackEndIconComboBox.gridx = 1;
        gbcTrackEndIconComboBox.gridy = ++rowCounter;
        add(trackEndIconComboBox, gbcTrackEndIconComboBox);
        trackEndIconComboBox.setPreferredSize(new Dimension(10, trackEndIconComboBox.getPreferredSize().height));

        final var lblSelectTrackEndIcon = new JLabel(resourceBundle.getString("ui.panel.tracksettings.icon.end.label"));
        final var gbcSelectTravelEndIcon = new GridBagConstraints();
        gbcSelectTravelEndIcon.anchor = GridBagConstraints.LINE_END;
        gbcSelectTravelEndIcon.insets = new Insets(0, 0, 5, 5);
        gbcSelectTravelEndIcon.gridx = 0;
        gbcSelectTravelEndIcon.gridy = rowCounter;
        add(lblSelectTrackEndIcon, gbcSelectTravelEndIcon);

        final var lblIconEnd = new JLabel(resourceBundle.getString("ui.panel.tracksettings.iconfile.label"));
        final var gbcLabelEndIcon = new GridBagConstraints();
        gbcLabelEndIcon.insets = new Insets(0, 0, 5, 5);
        gbcLabelEndIcon.anchor = GridBagConstraints.LINE_END;
        gbcLabelEndIcon.gridx = 0;
        gbcLabelEndIcon.gridy = ++rowCounter;
        add(lblIconEnd, gbcLabelEndIcon);

        trackEndIconFileSelector = new FileSelector(FILES_ONLY) {
            @Serial
            private static final long serialVersionUID = -7085193817022374995L;

            @Override
            protected Type configure(final JFileChooser iconFileChooser) {
                configureIconFileChooser(resourceBundle, iconFileChooser);
                return Type.OPEN;
            }
        };

        trackEndIconFileSelector.setToolTipText(Option.TRACK_ICON_FILE.getHelp());
        lblIconEnd.setLabelFor(trackEndIconFileSelector);
        final var gbcTrackEndIconFileSelector = new GridBagConstraints();
        gbcTrackEndIconFileSelector.insets = new Insets(0, 0, 5, 0);
        gbcTrackEndIconFileSelector.fill = GridBagConstraints.BOTH;
        gbcTrackEndIconFileSelector.gridx = 1;
        gbcTrackEndIconFileSelector.gridy = rowCounter;
        add(trackEndIconFileSelector, gbcTrackEndIconFileSelector);

        final var lblMirrorITrackEndIcon = new JLabel(resourceBundle.getString("ui.panel.tracksettings.icon.end.mirror.label"));
        final var gbcLabelMirrorTrackEndIcon = new GridBagConstraints();
        gbcLabelMirrorTrackEndIcon.anchor = GridBagConstraints.LINE_END;
        gbcLabelMirrorTrackEndIcon.insets = new Insets(0, 0, 5, 5);
        gbcLabelMirrorTrackEndIcon.gridx = 0;
        gbcLabelMirrorTrackEndIcon.gridy = ++rowCounter;
        add(lblMirrorITrackEndIcon, gbcLabelMirrorTrackEndIcon);

        trackEndMirrorCheckBox = new JCheckBox("");
        trackEndMirrorCheckBox.setToolTipText(Option.TRACK_ICON_MIRROR.getHelp());
        final var gbcMirrorIconTrackEndCheckBox = new GridBagConstraints();
        gbcMirrorIconTrackEndCheckBox.fill = GridBagConstraints.HORIZONTAL;
        gbcMirrorIconTrackEndCheckBox.insets = new Insets(0, 0, 5, 5);
        gbcMirrorIconTrackEndCheckBox.gridx = 1;
        gbcMirrorIconTrackEndCheckBox.gridy = rowCounter;
        add(trackEndMirrorCheckBox, gbcMirrorIconTrackEndCheckBox);

        final var btnRemoveButton = new JButton(resourceBundle.getString("ui.panel.tracksettings.button.remove"));
        btnRemoveButton.addActionListener(e -> remove());
        final var gbcButtonRemoveButton = new GridBagConstraints();
        gbcButtonRemoveButton.anchor = GridBagConstraints.LINE_END;
        gbcButtonRemoveButton.gridwidth = 3;
        gbcButtonRemoveButton.insets = new Insets(0, 0, 0, 5);
        gbcButtonRemoveButton.gridx = 0;
        gbcButtonRemoveButton.gridy = ++rowCounter;
        add(btnRemoveButton, gbcButtonRemoveButton);

        final PropertyChangeListener propertyChangeListener = evt -> configurationChanged();
        inputGpxFileSelector.addPropertyChangeListener(FileSelector.PROPERTY_FILENAME, propertyChangeListener);

        labelTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(final DocumentEvent e) {
                configurationChanged();
            }

            @Override
            public void insertUpdate(final DocumentEvent e) {
                configurationChanged();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                configurationChanged();
            }
        });

        colorSelector.addPropertyChangeListener(ColorSelector.PROPERTY_COLOR, propertyChangeListener);
        preDrawColorSelector.addPropertyChangeListener(ColorSelector.PROPERTY_COLOR, propertyChangeListener);

        final ChangeListener changeListener = e -> configurationChanged();
        final ItemListener itemListener = e -> configurationChanged();

        lineWidthSpinner.addChangeListener(changeListener);
        preDrawLineWidthSpinner.addChangeListener(changeListener);
        timeOffsetSpinner.addChangeListener(changeListener);
        forcedPointTimeIntervalSpinner.addChangeListener(changeListener);
        trimGpxStartSpinner.addChangeListener(changeListener);
        trimGpxEndSpinner.addChangeListener(changeListener);
        travelIconComboBox.addItemListener(itemListener);
        travelMirrorCheckBox.addChangeListener(changeListener);
        trackEndIconComboBox.addItemListener(itemListener);
        trackEndMirrorCheckBox.addChangeListener(changeListener);

        inputGpxFileSelector.addPropertyChangeListener(FileSelector.PROPERTY_FILENAME, evt -> readGpxTimeRange());
        timeZoneComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                timeRangeChanged();
            }
        });
        timeRangeFromCheckBox.addItemListener(e -> {
            if (timeRangeFromCheckBox.isSelected() && !timeRangeFromValueSet && timeRangeStatus == TimeRangeStatus.DETECTED) {
                timeRangeFromSpinner.setValue(epochToSpinnerValue(detectedTimeRange.first()));
            }
            timeRangeFromSpinner.setEnabled(timeRangeFromCheckBox.isSelected());
            timeRangeChanged();
        });
        timeRangeFromSpinner.addChangeListener(e -> {
            timeRangeFromValueSet = true;
            timeRangeChanged();
        });
        timeRangeToCheckBox.addItemListener(e -> {
            if (timeRangeToCheckBox.isSelected() && !timeRangeToValueSet && timeRangeStatus == TimeRangeStatus.DETECTED) {
                timeRangeToSpinner.setValue(epochToSpinnerValue(detectedTimeRange.last()));
            }
            timeRangeToSpinner.setEnabled(timeRangeToCheckBox.isSelected());
            timeRangeChanged();
        });
        timeRangeToSpinner.addChangeListener(e -> {
            timeRangeToValueSet = true;
            timeRangeChanged();
        });
    }

    public static void configureGpxFileChooser(final ResourceBundle resourceBundle, final JFileChooser fileChooser) {
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.addChoosableFileFilter(new FileFilter() {
            @Override
            public String getDescription() {
                return resourceBundle.getString("ui.panel.tracksettings.gpxfile.format.all");
            }

            @Override
            public boolean accept(final File f) {
                final var filename = f.getName().toLowerCase(Locale.getDefault());
                return f.isDirectory() || filename.endsWith(".gpx") || filename.endsWith(".gpx.gz"); //NON-NLS
            }
        });
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(
                resourceBundle.getString("ui.panel.tracksettings.gpxfile.format.gpx"), "gpx")); //NON-NLS
        fileChooser.addChoosableFileFilter(new FileFilter() {
            @Override
            public String getDescription() {
                return resourceBundle.getString("ui.panel.tracksettings.gpxfile.format.gpxgz");
            }

            @Override
            public boolean accept(final File f) {
                final var filename = f.getName().toLowerCase(Locale.getDefault());
                return f.isDirectory() || filename.endsWith(".gpx.gz"); //NON-NLS
            }
        });
    }

    public static void configureIconFileChooser(final ResourceBundle resourceBundle, final JFileChooser fileChooser) {
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.addChoosableFileFilter(new FileFilter() {
            @Override
            public String getDescription() {
                return resourceBundle.getString("ui.panel.tracksettings.iconfile.format.all");
            }

            @Override
            public boolean accept(final File f) {
                final var filename = f.getName().toLowerCase(Locale.getDefault());
                return f.isDirectory() || filename.endsWith(".png"); //NON-NLS
            }
        });
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(
                resourceBundle.getString("ui.panel.tracksettings.iconfile.format.png"), "png")); //NON-NLS
    }

    protected abstract void labelChanged(String label);


    public TrackConfiguration createConfiguration() {
        final var b = TrackConfiguration.createBuilder();
        final var zone = getSelectedZone();

        b.inputGpx(new File(inputGpxFileSelector.getFilename()))
                .label(labelTextField.getText())
                .color(colorSelector.getColor())
                .preDrawTrackColor(preDrawColorSelector.getColor())
                .lineWidth((Float) lineWidthSpinner.getValue())
                .preDrawLineWidth((Float) preDrawLineWidthSpinner.getValue())
                .forcedPointInterval((Long) forcedPointTimeIntervalSpinner.getValue())
                .timeOffset((Long) timeOffsetSpinner.getValue())
                .trimGpxStart((Long) trimGpxStartSpinner.getValue())
                .trimGpxEnd((Long) trimGpxEndSpinner.getValue())
                .timeRangeZone(zone.getId())
                .timeRangeFrom(getTimeRangeMillis(timeRangeFromCheckBox, timeRangeFromSpinner, zone))
                .timeRangeTo(getTimeRangeMillis(timeRangeToCheckBox, timeRangeToSpinner, zone))
                .trackIcon((TrackIcon) travelIconComboBox.getSelectedItem())
                .inputIcon(new File(travelIconFileSelector.getFilename()))
                .mirrorTrackIcon(travelMirrorCheckBox.isSelected())
                .trackEndIcon((TrackIcon) trackEndIconComboBox.getSelectedItem())
                .inputEndIcon(new File(trackEndIconFileSelector.getFilename()))
                .mirrorTrackEndIcon(trackEndMirrorCheckBox.isSelected());
        return b.build();
    }


    public void setConfiguration(final TrackConfiguration c) {
        inputGpxFileSelector.setFilename(c.getInputGpx() == null ? null : c.getInputGpx().toString());
        labelTextField.setText(c.getLabel());
        colorSelector.setColor(c.getColor());
        preDrawColorSelector.setColor(c.getPreDrawTrackColor());
        lineWidthSpinner.setValue(c.getLineWidth());
        preDrawLineWidthSpinner.setValue(c.getPreDrawLineWidth());
        forcedPointTimeIntervalSpinner.setValue(c.getForcedPointInterval());
        timeOffsetSpinner.setValue(c.getTimeOffset());
        trimGpxStartSpinner.setValue(c.getTrimGpxStart());
        trimGpxEndSpinner.setValue(c.getTrimGpxEnd());
        final var zone = DateUtil.toZoneIdOrDefault(c.getTimeRangeZone());
        selectTimeZone(zone);
        setTimeRangeValue(timeRangeFromCheckBox, timeRangeFromSpinner, c.getTimeRangeFrom(), zone);
        setTimeRangeValue(timeRangeToCheckBox, timeRangeToSpinner, c.getTimeRangeTo(), zone);
        travelIconComboBox.setSelectedItem(c.getTrackIcon());
        travelIconFileSelector.setFilename(c.getInputIcon() == null ? null : c.getInputIcon().toString());
        travelMirrorCheckBox.setSelected(c.isTrackIconMirrored());
        trackEndIconComboBox.setSelectedItem(c.getTrackEndIcon());
        trackEndIconFileSelector.setFilename(c.getInputEndIcon() == null ? null : c.getInputEndIcon().toString());
        trackEndMirrorCheckBox.setSelected(c.isMirrorTrackEndIcon());
        labelChanged(c.getLabel());
    }

    private void readGpxTimeRange() {
        if (timeRangeReader != null) {
            timeRangeReader.cancel(true);
        }
        timeRangeReaderGeneration++;
        final var generation = timeRangeReaderGeneration;

        final var filename = inputGpxFileSelector.getFilename();
        if (filename == null || filename.isBlank() || !new File(filename).isFile()) {
            timeRangeStatus = TimeRangeStatus.NO_FILE;
            updateTimeRangeLabel();
            return;
        }

        final var gpxFile = new File(filename);
        timeRangeStatus = TimeRangeStatus.READING;
        updateTimeRangeLabel();

        final var worker = new SwingWorker<Optional<GpxParser.TimeRange>, Void>() {
            @Override
            protected Optional<GpxParser.TimeRange> doInBackground() throws UserException {
                return GpxParser.readTrackTimeRange(gpxFile);
            }

            @Override
            protected void done() {
                if (isCancelled() || generation != timeRangeReaderGeneration) {
                    return;
                }
                try {
                    final var timeRange = get();
                    if (timeRange.isPresent()) {
                        detectedTimeRange = timeRange.get();
                        timeRangeStatus = TimeRangeStatus.DETECTED;
                    } else {
                        timeRangeStatus = TimeRangeStatus.NO_TIMESTAMPS;
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    timeRangeStatus = TimeRangeStatus.UNREADABLE;
                } catch (final ExecutionException e) {
                    LOGGER.warn("Unable to read the time range of the GPX file \"{}\"", gpxFile.getName(), e.getCause());
                    timeRangeStatus = TimeRangeStatus.UNREADABLE;
                }
                updateTimeRangeLabel();
            }
        };
        timeRangeReader = worker;
        worker.execute();
    }

    private void timeRangeChanged() {
        updateTimeRangeLabel();
        configurationChanged();
    }

    private void updateTimeRangeLabel() {
        gpxTimeRangeLabel.setText(switch (timeRangeStatus) {
            case NO_FILE -> "";
            case READING -> resourceBundle.getString("ui.panel.tracksettings.timerange.reading");
            case UNREADABLE -> resourceBundle.getString("ui.panel.tracksettings.timerange.unreadable");
            case NO_TIMESTAMPS -> resourceBundle.getString("ui.panel.tracksettings.timerange.notimestamps");
            case DETECTED -> describeTimeRange(detectedTimeRange);
        });
    }

    private String describeTimeRange(final GpxParser.TimeRange fileTimeRange) {
        final var zone = getSelectedZone();
        final var fileTimeRangeText = formatTimeRange(fileTimeRange.first(), fileTimeRange.last(), zone);

        final var from = getTimeRangeMillis(timeRangeFromCheckBox, timeRangeFromSpinner, zone);
        final var to = getTimeRangeMillis(timeRangeToCheckBox, timeRangeToSpinner, zone);
        if (from == null && to == null) {
            return fileTimeRangeText;
        }

        final var first = from == null ? fileTimeRange.first() : Math.max(from, fileTimeRange.first());
        final var last = to == null ? fileTimeRange.last() : Math.min(to, fileTimeRange.last());
        final var usedTimeRangeText = first > last
                ? resourceBundle.getString("ui.panel.tracksettings.timerange.nooverlap")
                : resourceBundle.getString("ui.panel.tracksettings.timerange.using").formatted(formatTimeRange(first, last, zone));
        return "<html>%s<br>%s</html>".formatted(escapeHtml(fileTimeRangeText), escapeHtml(usedTimeRangeText)); //NON-NLS
    }

    private static String formatTimeRange(final long first, final long last, final ZoneId zone) {
        final var start = DateUtil.toLocalDateTime(first, zone);
        final var end = DateUtil.toLocalDateTime(last, zone);
        final var endFormatter = start.toLocalDate().equals(end.toLocalDate()) ? TIME_FORMATTER : DATE_TIME_FORMATTER;
        return "%s – %s".formatted(DATE_TIME_FORMATTER.format(start), endFormatter.format(end));
    }

    private static String escapeHtml(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); //NON-NLS
    }

    private ZoneId getSelectedZone() {
        return timeZoneComboBox.getSelectedItem() instanceof final TimeZoneItem item ? item.zoneId() : ZoneId.systemDefault();
    }

    private void selectTimeZone(final ZoneId zone) {
        for (var i = 0; i < timeZoneComboBox.getItemCount(); i++) {
            final var item = timeZoneComboBox.getItemAt(i);
            if (item.zoneId().equals(zone)) {
                timeZoneComboBox.setSelectedItem(item);
                return;
            }
        }
        final var item = new TimeZoneItem(zone, formatTimeZone(zone, Instant.now()));
        timeZoneComboBox.insertItemAt(item, 0);
        timeZoneComboBox.setSelectedItem(item);
    }

    private static TimeZoneItem[] createTimeZoneItems() {
        final var now = Instant.now();
        return ZoneId.getAvailableZoneIds().stream()
                .sorted()
                .map(ZoneId::of)
                .map(zone -> new TimeZoneItem(zone, formatTimeZone(zone, now)))
                .toArray(TimeZoneItem[]::new);
    }

    private static String formatTimeZone(final ZoneId zone, final Instant now) {
        final var offset = zone.getRules().getOffset(now);
        return "%s (UTC%s)".formatted(zone.getId(), ZoneOffset.UTC.equals(offset) ? "+00:00" : offset.getId()); //NON-NLS
    }

    private static JSpinner createDateTimeSpinner() {
        final var spinner = new JSpinner(new WallClockSpinnerModel());
        final var editor = new JSpinner.DateEditor(spinner, DATE_TIME_PATTERN);
        editor.getFormat().setTimeZone(UTC_TIME_ZONE);
        spinner.setEditor(editor);
        spinner.setValue(wallClockToSpinnerValue(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
        spinner.setEnabled(false);
        return spinner;
    }

    private static void setTimeRangeValue(final JCheckBox checkBox, final JSpinner spinner, final Long epochMillis, final ZoneId zone) {
        if (epochMillis != null) {
            spinner.setValue(wallClockToSpinnerValue(DateUtil.toLocalDateTime(epochMillis, zone)));
        }
        checkBox.setSelected(epochMillis != null);
    }

    private static Long getTimeRangeMillis(final JCheckBox checkBox, final JSpinner spinner, final ZoneId zone) {
        if (!checkBox.isSelected()) {
            return null;
        }
        final var wallClock = LocalDateTime.ofInstant(((Date) spinner.getValue()).toInstant(), ZoneOffset.UTC);
        return DateUtil.toEpochMillis(wallClock, zone);
    }

    private Date epochToSpinnerValue(final long epochMillis) {
        return wallClockToSpinnerValue(DateUtil.toLocalDateTime(epochMillis, getSelectedZone()));
    }

    // the spinners edit a wall-clock time, so their date values are encoded in UTC and independent of any time zone
    private static Date wallClockToSpinnerValue(final LocalDateTime wallClock) {
        return Date.from(wallClock.toInstant(ZoneOffset.UTC));
    }


    protected abstract void remove();

    protected abstract void configurationChanged();

    private enum TimeRangeStatus { NO_FILE, READING, UNREADABLE, NO_TIMESTAMPS, DETECTED }

    private record TimeZoneItem(ZoneId zoneId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final class WallClockSpinnerModel extends SpinnerDateModel {
        @Serial
        private static final long serialVersionUID = -3326472851964123367L;

        @Override
        public Object getNextValue() {
            return add(1);
        }

        @Override
        public Object getPreviousValue() {
            return add(-1);
        }

        private Date add(final int amount) {
            final var calendar = Calendar.getInstance(UTC_TIME_ZONE, Locale.ROOT);
            calendar.setTime(getDate());
            calendar.add(getCalendarField(), amount);
            return calendar.getTime();
        }
    }

}
