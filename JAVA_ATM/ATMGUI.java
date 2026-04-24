// =============================================================
// ATMGUI.java
// =============================================================
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.LinkedBlockingQueue;

public class ATMGUI extends JFrame {

    // =========================================================
    // INNER CLASS: GUIScreen
    // =========================================================
    public static class GUIScreen extends Screen {
        private final JTextArea displayArea;
        public GUIScreen(JTextArea area) { this.displayArea = area; }

        @Override
        public void displayMessage(String message) {
            SwingUtilities.invokeLater(() -> displayArea.append(message));
        }

        @Override
        public void displayMessageLine(String message) {
            SwingUtilities.invokeLater(() -> displayArea.append(message + "\n"));
        }

        @Override
        public void displayDollarAmount(double amount) {
            SwingUtilities.invokeLater(
                () -> displayArea.append(String.format("HK$%,.2f", amount)));
        }

        @Override
        public void displayRMBAmount(double amount) {
            SwingUtilities.invokeLater(
                () -> displayArea.append(String.format("RMB %,.2f", amount)));
        }
    }

    // =========================================================
    // INNER CLASS: GUIKeypad
    // =========================================================
    public static class GUIKeypad extends Keypad {
        private final LinkedBlockingQueue<String> inputQueue =
            new LinkedBlockingQueue<>();

        @Override
        public int getInput() {
            try {
                String s = inputQueue.take();
                if ("CANCEL".equals(s)) return 0;
                return (int) Double.parseDouble(s.trim());
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }

        public String getStringInput() {
            try { return inputQueue.take(); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "0";
            }
        }

        public void submitInput(String text) {
            try { inputQueue.put(text.trim()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        public void submitCancel() {
            try { inputQueue.put("CANCEL"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // =========================================================
    // FIELDS
    // =========================================================
    private final BankDatabase bankDatabase;
    private final CashDispenser cashDispenser;
    private GUIScreen guiScreen;
    private GUIKeypad guiKeypad;

    private volatile int     currentState;
    private volatile int     currentAccountNumber = 0;
    private volatile boolean userAuthenticated    = false;
    private volatile int     pinAttempts          = 0;

    private static final int STATE_WELCOME       = 0;
    private static final int STATE_ENTER_ACCOUNT = 1;
    private static final int STATE_ENTER_PIN     = 2;
    private static final int STATE_MAIN_MENU     = 3;
    private static final int STATE_WITHDRAW      = 4;
    private static final int STATE_TRANSFER      = 5;
    private static final int STATE_BALANCE       = 6;
    private static final int STATE_EJECT_CARD    = 7;
    private static final int STATE_POST_WITHDRAW = 8;

    // Exchange Rate
    private static final double RMB_TO_HKD_RATE = 1.13;

    // --- LCD components ---
    private JTextArea displayArea;
    private JLabel    inputBarField;
    private JPanel    amountGridPanel;

    // --- Side buttons ---
    private JButton[] leftBtns  = new JButton[2];
    private JButton[] rightBtns = new JButton[2];

    // --- Keypad buttons ---
    private JButton[] digitBtns = new JButton[10];
    private JButton   dotBtn, doubleZeroBtn;
    private JButton   enterBtn, clearBtn, cancelBtn;
    private JLabel    statusLabel;

    // Preset amounts
    private static final int[] PRESET_AMOUNTS = {200, 800, 400, 1000};

    private final StringBuilder inputBuffer = new StringBuilder();

    // =========================================================
    // CONSTRUCTOR
    // =========================================================
    public ATMGUI() {
        super("ATM Machine — HKD / RMB");
        bankDatabase  = new BankDatabase();
        cashDispenser = new CashDispenser();
        buildUI();
        setAtmState(STATE_WELCOME);
    }

    // =========================================================
    // BUILD UI
    // =========================================================
    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 820);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(new Color(55, 55, 65));

        add(buildHeader(),      BorderLayout.NORTH);
        add(buildScreenArea(),  BorderLayout.CENTER);
        add(buildKeypadPanel(), BorderLayout.SOUTH);

        setVisible(true);
    }

    // ----------------------------------------------------------
    // Header
    // ----------------------------------------------------------
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0, 51, 102));
        p.setBorder(new EmptyBorder(9, 16, 9, 16));

        JLabel title = new JLabel("  JAVA BANK ATM");
        title.setFont(new Font("Arial", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Secure · Fast · Reliable  ");
        sub.setFont(new Font("Arial", Font.ITALIC, 12));
        sub.setForeground(new Color(180, 210, 255));

        p.add(title, BorderLayout.WEST);
        p.add(sub,   BorderLayout.EAST);
        return p;
    }

    // ----------------------------------------------------------
    // Screen area
    // ----------------------------------------------------------
    private JPanel buildScreenArea() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setBackground(new Color(55, 55, 65));
        wrapper.setBorder(new EmptyBorder(14, 16, 6, 16));

        wrapper.add(buildPillColumn(true),  BorderLayout.WEST);
        wrapper.add(buildLCD(),             BorderLayout.CENTER);
        wrapper.add(buildPillColumn(false), BorderLayout.EAST);
        return wrapper;
    }

    // ----------------------------------------------------------
    // Pill side-button column
    // ----------------------------------------------------------
    private JPanel buildPillColumn(boolean isLeft) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        col.setBorder(new EmptyBorder(8, 6, 8, 6));

        for (int i = 0; i < 2; i++) {
            JButton btn = makePillSideButton();
            if (isLeft) leftBtns[i]  = btn;
            else        rightBtns[i] = btn;

            JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            cell.setOpaque(false);
            cell.add(btn);
            col.add(cell);
            if (i == 0) col.add(Box.createVerticalStrut(18));
        }
        col.add(Box.createVerticalGlue());

        if (isLeft) {
            leftBtns[0].addActionListener(e -> handleSideButton(0));
            leftBtns[1].addActionListener(e -> handleSideButton(2));
        } else {
            rightBtns[0].addActionListener(e -> handleSideButton(1));
            rightBtns[1].addActionListener(e -> handleSideButton(3));
        }
        return col;
    }

    private JButton makePillSideButton() {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed()
                    ? new Color(90, 90, 100)
                    : new Color(130, 130, 140));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                g2.setColor(new Color(70, 70, 80));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 30, 30);
                g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        btn.setPreferredSize(new Dimension(32, 72));
        btn.setMinimumSize (new Dimension(32, 72));
        btn.setMaximumSize (new Dimension(32, 72));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ----------------------------------------------------------
    // LCD
    // ----------------------------------------------------------
    private JPanel buildLCD() {
        JPanel lcd = new JPanel(new BorderLayout(0, 0));
        lcd.setBackground(new Color(10, 22, 10));
        lcd.setBorder(new LineBorder(new Color(0, 110, 0), 2));

        displayArea = new JTextArea(11, 36);
        displayArea.setEditable(false);
        displayArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        displayArea.setBackground(new Color(10, 22, 10));
        displayArea.setForeground(new Color(0, 215, 0));
        displayArea.setCaretColor(new Color(0, 215, 0));
        displayArea.setLineWrap(true);
        displayArea.setWrapStyleWord(true);
        displayArea.setMargin(new Insets(6, 10, 4, 10));

        JScrollPane scroll = new JScrollPane(displayArea);
        scroll.setBorder(null);
        scroll.setBackground(new Color(10, 22, 10));

        amountGridPanel = buildAmountDisplayGrid();
        amountGridPanel.setVisible(false);

        JPanel inputBar = buildInputBar();

        JPanel south = new JPanel(new BorderLayout(0, 0));
        south.setBackground(new Color(10, 22, 10));
        south.add(amountGridPanel, BorderLayout.CENTER);
        south.add(inputBar,        BorderLayout.SOUTH);

        lcd.add(scroll, BorderLayout.CENTER);
        lcd.add(south,  BorderLayout.SOUTH);
        return lcd;
    }

    // ----------------------------------------------------------
    // Amount display grid
    // ----------------------------------------------------------
    private JPanel buildAmountDisplayGrid() {
        JPanel container = new JPanel(new BorderLayout(0, 2));
        container.setBackground(new Color(10, 22, 10));
        container.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel title = new JLabel("Please select the amount", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.PLAIN, 12));
        title.setForeground(new Color(0, 200, 0));

        JPanel grid = new JPanel(new GridLayout(2, 2, 3, 3));
        grid.setBackground(new Color(10, 22, 10));
        String[] labels = {"200", "800", "400", "1000"};
        for (String lbl : labels) grid.add(makeAmountDisplayLabel(lbl));

        JLabel hint = new JLabel(
            "<html><center><font color='#AAAAAA'>or<br>"
            + "enter the amount and press "
            + "<font color='#00FF00'><b>enter</b></font>"
            + " button</font></center></html>",
            SwingConstants.CENTER);
        hint.setFont(new Font("Arial", Font.PLAIN, 11));

        container.add(title, BorderLayout.NORTH);
        container.add(grid,  BorderLayout.CENTER);
        container.add(hint,  BorderLayout.SOUTH);
        return container;
    }

    private JLabel makeAmountDisplayLabel(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
        lbl.setForeground(new Color(0, 220, 0));
        lbl.setBackground(new Color(20, 40, 20));
        lbl.setOpaque(true);
        lbl.setBorder(new LineBorder(new Color(0, 130, 0), 1));
        lbl.setPreferredSize(new Dimension(0, 30));
        return lbl;
    }

    // ----------------------------------------------------------
    // Input bar
    // ----------------------------------------------------------
    private JPanel buildInputBar() {
        JPanel bar = new JPanel(new BorderLayout(0, 0));
        bar.setBackground(new Color(5, 14, 5));
        bar.setBorder(new MatteBorder(1, 0, 0, 0, new Color(0, 90, 0)));

        JLabel lbl = new JLabel("Input:");
        lbl.setFont(new Font("Monospaced", Font.BOLD, 13));
        lbl.setForeground(new Color(0, 200, 0));
        lbl.setOpaque(true);
        lbl.setBackground(new Color(5, 14, 5));
        lbl.setBorder(new EmptyBorder(4, 8, 4, 6));
        lbl.setPreferredSize(new Dimension(62, 30));

        inputBarField = new JLabel("");
        inputBarField.setFont(new Font("Monospaced", Font.BOLD, 15));
        inputBarField.setForeground(new Color(0, 255, 0));
        inputBarField.setOpaque(true);
        inputBarField.setBackground(new Color(5, 14, 5));
        inputBarField.setBorder(new EmptyBorder(4, 2, 4, 8));

        bar.add(lbl,           BorderLayout.WEST);
        bar.add(inputBarField, BorderLayout.CENTER);
        return bar;
    }

    // ----------------------------------------------------------
    // Keypad panel
    // ----------------------------------------------------------
    private JPanel buildKeypadPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 6));
        outer.setBackground(new Color(55, 55, 65));
        outer.setBorder(new CompoundBorder(
            new EmptyBorder(4, 14, 14, 14),
            new CompoundBorder(
                new LineBorder(new Color(75, 75, 85), 2),
                new EmptyBorder(8, 10, 8, 10))));

        statusLabel = new JLabel("  Ready", SwingConstants.LEFT);
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        statusLabel.setForeground(new Color(180, 180, 190));
        outer.add(statusLabel, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(4, 4, 8, 8));
        grid.setOpaque(false);

        for (int i = 0; i <= 9; i++)
            digitBtns[i] = makeKeyBtn(String.valueOf(i),
                new Color(228, 228, 228), Color.BLACK);

        dotBtn        = makeKeyBtn(".",      new Color(228, 228, 228), Color.BLACK);
        doubleZeroBtn = makeKeyBtn("00",     new Color(228, 228, 228), Color.BLACK);
        cancelBtn     = makeKeyBtn("cancel", new Color(248, 105, 107), Color.BLACK);
        clearBtn      = makeKeyBtn("clear",  new Color(255, 235, 100), Color.BLACK);
        enterBtn      = makeKeyBtn("Enter",  new Color(100, 200, 100), Color.BLACK);

        // Row 1
        grid.add(digitBtns[7]); grid.add(digitBtns[8]);
        grid.add(digitBtns[9]); grid.add(cancelBtn);
        // Row 2
        grid.add(digitBtns[4]); grid.add(digitBtns[5]);
        grid.add(digitBtns[6]); grid.add(clearBtn);
        // Row 3
        grid.add(digitBtns[1]); grid.add(digitBtns[2]);
        grid.add(digitBtns[3]); grid.add(enterBtn);
        // Row 4
        grid.add(digitBtns[0]); grid.add(dotBtn);
        grid.add(doubleZeroBtn); grid.add(new JLabel());

        outer.add(grid, BorderLayout.CENTER);
        wireKeypadListeners();
        return outer;
    }

    private JButton makeKeyBtn(String label, Color bg, Color fg) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = getBackground();
                g2.setColor(getModel().isPressed() ? base.darker() : base);
                int arc = getHeight();
                g2.fillRoundRect(1, 1, getWidth()-2, getHeight()-2, arc, arc);
                g2.setColor(base.darker());
                g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, arc, arc);
                g2.setFont(getFont());
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(label)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, tx, ty);
                g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Arial", Font.BOLD, 15));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(70, 48));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // =========================================================
    // EVENT WIRING
    // =========================================================
    private void wireKeypadListeners() {
        for (int i = 0; i <= 9; i++) {
            final String d = String.valueOf(i);
            digitBtns[i].addActionListener(e -> appendInput(d));
        }
        dotBtn.addActionListener(e -> {
            if (!inputBuffer.toString().contains(".")) appendInput(".");
        });
        doubleZeroBtn.addActionListener(e -> {
            appendInput("0"); appendInput("0");
        });
        enterBtn .addActionListener(e -> handleEnter());
        clearBtn .addActionListener(e -> handleClear());
        cancelBtn.addActionListener(e -> handleCancel());
    }

    // ── Input helpers ─────────────────────────────────────────
    private void appendInput(String ch) {
        inputBuffer.append(ch);
        // Show masked input during PIN entry, plain text otherwise
        if (currentState == STATE_ENTER_PIN) {
            inputBarField.setText("*".repeat(inputBuffer.length()));
        } else {
            inputBarField.setText(inputBuffer.toString());
        }
    }

    private void handleEnter() {
        String text = inputBuffer.toString().trim();
        clearInputBuffer();
        if (text.isEmpty()) text = "0";
        setStatus("Entered: " + (currentState == STATE_ENTER_PIN ? "****" : text));
        if (guiKeypad != null) guiKeypad.submitInput(text);
    }

    private void handleClear() {
        if (inputBuffer.length() > 0) {
            inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            // Keep masking consistent after clear
            if (currentState == STATE_ENTER_PIN) {
                inputBarField.setText("*".repeat(inputBuffer.length()));
            } else {
                inputBarField.setText(inputBuffer.toString());
            }
        }
        setStatus("Cleared last digit.");
    }

    private void handleCancel() {
        clearInputBuffer();
        setStatus("Cancelled.");
        if (guiKeypad != null) guiKeypad.submitCancel();
        Timer t = new Timer(600, e -> setAtmState(STATE_WELCOME));
        t.setRepeats(false);
        t.start();
    }

    private void clearInputBuffer() {
        inputBuffer.setLength(0);
        inputBarField.setText("");
    }

    // =========================================================
    // SIDE-BUTTON HANDLER
    // =========================================================
    private void handleSideButton(int idx) {
        switch (currentState) {
            case STATE_MAIN_MENU:
                if (guiKeypad != null)
                    guiKeypad.submitInput(String.valueOf(idx + 1));
                break;
            case STATE_WITHDRAW:
                if (guiKeypad != null)
                    guiKeypad.submitInput(String.valueOf(PRESET_AMOUNTS[idx]));
                break;
            case STATE_POST_WITHDRAW:
                if (guiKeypad != null) {
                    int option = idx + 1;
                    if (option <= 3)
                        guiKeypad.submitInput(String.valueOf(option));
                }
                break;
            default:
                break;
        }
    }

    // =========================================================
    // STATE MACHINE
    // =========================================================
    private void setAtmState(int state) {
        currentState = state;

        SwingUtilities.invokeLater(() ->
            amountGridPanel.setVisible(state == STATE_WITHDRAW));

        boolean kbEnabled   = (state != STATE_EJECT_CARD
                             && state != STATE_MAIN_MENU
                             && state != STATE_POST_WITHDRAW);
        boolean sideEnabled = (state == STATE_MAIN_MENU
                             || state == STATE_WITHDRAW
                             || state == STATE_POST_WITHDRAW);
        setKeypadEnabled(kbEnabled);
        setSideButtonsEnabled(sideEnabled);

        switch (state) {
            case STATE_WELCOME:
                clearDisplay();
                appendDisplay("╔══════════════════════════════════╗");
                appendDisplay("║    Welcome to JAVA BANK ATM      ║");
                appendDisplay("║                                  ║");
                appendDisplay("║    Please insert your card.      ║");
                appendDisplay("╚══════════════════════════════════╝");
                setStatus("Welcome");
                setAtmState(STATE_ENTER_ACCOUNT);
                break;

            case STATE_ENTER_ACCOUNT:
                currentState = STATE_ENTER_ACCOUNT;
                clearDisplay();
                appendDisplay("━━━━  ACCOUNT NUMBER  ━━━━");
                appendDisplay("");
                appendDisplay("  Enter your account number");
                appendDisplay("  and press Enter.");
                appendDisplay("");
                appendDisplay("  (Test: 12345  PIN: 54321)");
                appendDisplay("  (Test: 98765  PIN: 56789)");
                setStatus("Enter account number, press Enter");
                startATMThread();
                break;

            case STATE_ENTER_PIN:
                clearDisplay();
                appendDisplay("━━━━  PIN ENTRY  ━━━━");
                appendDisplay("");
                appendDisplay("  Enter your PIN and press Enter.");
                appendDisplay("  Input is hidden for security.");
                if (pinAttempts > 0)
                    appendDisplay("\n  ⚠ Wrong PIN — attempt " + pinAttempts + " of 3");
                setStatus("Enter PIN, press Enter");
                break;

            case STATE_MAIN_MENU:
                clearDisplay();
                appendDisplay("━━━━  MAIN MENU  ━━━━");
                appendDisplay("");
                appendDisplay("  1. View Balance  │  2. Withdraw Cash");
                appendDisplay("  ─────────────────────────────────────");
                appendDisplay("  3. Transfer Funds│  4. Exit / Log Out");
                appendDisplay("");
                appendDisplay("  Use the side buttons to select.");
                setStatus("Select an option with the side buttons");
                break;

            case STATE_WITHDRAW:
                clearDisplay();
                appendDisplay("━━━━  WITHDRAWAL  ━━━━");
                appendDisplay("");
                appendDisplay("  Select a preset amount (side buttons)");
                appendDisplay("  or type an amount and press Enter.");
                setStatus("Select or type amount, press Enter");
                break;

            case STATE_TRANSFER:
                clearDisplay();
                appendDisplay("━━━━  TRANSFER FUNDS  ━━━━");
                appendDisplay("");
                appendDisplay("  Step 1 of 3:");
                appendDisplay("  Type the TARGET account number,");
                appendDisplay("  then press Enter.");
                setStatus("Type target account number, press Enter");
                break;

            case STATE_BALANCE:
                setStatus("Viewing balance...");
                break;

            case STATE_POST_WITHDRAW:
                clearDisplay();
                appendDisplay("━━━━  TRANSACTION COMPLETE  ━━━━");
                appendDisplay("");
                appendDisplay("  Would you like to:");
                appendDisplay("");
                appendDisplay("  1 - Print receipt & Log out");
                appendDisplay("  2 - Perform another transaction");
                appendDisplay("  3 - Log out directly");
                appendDisplay("");
                appendDisplay("  Use side buttons to select.");
                setStatus("Select an option with the side buttons");
                break;

            case STATE_EJECT_CARD:
                clearDisplay();
                appendDisplay("━━━━  SESSION ENDED  ━━━━");
                appendDisplay("");
                appendDisplay("  ▶ Please take your card.");
                userAuthenticated    = false;
                currentAccountNumber = 0;
                pinAttempts          = 0;
                setStatus("Card ejected. Goodbye!");
                Timer t = new Timer(3000, e -> {
                    setKeypadEnabled(true);
                    setAtmState(STATE_WELCOME);
                });
                t.setRepeats(false);
                t.start();
                break;
        }
    }

    // =========================================================
    // ATM BACKGROUND THREAD
    // =========================================================
    private void startATMThread() {
        guiScreen = new GUIScreen(displayArea);
        guiKeypad = new GUIKeypad();
        Thread t  = new Thread(this::runATMSession, "ATM-Worker");
        t.setDaemon(true);
        t.start();
    }

    private void runATMSession() {

        // =====================================================
        // STEP 1: Account number — loop until valid or cancel
        // =====================================================
        int accountNumber = 0;
        boolean accountFound = false;

        while (!accountFound) {
            String rawAcc = guiKeypad.getStringInput();

            if ("CANCEL".equals(rawAcc)) {
                SwingUtilities.invokeLater(() -> setAtmState(STATE_WELCOME));
                return;
            }

            try {
                accountNumber = Integer.parseInt(rawAcc.trim());
            } catch (NumberFormatException e) {
                SwingUtilities.invokeLater(() -> {
                    clearDisplay();
                    appendDisplay("━━━━  ACCOUNT NUMBER  ━━━━");
                    appendDisplay("");
                    appendDisplay("  ✖ Invalid input.");
                    appendDisplay("  Please enter numbers only.");
                    appendDisplay("");
                    appendDisplay("  (Test: 12345  PIN: 54321)");
                    appendDisplay("  (Test: 98765  PIN: 56789)");
                    setStatus("Invalid input — enter account number again");
                    setKeypadEnabled(true);
                });
                sleep(1500);
                SwingUtilities.invokeLater(() -> {
                    clearDisplay();
                    appendDisplay("━━━━  ACCOUNT NUMBER  ━━━━");
                    appendDisplay("");
                    appendDisplay("  Enter your account number");
                    appendDisplay("  and press Enter.");
                    appendDisplay("");
                    appendDisplay("  (Test: 12345  PIN: 54321)");
                    appendDisplay("  (Test: 98765  PIN: 56789)");
                    setStatus("Enter account number, press Enter");
                });
                continue;
            }

            if (!bankDatabase.isAccountExist(accountNumber)) {
                final int badAcc = accountNumber;
                SwingUtilities.invokeLater(() -> {
                    clearDisplay();
                    appendDisplay("━━━━  ACCOUNT NUMBER  ━━━━");
                    appendDisplay("");
                    appendDisplay("  ✖ Account " + badAcc + " not found.");
                    appendDisplay("  Please try again.");
                    appendDisplay("");
                    appendDisplay("  (Test: 12345  PIN: 54321)");
                    appendDisplay("  (Test: 98765  PIN: 56789)");
                    setStatus("Account not found — try again or press Cancel");
                    setKeypadEnabled(true);
                });
                sleep(2000);
                SwingUtilities.invokeLater(() -> {
                    clearDisplay();
                    appendDisplay("━━━━  ACCOUNT NUMBER  ━━━━");
                    appendDisplay("");
                    appendDisplay("  Enter your account number");
                    appendDisplay("  and press Enter.");
                    appendDisplay("");
                    appendDisplay("  (Test: 12345  PIN: 54321)");
                    appendDisplay("  (Test: 98765  PIN: 56789)");
                    setStatus("Enter account number, press Enter");
                });
                continue;
            }

            accountFound = true;
        }

        currentAccountNumber = accountNumber;

        // =====================================================
        // STEP 2: PIN — up to 3 attempts
        // =====================================================
        SwingUtilities.invokeLater(() -> setAtmState(STATE_ENTER_PIN));
        sleep(150);

        boolean authenticated = false;
        for (pinAttempts = 0; pinAttempts < 3; pinAttempts++) {
            final int att = pinAttempts;
            if (att > 0) {
                SwingUtilities.invokeLater(() -> {
                    clearDisplay();
                    appendDisplay("━━━━  PIN ENTRY  ━━━━");
                    appendDisplay("\n  ⚠ Wrong PIN — attempt " + (att + 1) + " of 3");
                    appendDisplay("\n  Re-enter PIN and press Enter.");
                });
            }
            int pin = guiKeypad.getInput();
            if (bankDatabase.authenticateUser(accountNumber, pin)) {
                authenticated    = true;
                userAuthenticated = true;
                break;
            }
        }

        if (!authenticated) {
            SwingUtilities.invokeLater(() -> {
                clearDisplay();
                appendDisplay("━━━━  ACCESS DENIED  ━━━━");
                appendDisplay("\n  ✖ Too many wrong PIN attempts.");
                appendDisplay("  Your card has been retained.");
                setStatus("Card retained. Please contact bank.");
            });
            sleep(3500);
            SwingUtilities.invokeLater(() -> setAtmState(STATE_WELCOME));
            return;
        }

        // =====================================================
        // STEP 3: Main menu loop
        // =====================================================
        SwingUtilities.invokeLater(() -> setAtmState(STATE_MAIN_MENU));
        sleep(150);

        boolean running = true;
        while (running) {
            int choice = guiKeypad.getInput();
            switch (choice) {
                case 1: doBalance();              break;
                case 2: running = !doWithdraw();  break;
                case 3: doTransfer();             break;
                case 4: running = false;          break;
                default:
                    guiScreen.displayMessageLine("\n  ⚠ Use side buttons 1–4.");
                    break;
            }
            if (running) {
                SwingUtilities.invokeLater(() -> setAtmState(STATE_MAIN_MENU));
                sleep(150);
            }
        }
        SwingUtilities.invokeLater(() -> setAtmState(STATE_EJECT_CARD));
    }

    // =========================================================
    // TRANSACTIONS
    // =========================================================

    // ── Balance ──────────────────────────────────────────────
    private void doBalance() {
        SwingUtilities.invokeLater(() -> {
            currentState = STATE_BALANCE;
            clearDisplay();
            appendDisplay("━━━━  BALANCE INQUIRY  ━━━━");
            setKeypadEnabled(true);
        });
        sleep(100);

        double avail = bankDatabase.getAvailableBalance(currentAccountNumber);
        double total = bankDatabase.getTotalBalance(currentAccountNumber);

        guiScreen.displayMessageLine("\n  Account : " + currentAccountNumber);
        guiScreen.displayMessageLine("  ─────────────────────────────");
        guiScreen.displayMessage    ("  Available : ");
        guiScreen.displayDollarAmount(avail);
        guiScreen.displayMessageLine("");
        guiScreen.displayMessage    ("  Total     : ");
        guiScreen.displayDollarAmount(total);
        guiScreen.displayMessageLine("\n\n  Press Enter to return to menu.");
        guiKeypad.getStringInput();
    }

    // ── Withdrawal ───────────────────────────────────────────
    private boolean doWithdraw() {

        // -------------------------------------------------
        // Step 1: Currency Selection
        // -------------------------------------------------
        SwingUtilities.invokeLater(() -> {
            currentState = STATE_WITHDRAW;
            amountGridPanel.setVisible(false);
            clearDisplay();
            appendDisplay("━━━━  WITHDRAWAL  ━━━━");
            appendDisplay("");
            appendDisplay("  Select withdrawal currency:");
            appendDisplay("");
            appendDisplay("  1 - HKD (Hong Kong Dollar)");
            appendDisplay("  2 - RMB (Chinese Yuan)");
            appendDisplay("");
            appendDisplay("  Press Cancel to abort.");
            setKeypadEnabled(true);
            setSideButtonsEnabled(false);
            setStatus("Select currency: 1=HKD  2=RMB");
        });
        sleep(150);

        String currencyInput = guiKeypad.getStringInput();
        if ("CANCEL".equals(currencyInput)) return false;

        int currencyChoice;
        try { currencyChoice = Integer.parseInt(currencyInput.trim()); }
        catch (NumberFormatException e) { currencyChoice = 0; }

        final String currency;
        if      (currencyChoice == 1) currency = "HKD";
        else if (currencyChoice == 2) currency = "RMB";
        else {
            guiScreen.displayMessageLine("\n  ✖ Invalid selection.");
            sleep(1500);
            return false;
        }

        // -------------------------------------------------
        // Step 2: Amount Selection
        // -------------------------------------------------
        SwingUtilities.invokeLater(() -> {
            currentState = STATE_WITHDRAW;
            clearDisplay();
            appendDisplay("━━━━  WITHDRAWAL (" + currency + ")  ━━━━");
            appendDisplay("");
            appendDisplay("  Select a preset amount (side buttons)");
            appendDisplay("  or type an amount and press Enter.");
            appendDisplay("");
            appendDisplay("  Preset amounts are in " + currency);
            amountGridPanel.setVisible(true);
            setSideButtonsEnabled(true);
            setKeypadEnabled(true);
            setStatus("Select or type " + currency + " amount, press Enter");
        });
        sleep(150);

        String rawAmt = guiKeypad.getStringInput();
        if ("CANCEL".equals(rawAmt)) return false;

        double amount;
        try { amount = Double.parseDouble(rawAmt); }
        catch (NumberFormatException e) {
            guiScreen.displayMessageLine("\n  ✖ Invalid amount entered.");
            sleep(1500);
            return false;
        }

        SwingUtilities.invokeLater(() -> amountGridPanel.setVisible(false));

        if (amount <= 0) {
            guiScreen.displayMessageLine("\n  ✖ Amount must be positive.");
            sleep(1500);
            return false;
        }

        // -------------------------------------------------
        // Step 3: Currency Conversion
        // -------------------------------------------------
        final double withdrawalInHKD =
            currency.equals("HKD") ? amount : amount * RMB_TO_HKD_RATE;

        if (currency.equals("RMB")) {
            clearDisplay();
            appendDisplay("━━━━  CURRENCY CONVERSION  ━━━━");
            guiScreen.displayMessageLine("\n  Exchange Rate : 1 RMB = 1.13 HKD");
            guiScreen.displayMessage    ("  Amount (RMB)  : RMB ");
            guiScreen.displayMessageLine(String.format("%.2f", amount));
            guiScreen.displayMessage    ("  Converted HKD : ");
            guiScreen.displayDollarAmount(withdrawalInHKD);
            guiScreen.displayMessageLine("\n");
            sleep(2000);
        }

        // -------------------------------------------------
        // Step 4: Check available balance
        // -------------------------------------------------
        double available = bankDatabase.getAvailableBalance(currentAccountNumber);
        if (withdrawalInHKD > available) {
            clearDisplay();
            appendDisplay("━━━━  WITHDRAWAL  ━━━━");
            guiScreen.displayMessageLine("\n  ✖ Insufficient funds.");
            guiScreen.displayMessage    ("  Available : ");
            guiScreen.displayDollarAmount(available);
            guiScreen.displayMessageLine("\n\n  Press Enter to return.");
            SwingUtilities.invokeLater(() -> setKeypadEnabled(true));
            guiKeypad.getStringInput();
            return false;
        }

        if (!cashDispenser.isSufficientCashAvailable((int) amount)) {
            guiScreen.displayMessageLine("\n  ✖ ATM cannot dispense that amount.");
            sleep(2000);
            return false;
        }

        // -------------------------------------------------
        // Step 5: Confirmation
        // -------------------------------------------------
        final double finalAmount = amount;
        clearDisplay();
        appendDisplay("━━━━  CONFIRM WITHDRAWAL  ━━━━");

        if (currency.equals("RMB")) {
            guiScreen.displayMessageLine(
                String.format("\n  Withdraw  : RMB %.2f", finalAmount));
            guiScreen.displayMessageLine(
                String.format("  Equals    : HK$%.2f", withdrawalInHKD));
            guiScreen.displayMessageLine(
                "  Rate      : 1 RMB = 1.13 HKD");
        } else {
            guiScreen.displayMessageLine(
                String.format("\n  Withdraw HK$%.2f from account %d?",
                    finalAmount, currentAccountNumber));
        }

        guiScreen.displayMessageLine("\n  Press Enter to confirm.");
        guiScreen.displayMessageLine("  Press Cancel to abort.");
        SwingUtilities.invokeLater(() -> setKeypadEnabled(true));

        String conf = guiKeypad.getStringInput();
        if ("CANCEL".equals(conf)) {
            guiScreen.displayMessageLine("\n  Cancelled.");
            sleep(1200);
            return false;
        }

        // -------------------------------------------------
        // Step 6: Execute
        // -------------------------------------------------
        bankDatabase.debit(currentAccountNumber, withdrawalInHKD);
        cashDispenser.dispenseCash((int) amount);

        clearDisplay();
        appendDisplay("━━━━  WITHDRAWAL  ━━━━");
        guiScreen.displayMessageLine("\n  ✔ Cash dispensed!");

        if (currency.equals("RMB")) {
            guiScreen.displayMessage    ("  Amount (RMB) : RMB ");
            guiScreen.displayMessageLine(String.format("%.2f", finalAmount));
            guiScreen.displayMessage    ("  Amount (HKD) : ");
            guiScreen.displayDollarAmount(withdrawalInHKD);
            guiScreen.displayMessageLine("");
        } else {
            guiScreen.displayMessage    ("  Amount : HK$");
            guiScreen.displayMessageLine(String.format("%.2f", finalAmount));
        }

        guiScreen.displayMessage    ("\n  Balance : ");
        guiScreen.displayDollarAmount(
            bankDatabase.getAvailableBalance(currentAccountNumber));
        guiScreen.displayMessageLine("\n\n  ▶ Please take your cash.");
        setStatus("Cash dispensed. Please take your cash.");

        sleep(2500);

        // -------------------------------------------------
        // Step 7: Post-Withdrawal Menu
        // -------------------------------------------------
        SwingUtilities.invokeLater(() -> setAtmState(STATE_POST_WITHDRAW));
        sleep(150);

        final String receiptCurrency = currency;
        final double receiptAmount   = finalAmount;
        final double receiptHKD      = withdrawalInHKD;
        final double receiptBalance  =
            bankDatabase.getAvailableBalance(currentAccountNumber);

        int postChoice = guiKeypad.getInput();

        switch (postChoice) {
            case 1:
                printWithdrawalReceipt(receiptCurrency, receiptAmount,
                    receiptHKD, receiptBalance);
                sleep(3000);
                SwingUtilities.invokeLater(() -> setAtmState(STATE_EJECT_CARD));
                sleep(3500);
                return true;

            case 2:
                return false;

            case 3:
            default:
                SwingUtilities.invokeLater(() -> setAtmState(STATE_EJECT_CARD));
                sleep(3500);
                return true;
        }
    }

    // ── Print Withdrawal Receipt ──────────────────────────────
    private void printWithdrawalReceipt(String currency, double amount,
                                         double amountInHKD, double balance) {
        clearDisplay();
        appendDisplay("━━━━  RECEIPT  ━━━━");
        guiScreen.displayMessageLine("\n  ====== Withdrawal Receipt ======");
        guiScreen.displayMessageLine(
            "  Account  : " + currentAccountNumber);
        if (currency.equals("RMB")) {
            guiScreen.displayMessage("  Amount   : RMB ");
            guiScreen.displayMessageLine(String.format("%.2f", amount));
            guiScreen.displayMessage("  In HKD   : ");
            guiScreen.displayDollarAmount(amountInHKD);
            guiScreen.displayMessageLine("");
            guiScreen.displayMessageLine("  Rate     : 1 RMB = 1.13 HKD");
        } else {
            guiScreen.displayMessage("  Amount   : ");
            guiScreen.displayDollarAmount(amount);
            guiScreen.displayMessageLine("");
        }
        guiScreen.displayMessage("  Balance  : ");
        guiScreen.displayDollarAmount(balance);
        guiScreen.displayMessageLine("\n  ================================");
        guiScreen.displayMessageLine("  Thank you for using JAVA BANK ATM!");
        setStatus("Receipt printed. Please take your card.");
    }

    // ── Transfer ─────────────────────────────────────────────
    private void doTransfer() {

        // -------------------------------------------------
        // Sub-step 1: Target account
        // -------------------------------------------------
        SwingUtilities.invokeLater(() -> {
            currentState = STATE_TRANSFER;
            clearDisplay();
            appendDisplay("━━━━  TRANSFER FUNDS  ━━━━");
            appendDisplay("");
            appendDisplay("  Step 1 of 3:");
            appendDisplay("  Type the TARGET account number");
            appendDisplay("  and press Enter.");
            setStatus("Type target account number, press Enter");
            setKeypadEnabled(true);
        });
        sleep(150);

        String targetRaw = guiKeypad.getStringInput();
        if ("CANCEL".equals(targetRaw)) {
            guiScreen.displayMessageLine("\n  Cancelled.");
            sleep(1000);
            return;
        }

        int targetAcc;
        try { targetAcc = Integer.parseInt(targetRaw.trim()); }
        catch (NumberFormatException e) {
            guiScreen.displayMessageLine("\n  ✖ Invalid account number.");
            sleep(1500);
            return;
        }

        if (!bankDatabase.isAccountExist(targetAcc)) {
            clearDisplay();
            appendDisplay("━━━━  TRANSFER FUNDS  ━━━━");
            guiScreen.displayMessageLine("\n  ✖ Account " + targetAcc + " not found.");
            guiScreen.displayMessageLine("  Press Enter to return.");
            SwingUtilities.invokeLater(() -> setKeypadEnabled(true));
            guiKeypad.getStringInput();
            return;
        }

        if (targetAcc == currentAccountNumber) {
            clearDisplay();
            appendDisplay("━━━━  TRANSFER FUNDS  ━━━━");
            guiScreen.displayMessageLine("\n  ✖ Cannot transfer to your own account.");
            guiScreen.displayMessageLine("  Press Enter to return.");
            SwingUtilities.invokeLater(() -> setKeypadEnabled(true));
            guiKeypad.getStringInput();
            return;
        }

        final int finalTarget = targetAcc;

        // -------------------------------------------------
        // Sub-step 2: Currency Selection
        // -------------------------------------------------
        SwingUtilities.invokeLater(() -> {
            clearDisplay();
            appendDisplay("━━━━  TRANSFER FUNDS  ━━━━");
            appendDisplay("");
            appendDisplay("  To account : " + finalTarget);
            appendDisplay("");
            appendDisplay("  Step 2 of 3: Select currency:");
            appendDisplay("");
            appendDisplay("  1 - HKD (Hong Kong Dollar)");
            appendDisplay("  2 - RMB (Chinese Yuan)");
            appendDisplay("      Rate: 1 RMB = 1.13 HKD");
            appendDisplay("");
            appendDisplay("  Press Cancel to abort.");
            setStatus("Select currency: 1=HKD  2=RMB");
            setKeypadEnabled(true);
        });
        sleep(150);

        String currencyInput = guiKeypad.getStringInput();
        if ("CANCEL".equals(currencyInput)) {
            guiScreen.displayMessageLine("\n  Cancelled.");
            sleep(1000);
            return;
        }

        int currencyChoice;
        try { currencyChoice = Integer.parseInt(currencyInput.trim()); }
        catch (NumberFormatException e) { currencyChoice = 0; }

        final String currency;
        if      (currencyChoice == 1) currency = "HKD";
        else if (currencyChoice == 2) currency = "RMB";
        else {
            guiScreen.displayMessageLine("\n  ✖ Invalid currency selection.");
            sleep(1500);
            return;
        }

        // -------------------------------------------------
        // Sub-step 3: Amount
        // -------------------------------------------------
        SwingUtilities.invokeLater(() -> {
            clearDisplay();
            appendDisplay("━━━━  TRANSFER FUNDS  ━━━━");
            appendDisplay("");
            appendDisplay("  To account : " + finalTarget);
            appendDisplay("  Currency   : " + currency);
            appendDisplay("");
            appendDisplay("  Step 3 of 3:");
            appendDisplay("  Type the AMOUNT (" + currency + ") to transfer");
            appendDisplay("  and press Enter.");
            appendDisplay("  (Decimals allowed, e.g. 9.90, 150.50)");
            setStatus("Type transfer amount (" + currency + "), press Enter");
        });
        sleep(100);

        String amtRaw = guiKeypad.getStringInput();
        if ("CANCEL".equals(amtRaw)) {
            guiScreen.displayMessageLine("\n  Cancelled.");
            sleep(1000);
            return;
        }

        double amount;
        try { amount = Double.parseDouble(amtRaw.trim()); }
        catch (NumberFormatException e) {
            guiScreen.displayMessageLine("\n  ✖ Invalid amount.");
            sleep(1500);
            return;
        }

        if (amount <= 0) {
            guiScreen.displayMessageLine("\n  ✖ Amount must be positive.");
            sleep(1500);
            return;
        }

        // -------------------------------------------------
        // Currency Conversion
        // -------------------------------------------------
        final double transferInHKD =
            currency.equals("HKD") ? amount : amount * RMB_TO_HKD_RATE;

        if (currency.equals("RMB")) {
            clearDisplay();
            appendDisplay("━━━━  CURRENCY CONVERSION  ━━━━");
            guiScreen.displayMessageLine("\n  Exchange Rate : 1 RMB = 1.13 HKD");
            guiScreen.displayMessage    ("  Amount (RMB)  : RMB ");
            guiScreen.displayMessageLine(String.format("%.2f", amount));
            guiScreen.displayMessage    ("  Converted HKD : ");
            guiScreen.displayDollarAmount(transferInHKD);
            guiScreen.displayMessageLine("\n");
            sleep(2000);
        }

        // -------------------------------------------------
        // Check balance
        // -------------------------------------------------
        double available = bankDatabase.getAvailableBalance(currentAccountNumber);
        if (transferInHKD > available) {
            clearDisplay();
            appendDisplay("━━━━  TRANSFER FUNDS  ━━━━");
            guiScreen.displayMessageLine("\n  ✖ Insufficient funds.");
            guiScreen.displayMessage    ("  Available : ");
            guiScreen.displayDollarAmount(available);
            guiScreen.displayMessageLine("\n  Press Enter to return.");
            SwingUtilities.invokeLater(() -> setKeypadEnabled(true));
            guiKeypad.getStringInput();
            return;
        }

        // -------------------------------------------------
        // Sub-step 4: Confirmation
        // -------------------------------------------------
        final double finalAmount = amount;
        final double finalInHKD  = transferInHKD;
        SwingUtilities.invokeLater(() -> {
            clearDisplay();
            appendDisplay("━━━━  CONFIRM TRANSFER  ━━━━");
            appendDisplay("");
            appendDisplay("  From     : " + currentAccountNumber);
            appendDisplay("  To       : " + finalTarget);
            appendDisplay("  Currency : " + currency);
            if (currency.equals("RMB")) {
                appendDisplay(String.format("  Amount   : RMB %.2f",  finalAmount));
                appendDisplay(String.format("  Rate     : 1 RMB = %.2f HKD",
                    RMB_TO_HKD_RATE));
                appendDisplay(String.format("  In HKD   : HK$%.2f",  finalInHKD));
            } else {
                appendDisplay(String.format("  Amount   : HK$%.2f",  finalAmount));
            }
            appendDisplay("  Fee      : HK$0.00 (No Fee)");
            appendDisplay("");
            appendDisplay("  Press Enter  to CONFIRM");
            appendDisplay("  Press Cancel to ABORT");
            setStatus("Enter = confirm, Cancel = abort");
            setKeypadEnabled(true);
        });
        sleep(100);

        String conf = guiKeypad.getStringInput();
        if ("CANCEL".equals(conf)) {
            clearDisplay();
            appendDisplay("━━━━  TRANSFER FUNDS  ━━━━");
            guiScreen.displayMessageLine("\n  Transfer aborted.");
            sleep(1500);
            return;
        }

        // -------------------------------------------------
        // Execute transfer
        // -------------------------------------------------
        bankDatabase.debit (currentAccountNumber, transferInHKD);
        bankDatabase.credit(finalTarget,          transferInHKD);

        clearDisplay();
        appendDisplay("━━━━  TRANSFER COMPLETE  ━━━━");
        guiScreen.displayMessageLine("\n  ✔ Transfer successful!");
        guiScreen.displayMessageLine("  To account : " + finalTarget);

        if (currency.equals("RMB")) {
            guiScreen.displayMessage    ("  Amount (RMB) : RMB ");
            guiScreen.displayMessageLine(String.format("%.2f", finalAmount));
            guiScreen.displayMessage    ("  Amount (HKD) : ");
            guiScreen.displayDollarAmount(finalInHKD);
            guiScreen.displayMessageLine("");
        } else {
            guiScreen.displayMessage    ("  Amount : HK$");
            guiScreen.displayMessageLine(String.format("%.2f", finalAmount));
        }

        guiScreen.displayMessage    ("  New balance : ");
        guiScreen.displayDollarAmount(
            bankDatabase.getAvailableBalance(currentAccountNumber));
        guiScreen.displayMessageLine("\n\n  Press Enter to return to menu.");
        setStatus("Transfer complete.");
        SwingUtilities.invokeLater(() -> setKeypadEnabled(true));
        guiKeypad.getStringInput();
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private void clearDisplay() {
        SwingUtilities.invokeLater(() -> displayArea.setText(""));
    }

    private void appendDisplay(String line) {
        SwingUtilities.invokeLater(() -> displayArea.append(line + "\n"));
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("  " + msg));
    }

    private void setKeypadEnabled(boolean on) {
        SwingUtilities.invokeLater(() -> {
            for (JButton b : digitBtns)  if (b != null) b.setEnabled(on);
            if (dotBtn        != null) dotBtn.setEnabled(on);
            if (doubleZeroBtn != null) doubleZeroBtn.setEnabled(on);
            if (enterBtn      != null) enterBtn.setEnabled(on);
            if (clearBtn      != null) clearBtn.setEnabled(on);
            if (cancelBtn     != null) cancelBtn.setEnabled(on);
        });
    }

    private void setSideButtonsEnabled(boolean on) {
        SwingUtilities.invokeLater(() -> {
            for (JButton b : leftBtns)  if (b != null) b.setEnabled(on);
            for (JButton b : rightBtns) if (b != null) b.setEnabled(on);
        });
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // =========================================================
    // ENTRY POINT
    // =========================================================
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(ATMGUI::new);
    }
}
