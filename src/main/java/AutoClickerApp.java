import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AutoClickerApp extends JFrame implements NativeKeyListener {
    private JComboBox<String> actionTypeComboBox;
    private JComboBox<String> clickTypeComboBox;
    private JTextField keyField;
    private JTextField hourField, minuteField, secondField, millisecondField;
    private JTextField shortcutField;
    private JButton startStopButton;
    private JLabel statusLabel;
    private JPanel keyPanel;

    private final Preferences prefs;
    private Robot robot;
    private Timer clickTimer;
    private final Set<Integer> shortcutKeys = new HashSet<>();
    private boolean isRecordingShortcut = false;
    private boolean isRecordingKey = false;
    private boolean isRunning = false;

    public AutoClickerApp() {
        setTitle("Auto Clicker/Key Presser");
        setSize(700, 700);
        setMinimumSize(new Dimension(700, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(28, 28, 30));

        try {
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            logger.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
        } catch (NativeHookException ex) {
            System.err.println("There was a problem registering the native hook.");
            System.err.println(ex.getMessage());
            System.exit(1);
        }

        prefs = Preferences.userRoot().node(this.getClass().getName());

        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to initialize Robot: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        setupUI();
        loadConfiguration();
    }

    private void setupUI() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));
        mainPanel.setBackground(new Color(28, 28, 30));

        JLabel titleLabel = new JLabel("Auto Clicker/Key Presser");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        addVerticalSpace(mainPanel, 20);

        JPanel actionPanel = createStyledPanel("Action Type");
        actionTypeComboBox = new JComboBox<>(new String[]{"Click", "Key Press"});
        styleComboBox(actionTypeComboBox);
        actionTypeComboBox.addActionListener(e -> updateActionOptions());
        actionPanel.add(actionTypeComboBox);
        mainPanel.add(actionPanel);
        addVerticalSpace(mainPanel, 15);

        clickTypeComboBox = new JComboBox<>(new String[]{"Left Click", "Middle Click", "Right Click"});
        styleComboBox(clickTypeComboBox);
        JPanel clickPanel = createStyledPanel("Click Type");
        clickPanel.add(clickTypeComboBox);
        mainPanel.add(clickPanel);
        addVerticalSpace(mainPanel, 15);

        keyPanel = createStyledPanel("Key to Press");
        keyField = new JTextField(15);
        styleTextField(keyField);
        keyField.setEditable(false);
        keyField.setText("Click to set key");
        keyField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                startKeyRecording();
            }
        });
        keyPanel.add(keyField);
        mainPanel.add(keyPanel);
        addVerticalSpace(mainPanel, 15);

        JPanel intervalPanel = createStyledPanel("Interval");
        hourField = createStyledNumericField("0");
        minuteField = createStyledNumericField("0");
        secondField = createStyledNumericField("1");
        millisecondField = createStyledNumericField("0");

        intervalPanel.add(createIntervalComponent(hourField, "Hours"));
        intervalPanel.add(createIntervalComponent(minuteField, "Minutes"));
        intervalPanel.add(createIntervalComponent(secondField, "Seconds"));
        intervalPanel.add(createIntervalComponent(millisecondField, "Milliseconds"));
        mainPanel.add(intervalPanel);
        addVerticalSpace(mainPanel, 15);

        JPanel shortcutPanel = createStyledPanel("Shortcut Key");
        shortcutField = new JTextField(20);
        styleTextField(shortcutField);
        shortcutField.setEditable(false);
        shortcutField.setText("Click 'Set Shortcut' to bind");
        JButton setShortcutButton = createStyledButton("Set Shortcut");
        setShortcutButton.addActionListener(e -> recordShortcut());
        shortcutPanel.add(shortcutField);
        shortcutPanel.add(Box.createHorizontalStrut(10));
        shortcutPanel.add(setShortcutButton);
        mainPanel.add(shortcutPanel);
        addVerticalSpace(mainPanel, 20);

        statusLabel = new JLabel("Status: Stopped");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(new Color(255, 69, 58));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(statusLabel);
        addVerticalSpace(mainPanel, 20);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttonPanel.setOpaque(false);
        startStopButton = createStyledButton("Start");
        JButton saveConfigButton = createStyledButton("Save Configuration");

        startStopButton.addActionListener(e -> toggleAction());
        saveConfigButton.addActionListener(e -> saveConfiguration());

        buttonPanel.add(startStopButton);
        buttonPanel.add(saveConfigButton);
        mainPanel.add(buttonPanel);

        JPanel gradientPanel = getJPanel();
        gradientPanel.add(mainPanel, BorderLayout.CENTER);

        add(gradientPanel, BorderLayout.CENTER);
        updateActionOptions();

        UIManager.put("ComboBox.background", new Color(28, 28, 30));
        UIManager.put("ComboBox.foreground", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", new Color(45, 45, 47));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
    }

    private static JPanel getJPanel() {
        JPanel gradientPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int w = getWidth();
                int h = getHeight();
                Color color1 = new Color(28, 28, 30);
                Color color2 = new Color(44, 44, 46);
                GradientPaint gp = new GradientPaint(0, 0, color1, w, h, color2);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);
            }
        };
        gradientPanel.setLayout(new BorderLayout());
        return gradientPanel;
    }

    private JPanel createIntervalComponent(JTextField field, String label) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);

        JLabel timeLabel = new JLabel(label);
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        panel.add(field);
        panel.add(Box.createHorizontalStrut(5));
        panel.add(timeLabel);
        return panel;
    }

    private void addVerticalSpace(JPanel panel, int height) {
        panel.add(Box.createRigidArea(new Dimension(0, height)));
    }

    private JPanel createStyledPanel(String title) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));

        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(85, 85, 85)),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                Color.WHITE
        ));

        panel.setBackground(new Color(28, 28, 30));

        return panel;
    }

    private void styleComboBox(JComboBox comboBox) {
        comboBox.setPreferredSize(new Dimension(200, 35));
        comboBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        comboBox.setBackground(new Color(28, 28, 30));
        comboBox.setForeground(Color.BLACK);

        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (isSelected) {
                    setBackground(new Color(45, 45, 47));
                    setForeground(Color.WHITE);
                } else {
                    setBackground(new Color(28, 28, 30));
                    setForeground(Color.WHITE);
                }
                return c;
            }
        });

        Object popupObj = comboBox.getUI().getAccessibleChild(comboBox, 0);
        if (popupObj instanceof JPopupMenu popup) {
            popup.setBackground(new Color(28, 28, 30));
            popup.setBorder(BorderFactory.createLineBorder(new Color(85, 85, 85)));
        }

        Component[] comps = comboBox.getComponents();
        for (Component comp : comps) {
            if (comp instanceof JButton button) {
                button.setBackground(new Color(28, 28, 30));
                button.setForeground(Color.WHITE);
            }
        }
    }

    private void styleTextField(JTextField textField) {
        textField.setPreferredSize(new Dimension(200, 35));
        textField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        textField.setBackground(new Color(45, 45, 47));
        textField.setForeground(Color.WHITE);
        textField.setCaretColor(Color.WHITE);
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        textField.setHorizontalAlignment(JTextField.CENTER);
    }

    private JTextField createStyledNumericField(String initialText) {
        JTextField field = new JTextField(initialText, 4);
        styleTextField(field);
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!Character.isDigit(c)) {
                    e.consume();
                }
            }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(field::selectAll);
            }
        });
        return field;
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setPreferredSize(new Dimension(150, 40));
        button.setBackground(new Color(0, 122, 255));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(0, 100, 220));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(0, 122, 255));
            }
        });
        return button;
    }

    private void startKeyRecording() {
        if (!isRecordingKey) {
            isRecordingKey = true;
            keyField.setText("Press any key...");
            keyField.requestFocusInWindow();

            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
                @Override
                public boolean dispatchKeyEvent(KeyEvent e) {
                    if (isRecordingKey && e.getID() == KeyEvent.KEY_PRESSED) {
                        // Instead of using KeyEvent.getKeyText, we'll use our own mapping
                        String keyText = getStandardizedKeyText(e.getKeyCode());
                        keyField.setText(keyText);
                        isRecordingKey = false;
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
                        e.consume();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private void recordShortcut() {
        isRecordingShortcut = true;
        shortcutKeys.clear();
        shortcutField.setText("Press key combination...");

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (isRecordingShortcut && e.getID() == KeyEvent.KEY_PRESSED) {
                    Set<Integer> currentKeys = new HashSet<>();
                    if (e.isControlDown()) currentKeys.add(KeyEvent.VK_CONTROL);
                    if (e.isShiftDown()) currentKeys.add(KeyEvent.VK_SHIFT);
                    if (e.isAltDown()) currentKeys.add(KeyEvent.VK_ALT);
                    currentKeys.add(e.getKeyCode());

                    shortcutKeys.clear();
                    shortcutKeys.addAll(currentKeys);

                    String shortcutText = shortcutKeys.stream()
                            .map(KeyEvent::getKeyText)
                            .collect(Collectors.joining(" + "));

                    SwingUtilities.invokeLater(() -> {
                        shortcutField.setText(shortcutText);
                        isRecordingShortcut = false;
                    });

                    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
                    e.consume();
                    return true;
                }
                return false;
            }
        });
    }

    private void toggleAction() {
        isRunning = !isRunning;
        startStopButton.setText(isRunning ? "Stop" : "Start");
        statusLabel.setText("Status: " + (isRunning ? "Running" : "Stopped"));
        statusLabel.setForeground(isRunning ? new Color(82, 196, 26) : new Color(255, 69, 58));

        if (isRunning) {
            int interval = calculateIntervalMilliseconds();
            clickTimer = new Timer(interval, e -> performAction());
            clickTimer.start();
        } else if (clickTimer != null) {
            clickTimer.stop();
        }
    }

    private int calculateIntervalMilliseconds() {
        int hours = Integer.parseInt(hourField.getText());
        int minutes = Integer.parseInt(minuteField.getText());
        int seconds = Integer.parseInt(secondField.getText());
        int milliseconds = Integer.parseInt(millisecondField.getText());
        return (hours * 3600 + minutes * 60 + seconds) * 1000 + milliseconds;
    }

    private void performAction() {
        if (!isRunning) return;

        String actionType = (String) actionTypeComboBox.getSelectedItem();
        if ("Click".equals(actionType)) {
            int clickType = InputEvent.BUTTON1_DOWN_MASK;
            if (Objects.equals(clickTypeComboBox.getSelectedItem(), "Middle Click")) {
                clickType = InputEvent.BUTTON2_DOWN_MASK;
            } else if (Objects.equals(clickTypeComboBox.getSelectedItem(), "Right Click")) {
                clickType = InputEvent.BUTTON3_DOWN_MASK;
            }
            robot.mousePress(clickType);
            robot.delay(50);
            robot.mouseRelease(clickType);
        } else if ("Key Press".equals(actionType)) {
            String keyText = keyField.getText();
            if (!keyText.isEmpty() && !keyText.equals("Click to set key") && !keyText.equals("Press any key...")) {
                int keyCode = getKeyCode(keyText);
                if (keyCode != -1) {
                    robot.keyPress(keyCode);
                    robot.delay(50);
                    robot.keyRelease(keyCode);
                } else {
                    System.err.println("Invalid key: " + keyText);
                }
            }
        }
    }

    private int getKeyCode(String keyText) {
        return switch (keyText) {
            case "Space" -> KeyEvent.VK_SPACE;
            case "Enter" -> KeyEvent.VK_ENTER;
            case "Tab" -> KeyEvent.VK_TAB;
            case "Backspace" -> KeyEvent.VK_BACK_SPACE;
            case "Escape" -> KeyEvent.VK_ESCAPE;
            case "Delete" -> KeyEvent.VK_DELETE;
            case "Up" -> KeyEvent.VK_UP;
            case "Down" -> KeyEvent.VK_DOWN;
            case "Left" -> KeyEvent.VK_LEFT;
            case "Right" -> KeyEvent.VK_RIGHT;
            case "Home" -> KeyEvent.VK_HOME;
            case "End" -> KeyEvent.VK_END;
            case "Page Up" -> KeyEvent.VK_PAGE_UP;
            case "Page Down" -> KeyEvent.VK_PAGE_DOWN;
            case "Insert" -> KeyEvent.VK_INSERT;
            case "Control" -> KeyEvent.VK_CONTROL;
            case "Alt" -> KeyEvent.VK_ALT;
            case "Shift" -> KeyEvent.VK_SHIFT;
            case "Caps Lock" -> KeyEvent.VK_CAPS_LOCK;
            case "Num Lock" -> KeyEvent.VK_NUM_LOCK;
            case "Scroll Lock" -> KeyEvent.VK_SCROLL_LOCK;
            case "Pause" -> KeyEvent.VK_PAUSE;
            case "Print Screen" -> KeyEvent.VK_PRINTSCREEN;
            default -> {
                if (keyText.startsWith("F") && keyText.length() > 1) {
                    try {
                        int functionKeyNumber = Integer.parseInt(keyText.substring(1));
                        if (functionKeyNumber >= 1 && functionKeyNumber <= 12) {
                            yield KeyEvent.VK_F1 + functionKeyNumber - 1;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                // For single characters (letters and numbers)
                if (keyText.length() == 1) {
                    char c = keyText.charAt(0);
                    if (Character.isLetter(c)) {
                        yield KeyEvent.VK_A + (Character.toUpperCase(c) - 'A');
                    } else if (Character.isDigit(c)) {
                        yield KeyEvent.VK_0 + (c - '0');
                    }
                }
                yield -1;
            }
        };
    }

    private void updateActionOptions() {
        String selectedAction = (String) actionTypeComboBox.getSelectedItem();
        keyPanel.setVisible("Key Press".equals(selectedAction));
        clickTypeComboBox.getParent().setVisible("Click".equals(selectedAction));
        revalidate();
        repaint();
    }

    private void saveConfiguration() {
        prefs.put("interval_hours", hourField.getText());
        prefs.put("interval_minutes", minuteField.getText());
        prefs.put("interval_seconds", secondField.getText());
        prefs.put("interval_milliseconds", millisecondField.getText());
        prefs.put("action_type", (String) actionTypeComboBox.getSelectedItem());
        prefs.put("shortcut", shortcutField.getText());

        StringBuilder shortcutKeysStr = new StringBuilder();
        for (Integer key : shortcutKeys) {
            shortcutKeysStr.append(key).append(",");
        }
        prefs.put("shortcut_keys", shortcutKeysStr.toString());
        prefs.put("key_to_press", keyField.getText());

        JOptionPane.showMessageDialog(
                this,
                "Configuration saved successfully!",
                "Success",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void loadConfiguration() {
        hourField.setText(prefs.get("interval_hours", "0"));
        minuteField.setText(prefs.get("interval_minutes", "0"));
        secondField.setText(prefs.get("interval_seconds", "1"));
        millisecondField.setText(prefs.get("interval_milliseconds", "0"));
        actionTypeComboBox.setSelectedItem(prefs.get("action_type", "Click"));
        shortcutField.setText(prefs.get("shortcut", ""));

        String shortcutKeysStr = prefs.get("shortcut_keys", "");
        shortcutKeys.clear();
        if (!shortcutKeysStr.isEmpty()) {
            for (String keyStr : shortcutKeysStr.split(",")) {
                try {
                    shortcutKeys.add(Integer.parseInt(keyStr));
                } catch (NumberFormatException e) {
                    System.err.println("Invalid shortcut key: " + keyStr);
                }
            }
        }
        keyField.setText(prefs.get("key_to_press", "Click to set key"));
        updateActionOptions();
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (!isRecordingShortcut && !shortcutKeys.isEmpty()) {
            Set<Integer> currentKeys = getKeys(e);

            if (currentKeys.equals(shortcutKeys)) {
                SwingUtilities.invokeLater(this::toggleAction);
            }
        }
    }

    private Set<Integer> getKeys(NativeKeyEvent e) {
        Set<Integer> currentKeys = new HashSet<>();

        int modifiers = e.getModifiers();
        if ((modifiers & NativeKeyEvent.CTRL_MASK) != 0) currentKeys.add(KeyEvent.VK_CONTROL);
        if ((modifiers & NativeKeyEvent.SHIFT_MASK) != 0) currentKeys.add(KeyEvent.VK_SHIFT);
        if ((modifiers & NativeKeyEvent.ALT_MASK) != 0) currentKeys.add(KeyEvent.VK_ALT);

        int awtKeyCode = convertToAWTKeyCode(e.getKeyCode());
        if (awtKeyCode != -1) {
            currentKeys.add(awtKeyCode);
        }
        return currentKeys;
    }

    private int convertToAWTKeyCode(int nativeKeyCode) {
        return switch (nativeKeyCode) {
            case NativeKeyEvent.VC_A -> KeyEvent.VK_A;
            case NativeKeyEvent.VC_B -> KeyEvent.VK_B;
            case NativeKeyEvent.VC_C -> KeyEvent.VK_C;
            case NativeKeyEvent.VC_D -> KeyEvent.VK_D;
            case NativeKeyEvent.VC_E -> KeyEvent.VK_E;
            case NativeKeyEvent.VC_F -> KeyEvent.VK_F;
            case NativeKeyEvent.VC_G -> KeyEvent.VK_G;
            case NativeKeyEvent.VC_H -> KeyEvent.VK_H;
            case NativeKeyEvent.VC_I -> KeyEvent.VK_I;
            case NativeKeyEvent.VC_J -> KeyEvent.VK_J;
            case NativeKeyEvent.VC_K -> KeyEvent.VK_K;
            case NativeKeyEvent.VC_L -> KeyEvent.VK_L;
            case NativeKeyEvent.VC_M -> KeyEvent.VK_M;
            case NativeKeyEvent.VC_N -> KeyEvent.VK_N;
            case NativeKeyEvent.VC_O -> KeyEvent.VK_O;
            case NativeKeyEvent.VC_P -> KeyEvent.VK_P;
            case NativeKeyEvent.VC_Q -> KeyEvent.VK_Q;
            case NativeKeyEvent.VC_R -> KeyEvent.VK_R;
            case NativeKeyEvent.VC_S -> KeyEvent.VK_S;
            case NativeKeyEvent.VC_T -> KeyEvent.VK_T;
            case NativeKeyEvent.VC_U -> KeyEvent.VK_U;
            case NativeKeyEvent.VC_V -> KeyEvent.VK_V;
            case NativeKeyEvent.VC_W -> KeyEvent.VK_W;
            case NativeKeyEvent.VC_X -> KeyEvent.VK_X;
            case NativeKeyEvent.VC_Y -> KeyEvent.VK_Y;
            case NativeKeyEvent.VC_Z -> KeyEvent.VK_Z;

            case NativeKeyEvent.VC_F1 -> KeyEvent.VK_F1;
            case NativeKeyEvent.VC_F2 -> KeyEvent.VK_F2;
            case NativeKeyEvent.VC_F3 -> KeyEvent.VK_F3;
            case NativeKeyEvent.VC_F4 -> KeyEvent.VK_F4;
            case NativeKeyEvent.VC_F5 -> KeyEvent.VK_F5;
            case NativeKeyEvent.VC_F6 -> KeyEvent.VK_F6;
            case NativeKeyEvent.VC_F7 -> KeyEvent.VK_F7;
            case NativeKeyEvent.VC_F8 -> KeyEvent.VK_F8;
            case NativeKeyEvent.VC_F9 -> KeyEvent.VK_F9;
            case NativeKeyEvent.VC_F10 -> KeyEvent.VK_F10;
            case NativeKeyEvent.VC_F11 -> KeyEvent.VK_F11;
            case NativeKeyEvent.VC_F12 -> KeyEvent.VK_F12;

            case NativeKeyEvent.VC_ESCAPE -> KeyEvent.VK_ESCAPE;
            case NativeKeyEvent.VC_ENTER -> KeyEvent.VK_ENTER;
            case NativeKeyEvent.VC_SPACE -> KeyEvent.VK_SPACE;
            case NativeKeyEvent.VC_TAB -> KeyEvent.VK_TAB;
            case NativeKeyEvent.VC_BACKSPACE -> KeyEvent.VK_BACK_SPACE;

            case NativeKeyEvent.VC_1 -> KeyEvent.VK_1;
            case NativeKeyEvent.VC_2 -> KeyEvent.VK_2;
            case NativeKeyEvent.VC_3 -> KeyEvent.VK_3;
            case NativeKeyEvent.VC_4 -> KeyEvent.VK_4;
            case NativeKeyEvent.VC_5 -> KeyEvent.VK_5;
            case NativeKeyEvent.VC_6 -> KeyEvent.VK_6;
            case NativeKeyEvent.VC_7 -> KeyEvent.VK_7;
            case NativeKeyEvent.VC_8 -> KeyEvent.VK_8;
            case NativeKeyEvent.VC_9 -> KeyEvent.VK_9;
            case NativeKeyEvent.VC_0 -> KeyEvent.VK_0;
            default -> -1;
        };
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            UIManager.put("Panel.background", new Color(28, 28, 30));
            UIManager.put("OptionPane.background", new Color(28, 28, 30));
            UIManager.put("OptionPane.messageForeground", Color.WHITE);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            AutoClickerApp app = new AutoClickerApp();
            app.setLocationRelativeTo(null);
            app.setVisible(true);
        });
    }
    private String getStandardizedKeyText(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_SPACE -> "Space";
            case KeyEvent.VK_ENTER -> "Enter";
            case KeyEvent.VK_TAB -> "Tab";
            case KeyEvent.VK_BACK_SPACE -> "Backspace";
            case KeyEvent.VK_ESCAPE -> "Escape";
            case KeyEvent.VK_DELETE -> "Delete";
            case KeyEvent.VK_UP -> "Up";
            case KeyEvent.VK_DOWN -> "Down";
            case KeyEvent.VK_LEFT -> "Left";
            case KeyEvent.VK_RIGHT -> "Right";
            case KeyEvent.VK_HOME -> "Home";
            case KeyEvent.VK_END -> "End";
            case KeyEvent.VK_PAGE_UP -> "Page Up";
            case KeyEvent.VK_PAGE_DOWN -> "Page Down";
            case KeyEvent.VK_INSERT -> "Insert";
            case KeyEvent.VK_F1 -> "F1";
            case KeyEvent.VK_F2 -> "F2";
            case KeyEvent.VK_F3 -> "F3";
            case KeyEvent.VK_F4 -> "F4";
            case KeyEvent.VK_F5 -> "F5";
            case KeyEvent.VK_F6 -> "F6";
            case KeyEvent.VK_F7 -> "F7";
            case KeyEvent.VK_F8 -> "F8";
            case KeyEvent.VK_F9 -> "F9";
            case KeyEvent.VK_F10 -> "F10";
            case KeyEvent.VK_F11 -> "F11";
            case KeyEvent.VK_F12 -> "F12";
            case KeyEvent.VK_CONTROL -> "Control";
            case KeyEvent.VK_ALT -> "Alt";
            case KeyEvent.VK_SHIFT -> "Shift";
            case KeyEvent.VK_CAPS_LOCK -> "Caps Lock";
            case KeyEvent.VK_NUM_LOCK -> "Num Lock";
            case KeyEvent.VK_SCROLL_LOCK -> "Scroll Lock";
            case KeyEvent.VK_PAUSE -> "Pause";
            case KeyEvent.VK_PRINTSCREEN -> "Print Screen";
            default -> {
                // For regular letters and numbers, use a fixed mapping
                if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
                    yield String.valueOf((char) (keyCode));
                } else if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
                    yield String.valueOf((char) ('0' + (keyCode - KeyEvent.VK_0)));
                } else {
                    yield "Unknown";
                }
            }
        };
    }
}