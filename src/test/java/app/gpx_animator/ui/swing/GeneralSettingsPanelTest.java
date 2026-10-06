package app.gpx_animator.ui.swing;

import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralSettingsPanelTest {

    @Test
    void filtersLocalesWhileTypingAndCommitsAnExactCode() throws InterruptedException, InvocationTargetException {
        final var panel = new AtomicReference<GeneralSettingsPanel>();
        final var combo = new AtomicReference<JComboBox<?>>();
        final var editor = new AtomicReference<JTextField>();
        final var fullSize = new AtomicReference<Integer>();

        SwingUtilities.invokeAndWait(() -> {
            panel.set(newPanel());
            final var localeCombo = informationLocaleCombo(panel.get());
            combo.set(localeCombo);
            editor.set((JTextField) localeCombo.getEditor().getEditorComponent());
            fullSize.set(localeCombo.getItemCount());
            editor.get().setText("English");
        });
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(combo.get().getItemCount() > 0);
            assertTrue(combo.get().getItemCount() < fullSize.get());
            editor.get().setText("zh-TW");
        });

        final var locale = new AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            editor.get().postActionEvent();
            locale.set(((InformationLocaleCombo) combo.get()).getLanguageTag());
        });
        assertEquals("zh-TW", locale.get());
    }

    private static GeneralSettingsPanel newPanel() {
        return new GeneralSettingsPanel() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void configurationChanged() {
                // not needed for this test
            }
        };
    }

    private static JComboBox<?> informationLocaleCombo(final GeneralSettingsPanel panel) {
        JComboBox<?> found = null;
        for (final var component : panel.getComponents()) {
            if (component instanceof JComboBox<?> combo && combo.isEditable() && combo.getItemCount() > 50) {
                found = combo;
            }
        }
        assertNotNull(found, "information locale combo");
        return found;
    }

}
