import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
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
    private JTextField holdHourField, holdMinuteField, holdSecondField, holdMillisecondField;
    private JTextField hourField, minuteField, secondField, millisecondField;
    private JTextField shortcutField;
    private JButton startStopButton;
    private JButton saveConfigButton;
    private JButton setShortcutButton;
    private JButton settingsButton;
    private JLabel statusLabel;
    private JPanel actionDetailPanel;
    private JPanel holdPanel;
    private JPanel holdPanelWrapper;
    private JCheckBox holdCheckBox;

    // Titled borders for dynamic localization
    private TitledBorder actionPanelBorder;
    private TitledBorder actionDetailBorder;
    private TitledBorder holdPanelBorder;
    private TitledBorder intervalPanelBorder;
    private TitledBorder shortcutPanelBorder;

    // Time unit labels for localization
    private JLabel holdHourLabel, holdMinuteLabel, holdSecondLabel, holdMillisecondLabel;
    private JLabel hourLabel, minuteLabel, secondLabel, millisecondLabel;

    private final Preferences prefs;
    private Robot robot;
    private Thread actionThread;
    private volatile int activeMousePress = -1;
    private volatile int activeKeyPress = -1;
    private final Set<Integer> shortcutKeys = new HashSet<>();
    private boolean isRecordingShortcut = false;
    private boolean isRecordingKey = false;
    private volatile boolean isRunning = false;
    private String currentLanguage = "en";

    public AutoClickerApp() {
        this(createRobotSafely(), true);
    }

    AutoClickerApp(Robot robot, boolean initNativeHook) {
        this.robot = robot;
        prefs = Preferences.userRoot().node(this.getClass().getName());
        currentLanguage = detectInitialLanguage();

        setTitle(isSpanish() ? "Auto Clicker / Pulsador de Teclas" : "Auto Clicker/Key Presser");
        setSize(700, 660);
        setMinimumSize(new Dimension(700, 620));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(28, 28, 30));

        loadAppIcon();

        if (initNativeHook) {
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
        }

        setupUI();
        loadConfiguration();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAction();
                if (initNativeHook) {
                    try {
                        GlobalScreen.unregisterNativeHook();
                    } catch (NativeHookException ignored) {
                    }
                }
            }
        });
    }

    private void loadAppIcon() {
        try {
            URL iconUrl = getClass().getResource("/icon.png");
            if (iconUrl != null) {
                Image icon = ImageIO.read(iconUrl);
                if (icon != null) {
                    setIconImage(icon);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Robot createRobotSafely() {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        try {
            return new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to initialize Robot: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return null;
        }
    }

    private String detectInitialLanguage() {
        String saved = prefs.get("language", null);
        if (saved != null && (saved.equals("en") || saved.equals("es"))) {
            return saved;
        }
        String sysLang = Locale.getDefault().getLanguage().toLowerCase();
        if (sysLang.startsWith("es")) {
            return "es";
        }
        return "en";
    }

    public boolean isSpanish() {
        return "es".equals(currentLanguage);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setLanguage(String lang) {
        if (!"en".equals(lang) && !"es".equals(lang)) {
            lang = "en";
        }
        this.currentLanguage = lang;
        prefs.put("language", lang);
        applyLanguage();
    }

    private void setupUI() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(15, 25, 20, 25));
        mainPanel.setBackground(new Color(28, 28, 30));

        // Top bar with Settings Cog icon in the top right (removed redundant title text)
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topBar.setOpaque(false);
        topBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        topBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        settingsButton = new JButton(new CogIcon(20));
        settingsButton.setPreferredSize(new Dimension(36, 36));
        settingsButton.setBackground(new Color(28, 28, 30));
        settingsButton.setForeground(new Color(200, 200, 200));
        settingsButton.setFocusPainted(false);
        settingsButton.setBorderPainted(false);
        settingsButton.setContentAreaFilled(false);
        settingsButton.setOpaque(false);
        settingsButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        settingsButton.setToolTipText(isSpanish() ? "Idioma" : "Language");
        settingsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                settingsButton.setForeground(Color.WHITE);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                settingsButton.setForeground(new Color(200, 200, 200));
            }
        });
        settingsButton.addActionListener(e -> showLanguageMenu(settingsButton));

        topBar.add(settingsButton);
        mainPanel.add(topBar);
        addVerticalSpace(mainPanel, 10);

        // 1. Action Type Panel with Hold Checkbox on the right
        JPanel actionPanel = createStyledPanel(isSpanish() ? "Tipo de Acción" : "Action Type");
        actionPanelBorder = (TitledBorder) actionPanel.getBorder();
        actionPanel.setLayout(new BorderLayout(15, 0));
        actionPanel.setBorder(BorderFactory.createCompoundBorder(
                actionPanelBorder,
                BorderFactory.createEmptyBorder(0, 15, 0, 15)
        ));

        actionTypeComboBox = new JComboBox<>(getActionTypeItems());
        styleComboBox(actionTypeComboBox);
        actionTypeComboBox.addActionListener(e -> updateActionOptions());

        JPanel actionLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        actionLeft.setOpaque(false);
        actionLeft.add(actionTypeComboBox);

        holdCheckBox = new JCheckBox(isSpanish() ? "Mantener" : "Hold");
        holdCheckBox.setFont(new Font("Segoe UI", Font.BOLD, 13));
        holdCheckBox.setForeground(Color.WHITE);
        holdCheckBox.setOpaque(false);
        holdCheckBox.setFocusPainted(false);
        holdCheckBox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        holdCheckBox.addActionListener(e -> {
            boolean enabled = holdCheckBox.isSelected();
            holdPanelWrapper.setVisible(enabled);
            revalidate();
            repaint();
        });

        JPanel actionRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 12));
        actionRight.setOpaque(false);
        actionRight.add(holdCheckBox);

        actionPanel.add(actionLeft, BorderLayout.WEST);
        actionPanel.add(actionRight, BorderLayout.EAST);
        mainPanel.add(actionPanel);
        addVerticalSpace(mainPanel, 12);

        // 2. Single Action Detail Panel (switches between Click Type and Key to Press without shifting size)
        actionDetailPanel = createStyledPanel(isSpanish() ? "Tipo de Click" : "Click Type");
        actionDetailBorder = (TitledBorder) actionDetailPanel.getBorder();

        clickTypeComboBox = new JComboBox<>(getClickTypeItems());
        styleComboBox(clickTypeComboBox);

        keyField = new JTextField(15);
        styleTextField(keyField);
        keyField.setEditable(false);
        keyField.setText(isSpanish() ? "Click para asignar tecla" : "Click to set key");
        keyField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                startKeyRecording();
            }
        });
        keyField.setVisible(false); // Initially hidden, clickTypeComboBox is shown

        actionDetailPanel.add(clickTypeComboBox);
        actionDetailPanel.add(keyField);
        mainPanel.add(actionDetailPanel);
        addVerticalSpace(mainPanel, 12);

        // 3. Hold Panel (wrapped so it collapses cleanly when hold is OFF)
        holdPanelWrapper = new JPanel();
        holdPanelWrapper.setLayout(new BoxLayout(holdPanelWrapper, BoxLayout.Y_AXIS));
        holdPanelWrapper.setOpaque(false);
        holdPanelWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

        holdPanel = createStyledPanel(isSpanish() ? "Mantener" : "Hold");
        holdPanelBorder = (TitledBorder) holdPanel.getBorder();
        holdHourField = createStyledNumericField("0");
        holdMinuteField = createStyledNumericField("0");
        holdSecondField = createStyledNumericField("0");
        holdMillisecondField = createStyledNumericField("0");

        holdHourLabel = new JLabel(isSpanish() ? "Horas" : "Hours");
        holdMinuteLabel = new JLabel(isSpanish() ? "Minutos" : "Minutes");
        holdSecondLabel = new JLabel(isSpanish() ? "Segundos" : "Seconds");
        holdMillisecondLabel = new JLabel(isSpanish() ? "Milisegundos" : "Milliseconds");

        holdPanel.add(createIntervalComponentWithLabel(holdHourField, holdHourLabel));
        holdPanel.add(createIntervalComponentWithLabel(holdMinuteField, holdMinuteLabel));
        holdPanel.add(createIntervalComponentWithLabel(holdSecondField, holdSecondLabel));
        holdPanel.add(createIntervalComponentWithLabel(holdMillisecondField, holdMillisecondLabel));

        holdPanelWrapper.add(holdPanel);
        holdPanelWrapper.add(Box.createRigidArea(new Dimension(0, 12)));
        holdPanelWrapper.setVisible(false); // Collapsed by default unless enabled
        mainPanel.add(holdPanelWrapper);

        // 4. Interval Panel
        JPanel intervalPanel = createStyledPanel(isSpanish() ? "Intervalo" : "Interval");
        intervalPanelBorder = (TitledBorder) intervalPanel.getBorder();
        hourField = createStyledNumericField("0");
        minuteField = createStyledNumericField("0");
        secondField = createStyledNumericField("1");
        millisecondField = createStyledNumericField("0");

        hourLabel = new JLabel(isSpanish() ? "Horas" : "Hours");
        minuteLabel = new JLabel(isSpanish() ? "Minutos" : "Minutes");
        secondLabel = new JLabel(isSpanish() ? "Segundos" : "Seconds");
        millisecondLabel = new JLabel(isSpanish() ? "Milisegundos" : "Milliseconds");

        intervalPanel.add(createIntervalComponentWithLabel(hourField, hourLabel));
        intervalPanel.add(createIntervalComponentWithLabel(minuteField, minuteLabel));
        intervalPanel.add(createIntervalComponentWithLabel(secondField, secondLabel));
        intervalPanel.add(createIntervalComponentWithLabel(millisecondField, millisecondLabel));
        mainPanel.add(intervalPanel);
        addVerticalSpace(mainPanel, 12);

        // 5. Shortcut Key Panel
        JPanel shortcutPanel = createStyledPanel(isSpanish() ? "Tecla de Acceso Rápido" : "Shortcut Key");
        shortcutPanelBorder = (TitledBorder) shortcutPanel.getBorder();
        shortcutField = new JTextField(20);
        styleTextField(shortcutField);
        shortcutField.setEditable(false);
        shortcutField.setText(isSpanish() ? "Click en 'Asignar' para vincular" : "Click 'Set Shortcut' to bind");
        setShortcutButton = createStyledButton(isSpanish() ? "Asignar Acceso Rápido" : "Set Shortcut");
        setShortcutButton.addActionListener(e -> recordShortcut());
        shortcutPanel.add(shortcutField);
        shortcutPanel.add(Box.createHorizontalStrut(10));
        shortcutPanel.add(setShortcutButton);
        mainPanel.add(shortcutPanel);
        addVerticalSpace(mainPanel, 15);

        // Status Label
        statusLabel = new JLabel(isSpanish() ? "Estado: Detenido" : "Status: Stopped");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(new Color(255, 69, 58));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(statusLabel);
        addVerticalSpace(mainPanel, 15);

        // Control Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        startStopButton = createStyledButton(isSpanish() ? "Iniciar" : "Start");
        saveConfigButton = createStyledButton(isSpanish() ? "Guardar Configuración" : "Save Configuration");

        startStopButton.addActionListener(e -> toggleAction());
        saveConfigButton.addActionListener(e -> saveConfiguration());

        buttonPanel.add(startStopButton);
        buttonPanel.add(saveConfigButton);
        mainPanel.add(buttonPanel);

        mainPanel.add(Box.createVerticalGlue());

        JPanel gradientPanel = getJPanel();
        gradientPanel.add(mainPanel, BorderLayout.CENTER);

        add(gradientPanel, BorderLayout.CENTER);
        updateActionOptions();

        UIManager.put("ComboBox.background", new Color(45, 45, 47));
        UIManager.put("ComboBox.foreground", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", new Color(0, 122, 255));
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
    }

    private void showLanguageMenu(Component invoker) {
        JPopupMenu menu = new JPopupMenu();
        menu.setUI(new BasicPopupMenuUI());
        menu.setBackground(new Color(36, 36, 38));
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(75, 75, 75), 1),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        JLabel headerLabel = new JLabel(isSpanish() ? "  Idioma" : "  Language");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        headerLabel.setForeground(new Color(150, 150, 150));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        menu.add(headerLabel);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(60, 60, 60));
        sep.setBackground(new Color(36, 36, 38));
        menu.add(sep);

        boolean es = isSpanish();
        JMenuItem enItem = createStyledMenuItem("English", !es);
        JMenuItem esItem = createStyledMenuItem("Español", es);

        enItem.addActionListener(e -> setLanguage("en"));
        esItem.addActionListener(e -> setLanguage("es"));

        menu.add(enItem);
        menu.add(esItem);

        int menuWidth = 140;
        menu.setPreferredSize(new Dimension(menuWidth, menu.getPreferredSize().height));
        int x = invoker.getWidth() - menuWidth;
        menu.show(invoker, x, invoker.getHeight() + 4);
    }

    private JMenuItem createStyledMenuItem(String text, boolean isSelected) {
        JMenuItem item = new JMenuItem(text, new CheckIcon(isSelected));
        item.setIconTextGap(8);
        item.setFont(new Font("Segoe UI", isSelected ? Font.BOLD : Font.PLAIN, 12));
        item.setForeground(Color.WHITE);
        item.setBackground(new Color(36, 36, 38));
        item.setOpaque(true);
        item.setCursor(new Cursor(Cursor.HAND_CURSOR));
        item.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 12));

        item.setUI(new BasicMenuItemUI() {
            @Override
            protected void paintBackground(Graphics g, JMenuItem menuItem, Color defaultColor) {
                ButtonModel model = menuItem.getModel();
                if (model.isArmed() || model.isRollover()) {
                    g.setColor(new Color(0, 122, 255));
                } else {
                    g.setColor(new Color(36, 36, 38));
                }
                g.fillRect(0, 0, menuItem.getWidth(), menuItem.getHeight());
            }
        });

        return item;
    }

    private void applyLanguage() {
        boolean es = isSpanish();

        setTitle(es ? "Auto Clicker / Pulsador de Teclas" : "Auto Clicker/Key Presser");

        if (actionPanelBorder != null) actionPanelBorder.setTitle(es ? "Tipo de Acción" : "Action Type");
        if (holdPanelBorder != null) holdPanelBorder.setTitle(es ? "Mantener" : "Hold");
        if (intervalPanelBorder != null) intervalPanelBorder.setTitle(es ? "Intervalo" : "Interval");
        if (shortcutPanelBorder != null) shortcutPanelBorder.setTitle(es ? "Tecla de Acceso Rápido" : "Shortcut Key");

        if (holdCheckBox != null) holdCheckBox.setText(es ? "Mantener" : "Hold");

        if (holdHourLabel != null) holdHourLabel.setText(es ? "Horas" : "Hours");
        if (holdMinuteLabel != null) holdMinuteLabel.setText(es ? "Minutos" : "Minutes");
        if (holdSecondLabel != null) holdSecondLabel.setText(es ? "Segundos" : "Seconds");
        if (holdMillisecondLabel != null) holdMillisecondLabel.setText(es ? "Milisegundos" : "Milliseconds");

        if (hourLabel != null) hourLabel.setText(es ? "Horas" : "Hours");
        if (minuteLabel != null) minuteLabel.setText(es ? "Minutos" : "Minutes");
        if (secondLabel != null) secondLabel.setText(es ? "Segundos" : "Seconds");
        if (millisecondLabel != null) millisecondLabel.setText(es ? "Milisegundos" : "Milliseconds");

        if (setShortcutButton != null) setShortcutButton.setText(es ? "Asignar Acceso Rápido" : "Set Shortcut");
        if (saveConfigButton != null) saveConfigButton.setText(es ? "Guardar Configuración" : "Save Configuration");
        if (settingsButton != null) settingsButton.setToolTipText(es ? "Idioma" : "Language");

        if (startStopButton != null) {
            startStopButton.setText(isRunning ? (es ? "Detener" : "Stop") : (es ? "Iniciar" : "Start"));
        }
        if (statusLabel != null) {
            statusLabel.setText(isRunning ? (es ? "Estado: En ejecución" : "Status: Running") : (es ? "Estado: Detenido" : "Status: Stopped"));
        }

        if (keyField != null) {
            String kt = keyField.getText();
            if (kt.equals("Click to set key") || kt.equals("Click para asignar tecla")) {
                keyField.setText(es ? "Click para asignar tecla" : "Click to set key");
            }
        }
        if (shortcutField != null) {
            String st = shortcutField.getText();
            if (st.equals("Click 'Set Shortcut' to bind") || st.equals("Click en 'Asignar' para vincular")) {
                shortcutField.setText(es ? "Click en 'Asignar' para vincular" : "Click 'Set Shortcut' to bind");
            }
        }

        if (actionTypeComboBox != null) {
            int selectedIdx = actionTypeComboBox.getSelectedIndex();
            actionTypeComboBox.setModel(new DefaultComboBoxModel<>(getActionTypeItems()));
            if (selectedIdx >= 0 && selectedIdx < actionTypeComboBox.getItemCount()) {
                actionTypeComboBox.setSelectedIndex(selectedIdx);
            }
        }
        if (clickTypeComboBox != null) {
            int selectedIdx = clickTypeComboBox.getSelectedIndex();
            clickTypeComboBox.setModel(new DefaultComboBoxModel<>(getClickTypeItems()));
            if (selectedIdx >= 0 && selectedIdx < clickTypeComboBox.getItemCount()) {
                clickTypeComboBox.setSelectedIndex(selectedIdx);
            }
        }

        updateActionOptions();
        revalidate();
        repaint();
    }

    private String[] getActionTypeItems() {
        return isSpanish() ? new String[]{"Click", "Pulsación de Tecla"} : new String[]{"Click", "Key Press"};
    }

    private String[] getClickTypeItems() {
        return isSpanish() ? new String[]{"Click Izquierdo", "Click Central", "Click Derecho"} : new String[]{"Left Click", "Middle Click", "Right Click"};
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

    private JPanel createIntervalComponentWithLabel(JTextField field, JLabel label) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);

        label.setForeground(Color.WHITE);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        panel.add(field);
        panel.add(Box.createHorizontalStrut(5));
        panel.add(label);
        return panel;
    }

    private void addVerticalSpace(JPanel panel, int height) {
        panel.add(Box.createRigidArea(new Dimension(0, height)));
    }

    private JPanel createStyledPanel(String title) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));

        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(85, 85, 85)),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                Color.WHITE
        ));

        panel.setBackground(new Color(28, 28, 30));
        panel.setPreferredSize(new Dimension(650, 75));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        panel.setMinimumSize(new Dimension(600, 75));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);

        return panel;
    }

    private void styleComboBox(JComboBox<String> comboBox) {
        comboBox.setPreferredSize(new Dimension(200, 35));
        comboBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        comboBox.setBackground(new Color(45, 45, 47));
        comboBox.setForeground(Color.WHITE);

        comboBox.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton button = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(45, 45, 47));
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(Color.WHITE);
                        int w = getWidth();
                        int h = getHeight();
                        int[] xPoints = {w / 2 - 4, w / 2 + 4, w / 2};
                        int[] yPoints = {h / 2 - 2, h / 2 - 2, h / 2 + 3};
                        g2.fillPolygon(xPoints, yPoints, 3);
                        g2.dispose();
                    }
                };
                button.setBackground(new Color(45, 45, 47));
                button.setBorder(BorderFactory.createEmptyBorder());
                button.setContentAreaFilled(false);
                button.setFocusPainted(false);
                button.setOpaque(true);
                return button;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                g.setColor(new Color(45, 45, 47));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        });

        comboBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                BorderFactory.createEmptyBorder(2, 8, 2, 2)
        ));

        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                if (isSelected) {
                    setBackground(new Color(0, 122, 255));
                    setForeground(Color.WHITE);
                } else {
                    setBackground(new Color(45, 45, 47));
                    setForeground(Color.WHITE);
                }
                setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
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

    private JTextField createStyledNumericField(String initialText) {
        JTextField field = new JTextField(initialText, 4);
        field.setPreferredSize(new Dimension(55, 34));
        field.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        field.setBackground(new Color(45, 45, 47));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        field.setHorizontalAlignment(JTextField.CENTER);
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
        button.setPreferredSize(new Dimension(170, 40));
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
            keyField.setText(isSpanish() ? "Presione cualquier tecla..." : "Press any key...");
            keyField.requestFocusInWindow();

            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
                @Override
                public boolean dispatchKeyEvent(KeyEvent e) {
                    if (isRecordingKey && e.getID() == KeyEvent.KEY_PRESSED) {
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
        shortcutField.setText(isSpanish() ? "Presione combinación de teclas..." : "Press key combination...");

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

    public synchronized void toggleAction() {
        isRunning = !isRunning;
        boolean es = isSpanish();
        startStopButton.setText(isRunning ? (es ? "Detener" : "Stop") : (es ? "Iniciar" : "Start"));
        statusLabel.setText((es ? "Estado: " : "Status: ") + (isRunning ? (es ? "En ejecución" : "Running") : (es ? "Detenido" : "Stopped")));
        statusLabel.setForeground(isRunning ? new Color(82, 196, 26) : new Color(255, 69, 58));

        if (isRunning) {
            int interval = calculateIntervalMilliseconds();
            boolean holdActive = holdCheckBox != null && holdCheckBox.isSelected();
            int holdTime = holdActive ? calculateHoldMilliseconds() : 0;
            int actionIdx = actionTypeComboBox.getSelectedIndex();
            int clickIdx = clickTypeComboBox.getSelectedIndex();
            String keyText = keyField.getText();

            actionThread = new Thread(() -> runActionLoop(interval, holdTime, actionIdx, clickIdx, keyText), "AutoClicker-ActionThread");
            actionThread.setDaemon(true);
            actionThread.start();
        } else {
            stopAction();
        }
    }

    public synchronized void stopAction() {
        isRunning = false;
        if (actionThread != null) {
            actionThread.interrupt();
            actionThread = null;
        }
        releaseActiveInputs();
    }

    private synchronized void releaseActiveInputs() {
        if (robot == null) return;
        if (activeMousePress != -1) {
            try {
                robot.mouseRelease(activeMousePress);
            } catch (Exception ignored) {
            }
            activeMousePress = -1;
        }
        if (activeKeyPress != -1) {
            try {
                robot.keyRelease(activeKeyPress);
            } catch (Exception ignored) {
            }
            activeKeyPress = -1;
        }
    }

    private void runActionLoop(int interval, int holdTime, int actionIdx, int clickIdx, String keyText) {
        try {
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                if (interval > 0) {
                    Thread.sleep(interval);
                }
                if (!isRunning || Thread.currentThread().isInterrupted()) {
                    break;
                }
                performSingleAction(holdTime, actionIdx, clickIdx, keyText);
            }
        } catch (InterruptedException ignored) {
        } finally {
            releaseActiveInputs();
        }
    }

    private void performSingleAction(int holdTime, int actionIdx, int clickIdx, String keyText) throws InterruptedException {
        if (robot == null) return;
        int holdDuration = (holdTime > 0) ? holdTime : 50;

        if (actionIdx == 0) { // Mouse Click
            int clickMask = InputEvent.BUTTON1_DOWN_MASK;
            if (clickIdx == 1) {
                clickMask = InputEvent.BUTTON2_DOWN_MASK;
            } else if (clickIdx == 2) {
                clickMask = InputEvent.BUTTON3_DOWN_MASK;
            }

            activeMousePress = clickMask;
            robot.mousePress(clickMask);
            try {
                Thread.sleep(holdDuration);
            } finally {
                robot.mouseRelease(clickMask);
                activeMousePress = -1;
            }
        } else if (actionIdx == 1) { // Key Press
            if (keyText != null && !keyText.isEmpty()
                    && !keyText.equals("Click to set key") && !keyText.equals("Click para asignar tecla")
                    && !keyText.equals("Press any key...") && !keyText.equals("Presione cualquier tecla...")) {
                int keyCode = getKeyCode(keyText);
                if (keyCode != -1) {
                    activeKeyPress = keyCode;
                    robot.keyPress(keyCode);
                    try {
                        Thread.sleep(holdDuration);
                    } finally {
                        robot.keyRelease(keyCode);
                        activeKeyPress = -1;
                    }
                } else {
                    System.err.println("Invalid key: " + keyText);
                }
            }
        }
    }

    public static int parseNumber(String text) {
        if (text == null) return 0;
        try {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? 0 : Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int calculateMilliseconds(int hours, int minutes, int seconds, int milliseconds) {
        return (hours * 3600 + minutes * 60 + seconds) * 1000 + milliseconds;
    }

    public static int calculateMilliseconds(String hours, String minutes, String seconds, String milliseconds) {
        return calculateMilliseconds(
                parseNumber(hours),
                parseNumber(minutes),
                parseNumber(seconds),
                parseNumber(milliseconds)
        );
    }

    public int calculateIntervalMilliseconds() {
        return calculateMilliseconds(
                hourField != null ? hourField.getText() : "0",
                minuteField != null ? minuteField.getText() : "0",
                secondField != null ? secondField.getText() : "0",
                millisecondField != null ? millisecondField.getText() : "0"
        );
    }

    public int calculateHoldMilliseconds() {
        return calculateMilliseconds(
                holdHourField != null ? holdHourField.getText() : "0",
                holdMinuteField != null ? holdMinuteField.getText() : "0",
                holdSecondField != null ? holdSecondField.getText() : "0",
                holdMillisecondField != null ? holdMillisecondField.getText() : "0"
        );
    }

    public static int getKeyCode(String keyText) {
        if (keyText == null) return -1;
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
            case "F1" -> KeyEvent.VK_F1;
            case "F2" -> KeyEvent.VK_F2;
            case "F3" -> KeyEvent.VK_F3;
            case "F4" -> KeyEvent.VK_F4;
            case "F5" -> KeyEvent.VK_F5;
            case "F6" -> KeyEvent.VK_F6;
            case "F7" -> KeyEvent.VK_F7;
            case "F8" -> KeyEvent.VK_F8;
            case "F9" -> KeyEvent.VK_F9;
            case "F10" -> KeyEvent.VK_F10;
            case "F11" -> KeyEvent.VK_F11;
            case "F12" -> KeyEvent.VK_F12;
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
        if (actionTypeComboBox == null || actionDetailPanel == null) return;
        int selectedIdx = actionTypeComboBox.getSelectedIndex();
        boolean isKeyPress = (selectedIdx == 1);

        if (clickTypeComboBox != null) clickTypeComboBox.setVisible(!isKeyPress);
        if (keyField != null) keyField.setVisible(isKeyPress);

        if (actionDetailBorder != null) {
            actionDetailBorder.setTitle(isKeyPress ?
                    (isSpanish() ? "Tecla a Pulsar" : "Key to Press") :
                    (isSpanish() ? "Tipo de Click" : "Click Type"));
        }
        actionDetailPanel.repaint();
    }

    private void saveConfiguration() {
        prefs.put("interval_hours", hourField.getText());
        prefs.put("interval_minutes", minuteField.getText());
        prefs.put("interval_seconds", secondField.getText());
        prefs.put("interval_milliseconds", millisecondField.getText());
        prefs.put("hold_hours", holdHourField.getText());
        prefs.put("hold_minutes", holdMinuteField.getText());
        prefs.put("hold_seconds", holdSecondField.getText());
        prefs.put("hold_milliseconds", holdMillisecondField.getText());
        prefs.putBoolean("hold_enabled", holdCheckBox.isSelected());
        prefs.putInt("action_type_index", actionTypeComboBox.getSelectedIndex());
        prefs.putInt("click_type_index", clickTypeComboBox.getSelectedIndex());
        prefs.put("shortcut", shortcutField.getText());
        prefs.put("language", currentLanguage);

        StringBuilder shortcutKeysStr = new StringBuilder();
        for (Integer key : shortcutKeys) {
            shortcutKeysStr.append(key).append(",");
        }
        prefs.put("shortcut_keys", shortcutKeysStr.toString());
        prefs.put("key_to_press", keyField.getText());

        JOptionPane.showMessageDialog(
                this,
                isSpanish() ? "¡Configuración guardada exitosamente!" : "Configuration saved successfully!",
                isSpanish() ? "Éxito" : "Success",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void loadConfiguration() {
        hourField.setText(prefs.get("interval_hours", "0"));
        minuteField.setText(prefs.get("interval_minutes", "0"));
        secondField.setText(prefs.get("interval_seconds", "1"));
        millisecondField.setText(prefs.get("interval_milliseconds", "0"));
        holdHourField.setText(prefs.get("hold_hours", "0"));
        holdMinuteField.setText(prefs.get("hold_minutes", "0"));
        holdSecondField.setText(prefs.get("hold_seconds", "0"));
        holdMillisecondField.setText(prefs.get("hold_milliseconds", "0"));

        boolean holdEnabled = prefs.getBoolean("hold_enabled", false);
        holdCheckBox.setSelected(holdEnabled);
        holdPanelWrapper.setVisible(holdEnabled);

        int actionIdx = prefs.getInt("action_type_index", 0);
        if (actionIdx >= 0 && actionIdx < actionTypeComboBox.getItemCount()) {
            actionTypeComboBox.setSelectedIndex(actionIdx);
        }

        int clickIdx = prefs.getInt("click_type_index", 0);
        if (clickIdx >= 0 && clickIdx < clickTypeComboBox.getItemCount()) {
            clickTypeComboBox.setSelectedIndex(clickIdx);
        }

        shortcutField.setText(prefs.get("shortcut", isSpanish() ? "Click en 'Asignar' para vincular" : "Click 'Set Shortcut' to bind"));

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
        keyField.setText(prefs.get("key_to_press", isSpanish() ? "Click para asignar tecla" : "Click to set key"));
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

    public static String getStandardizedKeyText(int keyCode) {
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

    // Vector Gear Icon for Settings
    private static class CogIcon implements Icon {
        private final int width;
        private final int height;

        public CogIcon(int size) {
            this.width = size;
            this.height = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.getForeground());

            double cx = x + width / 2.0;
            double cy = y + height / 2.0;
            double rOuter = width * 0.44;
            double rInner = width * 0.33;
            double rHole = width * 0.16;
            int teeth = 8;

            Path2D path = new Path2D.Double();
            for (int i = 0; i < teeth; i++) {
                double a0 = (i * 2.0 * Math.PI) / teeth;
                double a1 = a0 + 0.15;
                double a2 = a0 + (Math.PI / teeth) - 0.15;
                double a3 = a0 + (Math.PI / teeth);
                double a4 = ((i + 1) * 2.0 * Math.PI) / teeth;

                double x0 = cx + rInner * Math.cos(a0);
                double y0 = cy + rInner * Math.sin(a0);
                double x1 = cx + rOuter * Math.cos(a1);
                double y1 = cy + rOuter * Math.sin(a1);
                double x2 = cx + rOuter * Math.cos(a2);
                double y2 = cy + rOuter * Math.sin(a2);
                double x3 = cx + rInner * Math.cos(a3);
                double y3 = cy + rInner * Math.sin(a3);
                double x4 = cx + rInner * Math.cos(a4);
                double y4 = cy + rInner * Math.sin(a4);

                if (i == 0) {
                    path.moveTo(x0, y0);
                } else {
                    path.lineTo(x0, y0);
                }
                path.lineTo(x1, y1);
                path.lineTo(x2, y2);
                path.lineTo(x3, y3);
                path.lineTo(x4, y4);
            }
            path.closePath();

            Area gear = new Area(path);
            gear.subtract(new Area(new Ellipse2D.Double(cx - rHole, cy - rHole, rHole * 2, rHole * 2)));

            g2.fill(gear);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }

    // Vector Checkmark Icon for Option Menu Items
    private static class CheckIcon implements Icon {
        private final boolean checked;

        public CheckIcon(boolean checked) {
            this.checked = checked;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (!checked) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.getForeground());
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(x + 2, y + 8, x + 5, y + 12);
            g2.drawLine(x + 5, y + 12, x + 12, y + 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    // Getters for testing and programmatic inspection
    public JTextField getHourField() { return hourField; }
    public JTextField getMinuteField() { return minuteField; }
    public JTextField getSecondField() { return secondField; }
    public JTextField getMillisecondField() { return millisecondField; }
    public JTextField getHoldHourField() { return holdHourField; }
    public JTextField getHoldMinuteField() { return holdMinuteField; }
    public JTextField getHoldSecondField() { return holdSecondField; }
    public JTextField getHoldMillisecondField() { return holdMillisecondField; }
    public JComboBox<String> getActionTypeComboBox() { return actionTypeComboBox; }
    public JComboBox<String> getClickTypeComboBox() { return clickTypeComboBox; }
    public JTextField getKeyField() { return keyField; }
    public JPanel getActionDetailPanel() { return actionDetailPanel; }
    public JButton getStartStopButton() { return startStopButton; }
    public JLabel getStatusLabel() { return statusLabel; }
    public JCheckBox getHoldCheckBox() { return holdCheckBox; }
    public JPanel getHoldPanelWrapper() { return holdPanelWrapper; }
    public boolean isRunning() { return isRunning; }
    public int getActiveMousePress() { return activeMousePress; }
    public int getActiveKeyPress() { return activeKeyPress; }
}
