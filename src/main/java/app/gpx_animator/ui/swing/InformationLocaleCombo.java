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

import org.jetbrains.annotations.Nullable;

import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Editable locale list. Typing filters by language code, the local name, or the English name.
 * Entries are ordered by language code, with the system locale kept first.
 */
final class InformationLocaleCombo extends JComboBox<InformationLocaleCombo.Choice> {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int POPUP_ROWS = 16;
    private static final int MAX_POPUP_WIDTH = 480;

    private final transient List<Choice> choices = new ArrayList<>();
    private final transient Runnable onChange;

    private transient Choice committed;
    private transient boolean updating;
    private transient boolean filterQueued;

    InformationLocaleCombo(final String systemLabelFormat, final String toolTip, final Runnable onChange) {
        this.onChange = onChange;
        setEditable(true);
        setMaximumRowCount(POPUP_ROWS);
        setToolTipText(toolTip);
        fillChoices(systemLabelFormat);
        installSearch();
    }

    void setLanguageTag(@Nullable final String languageTag) {
        final var requested = canonicalTag(languageTag);
        var choice = choices.stream()
                .filter(candidate -> candidate.languageTag.equalsIgnoreCase(requested))
                .findFirst()
                .orElse(null);
        if (choice == null) {
            choice = new Choice(requested, requested, "");
            choices.add(choice);
        }
        committed = choice;
        showChoices(choices, choice, false);
    }

    String getLanguageTag() {
        return committed == null ? "" : committed.languageTag;
    }

    private void fillChoices(final String systemLabelFormat) {
        final var systemLocale = Locale.getDefault();
        choices.add(new Choice("",
                systemLabelFormat.formatted(systemLocale.getDisplayName(systemLocale)),
                systemLocale.getDisplayName(Locale.ENGLISH)));

        final var byTag = new LinkedHashMap<String, Locale>();
        for (final var locale : Locale.getAvailableLocales()) {
            if (locale.getLanguage().isEmpty() || !locale.getVariant().isEmpty()) {
                continue;
            }
            byTag.putIfAbsent(locale.toLanguageTag(), locale);
        }
        final var displayLocale = Locale.getDefault();
        byTag.values().stream()
                .sorted(Comparator.comparing(Locale::toLanguageTag, String.CASE_INSENSITIVE_ORDER))
                .map(locale -> Choice.forLocale(locale, displayLocale))
                .forEach(choices::add);
        committed = choices.getFirst();
        showChoices(choices, committed, false);
    }

    private void installSearch() {
        final var editor = editor();
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(final DocumentEvent event) {
                queueFilter();
            }

            @Override
            public void removeUpdate(final DocumentEvent event) {
                queueFilter();
            }

            @Override
            public void changedUpdate(final DocumentEvent event) {
                queueFilter();
            }
        });
        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent event) {
                commitEditor();
            }
        });
        editor.addActionListener(event -> acceptSearch());
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(final KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_DOWN) {
                    movePopupSelection(1);
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_UP) {
                    movePopupSelection(-1);
                    event.consume();
                }
            }
        });
        addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(final PopupMenuEvent event) {
                restoreFullListForBrowsing();
                widenPopup();
            }

            @Override
            public void popupMenuWillBecomeInvisible(final PopupMenuEvent event) {
                // The typed text is committed when the editor loses focus.
            }

            @Override
            public void popupMenuCanceled(final PopupMenuEvent event) {
                // The typed text is committed when the editor loses focus.
            }
        });
        addItemListener(event -> {
            if (updating || event.getStateChange() != ItemEvent.SELECTED) {
                return;
            }
            if (event.getItem() instanceof Choice choice && !choice.equals(committed)) {
                committed = choice;
                onChange.run();
            }
        });
    }

    private void queueFilter() {
        if (updating || filterQueued) {
            return;
        }
        filterQueued = true;
        SwingUtilities.invokeLater(this::applyFilter);
    }

    private void applyFilter() {
        filterQueued = false;
        if (updating) {
            return;
        }
        final var text = editor().getText();
        if (getSelectedItem() instanceof Choice selected && selected.toString().equals(text)) {
            if (!selected.equals(committed)) {
                committed = selected;
                onChange.run();
            }
            return;
        }
        if (committed != null && committed.toString().equals(text)) {
            return;
        }
        final var query = text.trim().toLowerCase(Locale.ROOT);
        final var matches = choices.stream().filter(choice -> choice.matches(query)).toList();
        showChoices(matches, null, true);
    }

    private void restoreFullListForBrowsing() {
        if (updating || getItemCount() == choices.size()) {
            return;
        }
        if (committed != null && committed.toString().equals(editor().getText())) {
            showChoices(choices, committed, true);
        }
    }

    private void commitEditor() {
        if (updating) {
            return;
        }
        final var text = editor().getText().trim();
        final var match = choices.stream()
                .filter(choice -> choice.toString().equalsIgnoreCase(text)
                        || choice.languageTag.equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
        if (match == null) {
            showChoices(choices, committed, false);
            return;
        }
        commit(match);
    }

    private void acceptSearch() {
        if (updating) {
            return;
        }
        final var list = popupList();
        if (isPopupVisible() && list != null && list.getSelectedIndex() >= 0
                && list.getSelectedValue() instanceof Choice highlighted) {
            commit(highlighted);
            hidePopup();
            return;
        }
        commitEditor();
        hidePopup();
    }

    private void commit(final Choice choice) {
        final var changed = !choice.equals(committed);
        committed = choice;
        showChoices(choices, choice, false);
        if (changed) {
            onChange.run();
        }
    }

    private void movePopupSelection(final int delta) {
        if (!isPopupVisible() && isShowing()) {
            showPopup();
        }
        final var list = popupList();
        if (list == null || list.getModel().getSize() == 0) {
            return;
        }
        final var next = Math.max(0, Math.min(list.getModel().getSize() - 1, list.getSelectedIndex() + delta));
        list.setSelectedIndex(next);
        list.ensureIndexIsVisible(next);
    }

    private JList<?> popupList() {
        final var child = getAccessibleContext().getAccessibleChild(0);
        if (!(child instanceof JPopupMenu popup) || popup.getComponentCount() == 0
                || !(popup.getComponent(0) instanceof JScrollPane scroll)
                || !(scroll.getViewport().getView() instanceof JList<?> list)) {
            return null;
        }
        return list;
    }

    private void widenPopup() {
        final var child = getAccessibleContext().getAccessibleChild(0);
        if (!(child instanceof JPopupMenu popup) || popup.getComponentCount() == 0
                || !(popup.getComponent(0) instanceof JScrollPane scroll)) {
            return;
        }
        final var listWidth = scroll.getViewport().getView().getPreferredSize().width;
        final var scrollbarWidth = scroll.getVerticalScrollBar().getPreferredSize().width;
        final var width = Math.max(getWidth(), Math.min(listWidth + scrollbarWidth, MAX_POPUP_WIDTH));
        final var size = new Dimension(width, popup.getPreferredSize().height);
        popup.setPreferredSize(size);
        scroll.setPreferredSize(size);
    }

    private void showChoices(final List<Choice> visible, @Nullable final Choice selected, final boolean showPopup) {
        if (updating) {
            return;
        }
        updating = true;
        final var editor = editor();
        final var text = editor.getText();
        final var caret = editor.getCaretPosition();
        hidePopup();
        removeAllItems();
        visible.forEach(this::addItem);
        if (selected == null) {
            setSelectedIndex(-1);
            editor.setText(text);
            editor.setCaretPosition(Math.min(Math.max(caret, 0), text.length()));
        } else {
            setSelectedItem(selected);
        }
        updating = false;
        if (showPopup && isShowing()) {
            showPopup();
            if (selected == null) {
                final var list = popupList();
                if (list != null && list.getModel().getSize() > 0) {
                    list.setSelectedIndex(0);
                    list.ensureIndexIsVisible(0);
                }
            }
        }
    }

    private JTextField editor() {
        return (JTextField) getEditor().getEditorComponent();
    }

    private static String canonicalTag(@Nullable final String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return "";
        }
        final var parsed = Locale.forLanguageTag(languageTag.trim().replace('_', '-'));
        if (parsed.getLanguage().isEmpty()) {
            return languageTag.trim();
        }
        return parsed.toLanguageTag();
    }

    static final class Choice implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final String LABEL_SEPARATOR = " — ";

        private final String languageTag;
        private final String label;
        private final String searchText;

        private Choice(final String languageTag, final String label, final String extraSearchText) {
            this.languageTag = languageTag;
            this.label = label;
            this.searchText = (label + " " + languageTag + " " + extraSearchText).toLowerCase(Locale.ROOT);
        }

        private static Choice forLocale(final Locale locale, final Locale displayLocale) {
            final var tag = locale.toLanguageTag();
            final var displayName = locale.getDisplayName(displayLocale);
            final var label = displayName.equalsIgnoreCase(tag) ? tag : tag + LABEL_SEPARATOR + displayName;
            return new Choice(tag, label, locale.getDisplayName(Locale.ENGLISH));
        }

        private boolean matches(final String query) {
            return query.isEmpty() || searchText.contains(query);
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Choice choice && languageTag.equals(choice.languageTag);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(languageTag);
        }
    }

}
