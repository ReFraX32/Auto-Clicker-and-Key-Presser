import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

public class AutoClickerAppTest {

    @Test
    public void testCalculateMillisecondsFromIntegers() {
        assertEquals(0, AutoClickerApp.calculateMilliseconds(0, 0, 0, 0));
        assertEquals(3600000, AutoClickerApp.calculateMilliseconds(1, 0, 0, 0));
        assertEquals(120000, AutoClickerApp.calculateMilliseconds(0, 2, 0, 0));
        assertEquals(5000, AutoClickerApp.calculateMilliseconds(0, 0, 5, 0));
        assertEquals(500, AutoClickerApp.calculateMilliseconds(0, 0, 0, 500));

        // Combined: 1h 2m 3s 450ms
        int expected = (1 * 3600 + 2 * 60 + 3) * 1000 + 450;
        assertEquals(expected, AutoClickerApp.calculateMilliseconds(1, 2, 3, 450));
    }

    @Test
    public void testCalculateMillisecondsFromStrings() {
        // Zero values
        assertEquals(0, AutoClickerApp.calculateMilliseconds("0", "0", "0", "0"));

        // Hold duration tests
        assertEquals(1500, AutoClickerApp.calculateMilliseconds("0", "0", "1", "500"));
        assertEquals(3000, AutoClickerApp.calculateMilliseconds("0", "0", "3", "0"));
        assertEquals(60000, AutoClickerApp.calculateMilliseconds("0", "1", "0", "0"));

        // Robustness: Empty strings, whitespace, and null/invalid characters default to 0
        assertEquals(1000, AutoClickerApp.calculateMilliseconds("", " ", "1", null));
        assertEquals(500, AutoClickerApp.calculateMilliseconds("abc", "", "", "500"));
    }

    @Test
    public void testParseNumber() {
        assertEquals(0, AutoClickerApp.parseNumber(""));
        assertEquals(0, AutoClickerApp.parseNumber("   "));
        assertEquals(0, AutoClickerApp.parseNumber(null));
        assertEquals(0, AutoClickerApp.parseNumber("invalid"));
        assertEquals(42, AutoClickerApp.parseNumber("42"));
        assertEquals(100, AutoClickerApp.parseNumber("  100  "));
    }

    @Test
    public void testGetKeyCode() {
        assertEquals(KeyEvent.VK_SPACE, AutoClickerApp.getKeyCode("Space"));
        assertEquals(KeyEvent.VK_ENTER, AutoClickerApp.getKeyCode("Enter"));
        assertEquals(KeyEvent.VK_TAB, AutoClickerApp.getKeyCode("Tab"));
        assertEquals(KeyEvent.VK_ESCAPE, AutoClickerApp.getKeyCode("Escape"));
        assertEquals(KeyEvent.VK_BACK_SPACE, AutoClickerApp.getKeyCode("Backspace"));
        assertEquals(KeyEvent.VK_A, AutoClickerApp.getKeyCode("A"));
        assertEquals(KeyEvent.VK_Z, AutoClickerApp.getKeyCode("Z"));
        assertEquals(KeyEvent.VK_0, AutoClickerApp.getKeyCode("0"));
        assertEquals(KeyEvent.VK_9, AutoClickerApp.getKeyCode("9"));
        assertEquals(KeyEvent.VK_F1, AutoClickerApp.getKeyCode("F1"));
        assertEquals(KeyEvent.VK_F12, AutoClickerApp.getKeyCode("F12"));
        assertEquals(-1, AutoClickerApp.getKeyCode("InvalidKeyName"));
        assertEquals(-1, AutoClickerApp.getKeyCode(null));
    }

    @Test
    public void testGetStandardizedKeyText() {
        assertEquals("Space", AutoClickerApp.getStandardizedKeyText(KeyEvent.VK_SPACE));
        assertEquals("Enter", AutoClickerApp.getStandardizedKeyText(KeyEvent.VK_ENTER));
        assertEquals("Escape", AutoClickerApp.getStandardizedKeyText(KeyEvent.VK_ESCAPE));
        assertEquals("F5", AutoClickerApp.getStandardizedKeyText(KeyEvent.VK_F5));
        assertEquals("A", AutoClickerApp.getStandardizedKeyText(KeyEvent.VK_A));
        assertEquals("7", AutoClickerApp.getStandardizedKeyText(KeyEvent.VK_7));
        assertEquals("Unknown", AutoClickerApp.getStandardizedKeyText(999999));
    }

    @Test
    public void testGuiHoldCheckboxAndLanguageWhenNotHeadless() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Skipping GUI test in headless mode");

        AutoClickerApp app = new AutoClickerApp(null, false);

        // 1. Check hold checkbox exists and controls wrapper visibility
        assertNotNull(app.getHoldCheckBox());
        assertNotNull(app.getHoldPanelWrapper());

        // Toggle hold
        app.getHoldCheckBox().setSelected(false);
        app.getHoldCheckBox().getActionListeners()[0].actionPerformed(null);
        assertFalse(app.getHoldPanelWrapper().isVisible(), "Hold panel should be hidden when unchecked");

        app.getHoldCheckBox().setSelected(true);
        app.getHoldCheckBox().getActionListeners()[0].actionPerformed(null);
        assertTrue(app.getHoldPanelWrapper().isVisible(), "Hold panel should be visible when checked");

        // 2. Language switching
        app.setLanguage("es");
        assertTrue(app.isSpanish());
        assertEquals("Mantener", app.getHoldCheckBox().getText());
        assertTrue(app.getTitle().contains("Pulsador"));

        app.setLanguage("en");
        assertFalse(app.isSpanish());
        assertEquals("Hold", app.getHoldCheckBox().getText());
        assertTrue(app.getTitle().contains("Presser"));

        // Fallback for unsupported language
        app.setLanguage("fr");
        assertEquals("en", app.getCurrentLanguage());

        // 3. Single action detail panel does not shift size
        assertNotNull(app.getActionDetailPanel());
        app.getActionTypeComboBox().setSelectedIndex(0); // Click
        assertTrue(app.getClickTypeComboBox().isVisible());
        assertFalse(app.getKeyField().isVisible());

        app.getActionTypeComboBox().setSelectedIndex(1); // Key Press
        assertFalse(app.getClickTypeComboBox().isVisible());
        assertTrue(app.getKeyField().isVisible());

        // 4. Start / stop action
        assertFalse(app.isRunning());
        app.toggleAction();
        assertTrue(app.isRunning());
        assertEquals("Stop", app.getStartStopButton().getText());

        app.stopAction();
        assertFalse(app.isRunning());
    }
}
