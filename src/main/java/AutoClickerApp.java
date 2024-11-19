import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
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
    private JButton startStopButton, saveConfigButton;
    private JLabel statusLabel;
    private JPanel keyPanel;

    private Preferences prefs;
    private Robot robot;
    private Timer clickTimer;
    private Set<Integer> shortcutKeys = new HashSet<>();
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
        hourField = createStyledNumericField("0", 4);
        minuteField = createStyledNumericField("0", 4);
        secondField = createStyledNumericField("1", 4);
        millisecondField = createStyledNumericField("0", 4);

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
        saveConfigButton = createStyledButton("Save Configuration");

        startStopButton.addActionListener(e -> toggleAction());
        saveConfigButton.addActionListener(e -> saveConfiguration());

        buttonPanel.add(startStopButton);
        buttonPanel.add(saveConfigButton);
        mainPanel.add(buttonPanel);

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
        gradientPanel.add(mainPanel, BorderLayout.CENTER);

        add(gradientPanel, BorderLayout.CENTER);
        updateActionOptions();
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

    private JTextField createStyledNumericField(String initialText, int columns) {
        JTextField field = new JTextField(initialText, columns);
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
                SwingUtilities.invokeLater(() -> field.selectAll());
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

            keyField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (isRecordingKey) {
                        keyField.setText(KeyEvent.getKeyText(e.getKeyCode()));
                        isRecordingKey = false;
                        keyField.removeKeyListener(this);
                    }
                }
            });
        }
    }

    private void recordShortcut() {
        isRecordingShortcut = true;
        shortcutKeys.clear();
        shortcutField.setText("Press key combination...");
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
            if (clickTypeComboBox.getSelectedItem().equals("Middle Click")) {
                clickType = InputEvent.BUTTON2_DOWN_MASK;
            } else if (clickTypeComboBox.getSelectedItem().equals("Right Click")) {
                clickType = InputEvent.BUTTON3_DOWN_MASK;
            }
            robot.mousePress(clickType);
            robot.delay(50);
            robot.mouseRelease(clickType);
        } else if ("Key Press".equals(actionType)) {
            String keyText = keyField.getText();
            if (!keyText.isEmpty() && !keyText.equals("Click to set key") && !keyText.equals("Press any key...")) {
                try {
                    int keyCode = KeyEvent.getExtendedKeyCodeForChar(keyText.charAt(0));
                    robot.keyPress(keyCode);
                    robot.delay(50);
                    robot.keyRelease(keyCode);
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid key: " + keyText);
                }
            }
        }
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
        
        updateActionOptions();
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (isRecordingShortcut) {
            Set<Integer> currentKeys = new HashSet<>();

            int modifiers = e.getModifiers();
            if ((modifiers & NativeKeyEvent.CTRL_MASK) != 0) currentKeys.add(KeyEvent.VK_CONTROL);
            if ((modifiers & NativeKeyEvent.SHIFT_MASK) != 0) currentKeys.add(KeyEvent.VK_SHIFT);
            if ((modifiers & NativeKeyEvent.ALT_MASK) != 0) currentKeys.add(KeyEvent.VK_ALT);

            int keyCode = e.getKeyCode();
            int awtKeyCode = convertToAWTKeyCode(keyCode);
            if (awtKeyCode != -1 && awtKeyCode != KeyEvent.VK_CONTROL
                    && awtKeyCode != KeyEvent.VK_SHIFT && awtKeyCode != KeyEvent.VK_ALT) {
                currentKeys.add(awtKeyCode);
            }

            if (!currentKeys.isEmpty()) {
                shortcutKeys.clear();
                shortcutKeys.addAll(currentKeys);

                String shortcutText = shortcutKeys.stream()
                        .map(key -> KeyEvent.getKeyText(key))
                        .collect(Collectors.joining(" + "));

                SwingUtilities.invokeLater(() -> {
                    shortcutField.setText(shortcutText);
                    isRecordingShortcut = false;
                });
            }
        } else if (!shortcutKeys.isEmpty()) {
            Set<Integer> currentKeys = new HashSet<>();

            int modifiers = e.getModifiers();
            if ((modifiers & NativeKeyEvent.CTRL_MASK) != 0) currentKeys.add(KeyEvent.VK_CONTROL);
            if ((modifiers & NativeKeyEvent.SHIFT_MASK) != 0) currentKeys.add(KeyEvent.VK_SHIFT);
            if ((modifiers & NativeKeyEvent.ALT_MASK) != 0) currentKeys.add(KeyEvent.VK_ALT);

            int awtKeyCode = convertToAWTKeyCode(e.getKeyCode());
            if (awtKeyCode != -1) {
                currentKeys.add(awtKeyCode);
            }

            if (currentKeys.equals(shortcutKeys)) {
                SwingUtilities.invokeLater(this::toggleAction);
            }
        }
    }



    private int convertToAWTKeyCode(int nativeKeyCode) {
        switch (nativeKeyCode) {
            // Letters
            case NativeKeyEvent.VC_A: return KeyEvent.VK_A;
            case NativeKeyEvent.VC_B: return KeyEvent.VK_B;
            case NativeKeyEvent.VC_C: return KeyEvent.VK_C;
            case NativeKeyEvent.VC_D: return KeyEvent.VK_D;
            case NativeKeyEvent.VC_E: return KeyEvent.VK_E;
            case NativeKeyEvent.VC_F: return KeyEvent.VK_F;
            case NativeKeyEvent.VC_G: return KeyEvent.VK_G;
            case NativeKeyEvent.VC_H: return KeyEvent.VK_H;
            case NativeKeyEvent.VC_I: return KeyEvent.VK_I;
            case NativeKeyEvent.VC_J: return KeyEvent.VK_J;
            case NativeKeyEvent.VC_K: return KeyEvent.VK_K;
            case NativeKeyEvent.VC_L: return KeyEvent.VK_L;
            case NativeKeyEvent.VC_M: return KeyEvent.VK_M;
            case NativeKeyEvent.VC_N: return KeyEvent.VK_N;
            case NativeKeyEvent.VC_O: return KeyEvent.VK_O;
            case NativeKeyEvent.VC_P: return KeyEvent.VK_P;
            case NativeKeyEvent.VC_Q: return KeyEvent.VK_Q;
            case NativeKeyEvent.VC_R: return KeyEvent.VK_R;
            case NativeKeyEvent.VC_S: return KeyEvent.VK_S;
            case NativeKeyEvent.VC_T: return KeyEvent.VK_T;
            case NativeKeyEvent.VC_U: return KeyEvent.VK_U;
            case NativeKeyEvent.VC_V: return KeyEvent.VK_V;
            case NativeKeyEvent.VC_W: return KeyEvent.VK_W;
            case NativeKeyEvent.VC_X: return KeyEvent.VK_X;
            case NativeKeyEvent.VC_Y: return KeyEvent.VK_Y;
            case NativeKeyEvent.VC_Z: return KeyEvent.VK_Z;

            // Function keys
            case NativeKeyEvent.VC_F1: return KeyEvent.VK_F1;
            case NativeKeyEvent.VC_F2: return KeyEvent.VK_F2;
            case NativeKeyEvent.VC_F3: return KeyEvent.VK_F3;
            case NativeKeyEvent.VC_F4: return KeyEvent.VK_F4;
            case NativeKeyEvent.VC_F5: return KeyEvent.VK_F5;
            case NativeKeyEvent.VC_F6: return KeyEvent.VK_F6;
            case NativeKeyEvent.VC_F7: return KeyEvent.VK_F7;
            case NativeKeyEvent.VC_F8: return KeyEvent.VK_F8;
            case NativeKeyEvent.VC_F9: return KeyEvent.VK_F9;
            case NativeKeyEvent.VC_F10: return KeyEvent.VK_F10;
            case NativeKeyEvent.VC_F11: return KeyEvent.VK_F11;
            case NativeKeyEvent.VC_F12: return KeyEvent.VK_F12;

            // Other common keys
            case NativeKeyEvent.VC_ESCAPE: return KeyEvent.VK_ESCAPE;
            case NativeKeyEvent.VC_ENTER: return KeyEvent.VK_ENTER;
            case NativeKeyEvent.VC_SPACE: return KeyEvent.VK_SPACE;
            case NativeKeyEvent.VC_TAB: return KeyEvent.VK_TAB;
            case NativeKeyEvent.VC_BACKSPACE: return KeyEvent.VK_BACK_SPACE;

            // Numbers
            case NativeKeyEvent.VC_1: return KeyEvent.VK_1;
            case NativeKeyEvent.VC_2: return KeyEvent.VK_2;
            case NativeKeyEvent.VC_3: return KeyEvent.VK_3;
            case NativeKeyEvent.VC_4: return KeyEvent.VK_4;
            case NativeKeyEvent.VC_5: return KeyEvent.VK_5;
            case NativeKeyEvent.VC_6: return KeyEvent.VK_6;
            case NativeKeyEvent.VC_7: return KeyEvent.VK_7;
            case NativeKeyEvent.VC_8: return KeyEvent.VK_8;
            case NativeKeyEvent.VC_9: return KeyEvent.VK_9;
            case NativeKeyEvent.VC_0: return KeyEvent.VK_0;

            default: return -1;
        }
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
}