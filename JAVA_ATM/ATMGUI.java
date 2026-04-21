// =============================================================
// ATMGUI.java
// Complete Swing GUI for the ATM simulation system.
//
// Design principle: This file REPLACES only the console I/O
// (Screen + Keypad) with GUI equivalents, then runs the
// EXISTING ATM workflow on a background thread so the EDT
// stays responsive. No logic in Account, BankDatabase,
// CashDispenser, Transaction, BalanceInquiry, Withdrawal,
// Transfer, or ATM is modified.
// =============================================================

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ATMGUI extends JFrame {

    // =========================================================
    // INNER CLASS: GUIScreen
    // Replaces Screen.java — routes all display calls to the
    // JTextArea on the EDT using SwingUtilities.invokeLater.
    // =========================================================
    public static class GUIScreen extends Screen {

        private final JTextArea displayArea; // reference to the ATM display

        public GUIScreen(JTextArea area) {
            this.displayArea = area;
        }

        /** Append text WITHOUT a newline (mirrors Screen.displayMessage) */
        @Override
        public void displayMessage(String message) {
            // Must update Swing components on the Event Dispatch Thread
            SwingUtilities.invokeLater(() -> displayArea.append(message));
        }

        /** Append text WITH a newline (mirrors Screen.displayMessageLine) */
        @Override
        public void displayMessageLine(String message) {
            SwingUtilities.invokeLater(() -> displayArea.append(message + "\n"));
        }

        /** Format and append HKD amount */
        @Override
        public void displayDollarAmount(double amount) {
            SwingUtilities.invokeLater(
                () -> displayArea.append(String.format("HK$%,.2f", amount))
            );
        }

        /** Format and append RMB amount */
        @Override
        public void displayRMBAmount(double amount) {
            SwingUtilities.invokeLater(
                () -> displayArea.append(String.format("RMB %,.2f", amount))
            );
        }

        /** Clear the display area and show a fresh message */
        public void clearAndShow(String message) {
            SwingUtilities.invokeLater(() -> {
                displayArea.setText(""); // wipe previous content
                displayArea.append(message + "\n");
            });
        }
    }

    // =========================================================
    // INNER CLASS: GUIKeypad
    // Replaces Keypad.java — blocks the ATM background thread
    // on a LinkedBlockingQueue until the user presses "Enter"
    // in the GUI, then returns the buffered integer value.
    // =========================================================
    public static class GUIKeypad extends Keypad {

        // Thread-safe queue: GUI thread puts values, ATM thread takes them
        private final LinkedBlockingQueue<Integer> inputQueue =
                new LinkedBlockingQueue<>();

        /**
         * Called by ATM background thread.
         * BLOCKS until the user submits input via the GUI keypad.
         * Returns -1 on interrupt (safe fallback).
         */
        @Override
        public int getInput() {
            try {
                // take() blocks indefinitely until a value is available
                return inputQueue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore interrupt flag
                return -1; // safe sentinel value
            }
        }

        /**
         * Called by the GUI "Enter" button handler on the EDT.
         * Parses the string and enqueues it for the ATM thread.
         */
        public void submitInput(String text) {
            try {
                int value = Integer.parseInt(text.trim());
                inputQueue.put(value); // unblocks the waiting ATM thread
            } catch (NumberFormatException e) {
                // If the user typed non-numeric text, show a 0 (safe default)
                try { inputQueue.put(0); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Enqueue a cancel signal (value = 0) so blocked ATM thread can exit.
         * Used when the user presses the "Cancel" button.
         */
        public void submitCancel() {
            try { inputQueue.put(0); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // =========================================================
    // MAIN ATMGUI FIELDS
    // =========================================================

    // --- ATM business objects (keep existing classes intact) ---
    private final BankDatabase bankDatabase;
    private final CashDispenser cashDispenser;
    private GUIScreen  guiScreen;   // our Swing-aware Screen
    private GUIKeypad  guiKeypad;   // our queue-based Keypad

    // --- ATM workflow state (for GUI-side awareness only) ---
    private volatile int  currentState;       // current workflow step
    private volatile int  currentAccountNumber = 0;
    private volatile boolean userAuthenticated = false;
    private volatile int  pinAttempts = 0;    // count failed PIN tries

    // Workflow state constants
    private static final int STATE_WELCOME       = 0;
    private static final int STATE_ENTER_ACCOUNT = 1;
    private static final int STATE_ENTER_PIN     = 2;
    private static final int STATE_MAIN_MENU     = 3;
    private static final int STATE_IN_TRANSACTION= 4;
    private static final int STATE_EJECT_CARD    = 5;

    // --- GUI Components ---
    private JTextArea      displayArea;    // ATM "screen"
    private JPasswordField pinField;       // masked PIN entry
    private JTextField     inputField;     // numeric input display
    private JButton[]      numButtons;     // 0-9 digit buttons
    private JButton        enterBtn;
    private JButton        clearBtn;
    private JButton        cancelBtn;
    private JLabel         statusLabel;   // status bar at bottom

    // Buffer for digits pressed before Enter is hit
    private final StringBuilder inputBuffer = new StringBuilder();

    // =========================================================
    // CONSTRUCTOR
    // =========================================================
    public ATMGUI() {
        super("🏦  ATM Machine — HKD / RMB");

        // Instantiate the existing business-logic objects
        bankDatabase  = new BankDatabase();
        cashDispenser = new CashDispenser();

        buildUI();           // construct all Swing components
        setAtmState(STATE_WELCOME); // show welcome screen
    }

    // =========================================================
    // UI CONSTRUCTION
    // =========================================================

    /** Assemble and display the complete ATM window. */
    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(680, 620);
        setLocationRelativeTo(null); // centre on screen
        setResizable(false);
        setLayout(new BorderLayout(0, 0));

        // ----- HEADER -----
        JPanel headerPanel = buildHeader();
        add(headerPanel, BorderLayout.NORTH);

        // ----- CENTRE: ATM display screen -----
        JPanel screenPanel = buildScreen();
        add(screenPanel, BorderLayout.CENTER);

        // ----- SOUTH: keypad + input -----
        JPanel keypadPanel = buildKeypad();
        add(keypadPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    /** Branded header panel */
    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(0, 51, 102)); // dark bank-blue
        panel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel bankName = new JLabel("★  JAVA BANK ATM");
        bankName.setFont(new Font("Arial", Font.BOLD, 22));
        bankName.setForeground(Color.WHITE);

        JLabel subTitle = new JLabel("Secure · Fast · Reliable");
        subTitle.setFont(new Font("Arial", Font.ITALIC, 12));
        subTitle.setForeground(new Color(180, 210, 255));

        panel.add(bankName, BorderLayout.WEST);
        panel.add(subTitle, BorderLayout.EAST);
        return panel;
    }

    /** Green-on-black ATM display area */
    private JPanel buildScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new CompoundBorder(
                new EmptyBorder(10, 15, 5, 15),
                new LineBorder(new Color(0, 150, 0), 2)));

        // JTextArea simulates the ATM LCD screen
        displayArea = new JTextArea(12, 50);
        displayArea.setEditable(false);               // read-only display
        displayArea.setFont(new Font("Monospaced", Font.PLAIN, 15));
        displayArea.setBackground(new Color(10, 20, 10));  // near-black green
        displayArea.setForeground(new Color(0, 230, 0));   // bright green text
        displayArea.setCaretColor(new Color(0, 230, 0));
        displayArea.setLineWrap(true);
        displayArea.setWrapStyleWord(true);
        displayArea.setMargin(new Insets(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(displayArea);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    /** Keypad panel: digit buttons + Enter/Clear/Cancel + input fields */
    private JPanel buildKeypad() {
        JPanel outer = new JPanel(new BorderLayout(5, 5));
        outer.setBorder(new EmptyBorder(5, 15, 10, 15));
        outer.setBackground(new Color(230, 230, 230));

        // ---- Input fields row ----
        JPanel fieldRow = new JPanel(new GridLayout(1, 2, 10, 0));
        fieldRow.setOpaque(false);

        // Regular text field for account number / amounts
        inputField = new JTextField();
        inputField.setFont(new Font("Monospaced", Font.BOLD, 18));
        inputField.setHorizontalAlignment(JTextField.CENTER);
        inputField.setEditable(false);  // user presses buttons, not keyboard
        inputField.setBorder(BorderFactory.createTitledBorder("Input"));

        // Password field — echoChar masks each digit with a bullet
        pinField = new JPasswordField();
        pinField.setFont(new Font("Monospaced", Font.BOLD, 18));
        pinField.setHorizontalAlignment(JTextField.CENTER);
        pinField.setEchoChar('●');      // ← PIN MASKING requirement
        pinField.setEditable(false);    // driven by numButtons
        pinField.setBorder(BorderFactory.createTitledBorder("PIN (hidden)"));
        pinField.setVisible(false);     // only shown during STATE_ENTER_PIN

        fieldRow.add(inputField);
        fieldRow.add(pinField);
        outer.add(fieldRow, BorderLayout.NORTH);

        // ---- Digit grid (1-9, then blank, 0, blank) ----
        JPanel digitGrid = new JPanel(new GridLayout(4, 3, 6, 6));
        digitGrid.setOpaque(false);
        numButtons = new JButton[10];

        for (int i = 1; i <= 9; i++) {
            numButtons[i] = makeDigitButton(i);
            digitGrid.add(numButtons[i]);
        }
        digitGrid.add(new JLabel()); // empty cell (bottom-left)
        numButtons[0] = makeDigitButton(0);
        digitGrid.add(numButtons[0]);
        digitGrid.add(new JLabel()); // empty cell (bottom-right)

        outer.add(digitGrid, BorderLayout.CENTER);

        // ---- Action buttons row ----
        JPanel actionRow = new JPanel(new GridLayout(1, 3, 6, 0));
        actionRow.setOpaque(false);

        enterBtn  = makeActionButton("ENTER",  Color.WHITE, Color.BLACK);
        clearBtn  = makeActionButton("CLEAR",  Color.WHITE, Color.BLACK);
        cancelBtn = makeActionButton("CANCEL", Color.WHITE, Color.BLACK);

        actionRow.add(enterBtn);
        actionRow.add(clearBtn);
        actionRow.add(cancelBtn);

        // ---- Status bar ----
        statusLabel = new JLabel(" Ready", SwingConstants.LEFT);
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

        JPanel bottomStrip = new JPanel(new BorderLayout());
        bottomStrip.setOpaque(false);
        bottomStrip.add(actionRow, BorderLayout.CENTER);
        bottomStrip.add(statusLabel, BorderLayout.SOUTH);

        outer.add(bottomStrip, BorderLayout.SOUTH);

        // Wire up button listeners
        wireListeners();

        return outer;
    }

    /** Create a styled digit button */
    private JButton makeDigitButton(int digit) {
        JButton btn = new JButton(String.valueOf(digit));
        btn.setFont(new Font("Arial", Font.BOLD, 20));
        btn.setBackground(new Color(245, 245, 245));
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(70, 50));
        return btn;
    }

    /** Create a styled action button with given background and text colours */
    private JButton makeActionButton(String label, Color bg, Color fg) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("Arial", Font.BOLD, 14));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(100, 45));
        return btn;
    }

    // =========================================================
    // EVENT WIRING
    // =========================================================

    /** Attach ActionListeners to all buttons. */
    private void wireListeners() {

        // Digit buttons — append digit to buffer and update display fields
        for (int i = 0; i <= 9; i++) {
            final int digit = i;
            numButtons[i].addActionListener(e -> handleDigitPress(digit));
        }

        // ENTER — submit whatever is buffered
        enterBtn.addActionListener(e -> handleEnter());

        // CLEAR — erase last digit or whole buffer
        clearBtn.addActionListener(e -> handleClear());

        // CANCEL — send cancel signal and reset to welcome
        cancelBtn.addActionListener(e -> handleCancel());
    }

    // =========================================================
    // BUTTON HANDLERS  (all run on the EDT)
    // =========================================================

    /**
     * Appends a digit to the shared buffer and refreshes both
     * the regular input field and the masked PIN field.
     */
    private void handleDigitPress(int digit) {
        inputBuffer.append(digit);

        // Update the visible (non-PIN) field
        inputField.setText(inputBuffer.toString());

        // Update the PIN field (JPasswordField masks automatically)
        // We store the raw digits in pinField's text so getPassword() works
        pinField.setText(inputBuffer.toString());

        setStatus("Digit entered: " + inputBuffer.length() + " char(s)");
    }

    /**
     * "Enter" pressed: read buffer, clear it, then hand the
     * value to GUIKeypad so the ATM background thread unblocks.
     */
    private void handleEnter() {
    String text = inputBuffer.toString().trim();
    inputBuffer.setLength(0);
    inputField.setText("");
    pinField.setText("");

    if (text.isEmpty()) {
        // Instead of blocking, send a safe sentinel (e.g., 0)
        if (guiKeypad != null) {
            guiKeypad.submitInput("0"); // unblock ATM thread
        }
        setStatus("Acknowledged.");
        return;
    }

    setStatus("Submitted: " + (currentState == STATE_ENTER_PIN ? "****" : text));
    if (guiKeypad != null) {
        guiKeypad.submitInput(text);
    }
    }
    

    /** Removes the last digit from the buffer (backspace behaviour). */
    private void handleClear() {
        if (inputBuffer.length() > 0) {
            inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            inputField.setText(inputBuffer.toString());
            pinField.setText(inputBuffer.toString());
        }
        setStatus("Cleared.");
    }

    /**
     * Cancel: push a cancel sentinel to the queue so the
     * waiting ATM thread can break out, then reset the GUI.
     */
    private void handleCancel() {
        inputBuffer.setLength(0);
        inputField.setText("");
        pinField.setText("");
        setStatus("Transaction cancelled by user.");

        if (guiKeypad != null) {
            guiKeypad.submitCancel();   // ATM thread gets 0 = CANCEL
        }
        // GUI resets to welcome after a short delay
        Timer t = new Timer(1500, e -> setAtmState(STATE_WELCOME));
        t.setRepeats(false);
        t.start();
    }

    // =========================================================
    // STATE MACHINE  (controls GUI appearance per workflow step)
    // =========================================================

    /**
     * Switch the GUI into a new state.
     * Always called on the EDT.
     */
    private void setAtmState(int state) {
        currentState = state;

        // Show/hide the PIN field based on whether we need masking
        boolean isPinState = (state == STATE_ENTER_PIN);
        pinField.setVisible(isPinState);
        inputField.setVisible(!isPinState);

        // Enable/disable keypad based on whether input is expected
        boolean inputExpected = (state != STATE_EJECT_CARD);
        setKeypadEnabled(inputExpected);

        switch (state) {

            case STATE_WELCOME:
                clearDisplay();
                appendDisplay("╔══════════════════════════════════╗");
                appendDisplay("║    Welcome to JAVA BANK ATM!     ║");
                appendDisplay("║                                  ║");
                appendDisplay("║  Please insert your card and     ║");
                appendDisplay("║  enter your Account Number.      ║");
                appendDisplay("╚══════════════════════════════════╝");
                appendDisplay("");
                // Transition: automatically move to account-entry
                setAtmState(STATE_ENTER_ACCOUNT);
                break;

            case STATE_ENTER_ACCOUNT:
                currentState = STATE_ENTER_ACCOUNT;
                clearDisplay();
                appendDisplay("━━━━━━  ACCOUNT NUMBER  ━━━━━━");
                appendDisplay("");
                appendDisplay("  Please enter your account number");
                appendDisplay("  and press ENTER.");
                appendDisplay("");
                appendDisplay("  (Test accounts: 12345 or 98765)");
                setStatus("Enter account number");
                startATMThread(); // kick off the background ATM thread
                break;

            case STATE_ENTER_PIN:
                clearDisplay();
                appendDisplay("━━━━━━  PIN ENTRY  ━━━━━━");
                appendDisplay("");
                appendDisplay("  Enter your PIN and press ENTER.");
                appendDisplay("  Your PIN is hidden for security.");
                appendDisplay("");
                if (pinAttempts > 0) {
                    appendDisplay("  ⚠  Incorrect PIN. Attempt "
                            + pinAttempts + " of 3.");
                }
                setStatus("Enter PIN (masked)");
                break;

            case STATE_MAIN_MENU:
                clearDisplay();
                appendDisplay("━━━━━━  MAIN MENU  ━━━━━━");
                appendDisplay("");
                appendDisplay("  1 — View Balance");
                appendDisplay("  2 — Withdraw Cash");
                appendDisplay("  3 — Transfer Funds");
                appendDisplay("  4 — Exit / Log Out");
                appendDisplay("");
                appendDisplay("  Enter option and press ENTER.");
                setStatus("Authenticated — Account: " + currentAccountNumber);
                break;

            case STATE_IN_TRANSACTION:
                setStatus("Transaction in progress…");
                break;

            case STATE_EJECT_CARD:
                clearDisplay();
                appendDisplay("━━━━━━  SESSION ENDED  ━━━━━━");
                appendDisplay("");
                appendDisplay("  ▶  Please take your card.");
                appendDisplay("");
                userAuthenticated   = false;
                currentAccountNumber = 0;
                pinAttempts         = 0;
                setKeypadEnabled(false);
                setStatus("Card ejected. Session ended.");

                // Auto-return to Welcome after 3 seconds
                Timer ejectionTimer = new Timer(3000, e -> {
                    setKeypadEnabled(true);
                    setAtmState(STATE_WELCOME);
                });
                ejectionTimer.setRepeats(false);
                ejectionTimer.start();
                break;
        }
    }

    // =========================================================
    // ATM BACKGROUND THREAD
    // =========================================================

    /**
     * Runs the existing ATM workflow on a daemon thread so the
     * Event Dispatch Thread is never blocked.
     *
     * We create fresh GUIScreen / GUIKeypad instances each
     * session so the blocking queue starts empty.
     */
    private void startATMThread() {
        guiScreen = new GUIScreen(displayArea);
        guiKeypad = new GUIKeypad();

        Thread atmThread = new Thread(() -> runATMSession(), "ATM-Worker");
        atmThread.setDaemon(true); // thread dies when app closes
        atmThread.start();
    }

    /**
     * Full ATM session logic — mirrors ATM.run() but uses
     * GUIScreen / GUIKeypad instead of console I/O.
     * Runs entirely on the ATM-Worker thread.
     */
    private void runATMSession() {
        // ---- STEP 1: Account number entry ----
        guiScreen.displayMessageLine("\n  Enter your account number:");

        int accountNumber = guiKeypad.getInput();
        currentAccountNumber = accountNumber;

        // ---- STEP 2: PIN entry (up to 3 attempts) ----
        SwingUtilities.invokeLater(() -> setAtmState(STATE_ENTER_PIN));
        // Small sleep so the EDT can repaint before we block again
        sleep(200);

        boolean authenticated = false;
        for (pinAttempts = 0; pinAttempts < 3; pinAttempts++) {

            // Refresh PIN screen with attempt counter
            final int attempt = pinAttempts;
            if (attempt > 0) {
                SwingUtilities.invokeLater(() -> {
                    clearDisplay();
                    appendDisplay("━━━━━━  PIN ENTRY  ━━━━━━");
                    appendDisplay("");
                    appendDisplay("  ⚠  Incorrect PIN.");
                    appendDisplay("  Attempt " + (attempt + 1) + " of 3.");
                    appendDisplay("");
                    appendDisplay("  Please re-enter your PIN:");
                });
            }

            int pin = guiKeypad.getInput();

            if (bankDatabase.authenticateUser(accountNumber, pin)) {
                authenticated = true;
                userAuthenticated = true;
                break;
            }
        }

        // ---- Too many failed PIN attempts ----
        if (!authenticated) {
            SwingUtilities.invokeLater(() -> {
                clearDisplay();
                appendDisplay("━━━━━━  ACCESS DENIED  ━━━━━━");
                appendDisplay("");
                appendDisplay("  ✖  Too many incorrect PIN attempts.");
                appendDisplay("  Your card has been retained.");
                appendDisplay("  Please contact your bank.");
                setStatus("Authentication failed — card retained.");
            });
            sleep(3500);
            SwingUtilities.invokeLater(() -> setAtmState(STATE_WELCOME));
            return;
        }

        // ---- STEP 3: Show main menu ----
        SwingUtilities.invokeLater(() -> setAtmState(STATE_MAIN_MENU));
        sleep(200);

        // ---- STEP 4: Transaction loop ----
        boolean userExited = false;
        while (!userExited) {

            guiScreen.displayMessageLine("\n  Enter option (1-4):");
            int choice = guiKeypad.getInput();

            switch (choice) {
                case 1: // Balance inquiry
                    SwingUtilities.invokeLater(() -> {
                        currentState = STATE_IN_TRANSACTION;
                        clearDisplay();
                        appendDisplay("━━━━━━  BALANCE INQUIRY  ━━━━━━");
                    });
                    sleep(100);
                    performBalanceInquiry();
                    sleep(200);
                    SwingUtilities.invokeLater(() -> setAtmState(STATE_MAIN_MENU));
                    sleep(200);
                    break;

                case 2: // Withdrawal
                    SwingUtilities.invokeLater(() -> {
                        currentState = STATE_IN_TRANSACTION;
                        clearDisplay();
                        appendDisplay("━━━━━━  WITHDRAWAL  ━━━━━━");
                    });
                    sleep(100);
                    boolean dispensed = performWithdrawal();
                    sleep(200);
                    if (dispensed) {
                        // Show card-eject screen after successful withdrawal
                        SwingUtilities.invokeLater(() -> {
                            appendDisplay("");
                            appendDisplay("  ▶  Please take your cash.");
                        });
                        sleep(2500);
                        SwingUtilities.invokeLater(() -> setAtmState(STATE_EJECT_CARD));
                        sleep(3500);
                        // Re-authenticate for next use (return to welcome)
                        SwingUtilities.invokeLater(() -> setAtmState(STATE_WELCOME));
                        return; // end this session
                    } else {
                        SwingUtilities.invokeLater(() -> setAtmState(STATE_MAIN_MENU));
                        sleep(200);
                    }
                    break;

                case 3: // Transfer
                    SwingUtilities.invokeLater(() -> {
                        currentState = STATE_IN_TRANSACTION;
                        clearDisplay();
                        appendDisplay("━━━━━━  TRANSFER  ━━━━━━");
                    });
                    sleep(100);
                    performTransfer();
                    sleep(200);
                    SwingUtilities.invokeLater(() -> setAtmState(STATE_MAIN_MENU));
                    sleep(200);
                    break;

                case 4: // Exit
                    userExited = true;
                    break;

                default:
                    guiScreen.displayMessageLine(
                        "  ⚠  Invalid choice. Please select 1-4.");
                    break;
            }
        }

        // ---- STEP 5: Log out — eject card ----
        SwingUtilities.invokeLater(() -> setAtmState(STATE_EJECT_CARD));
    }

    // =========================================================
    // TRANSACTION METHODS  (ATM-Worker thread)
    // Uses existing BankDatabase / CashDispenser logic.
    // Dialogs are shown via SwingUtilities.invokeAndWait so
    // they appear on the EDT while the worker thread waits.
    // =========================================================

    /** Display available and total balance for the current account. */
    private void performBalanceInquiry() {
        double available = bankDatabase.getAvailableBalance(currentAccountNumber);
        double total     = bankDatabase.getTotalBalance(currentAccountNumber);

        guiScreen.displayMessageLine("");
        guiScreen.displayMessageLine(
            "  Account:           " + currentAccountNumber);
        guiScreen.displayMessageLine("  ─────────────────────────────");
        guiScreen.displayMessage("  Available Balance: ");
        guiScreen.displayDollarAmount(available);
        guiScreen.displayMessageLine("");
        guiScreen.displayMessage("  Total Balance:     ");
        guiScreen.displayDollarAmount(total);
        guiScreen.displayMessageLine("");
        guiScreen.displayMessageLine("  ─────────────────────────────");
        guiScreen.displayMessageLine("  Press ENTER to return to menu.");

        guiKeypad.getInput(); // wait for user to acknowledge
    }

    /**
     * Guides user through currency selection → amount selection
     * → balance/dispenser check → confirmation → debit.
     * Returns true if cash was successfully dispensed.
     */
    private boolean performWithdrawal() {
    
        // --- Currency selection via JOptionPane (EDT-safe) ---
        String[] currencies = {"HKD", "RMB"};
        final int[] curResult = {-1};
        try {
            SwingUtilities.invokeAndWait(() ->
                curResult[0] = JOptionPane.showOptionDialog(
                    this,
                    "Select withdrawal currency:",
                    "Withdrawal - Currency",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null, currencies, currencies[0])
            );
        } catch (Exception e) { return false; }
    
        if (curResult[0] == JOptionPane.CLOSED_OPTION) return false;
        String currency = currencies[curResult[0]];
    
        // --- Amount menu (updated) ---
        int[] amountsHKD = {100, 200, 400, 800, 1000};
        int[] amountsRMB = {100, 200, 400, 800, 1000};
        int[] amounts = currency.equals("HKD") ? amountsHKD : amountsRMB;
        String sym = currency.equals("HKD") ? "HK$" : "RMB ";
    
        // Build labels including "Other Amount"
        String[] amtLabels = new String[amounts.length + 1];
        for (int i = 0; i < amounts.length; i++) {
            amtLabels[i] = sym + amounts[i];
        }
        amtLabels[amounts.length] = "Other Amount";
    
        final String[] selectedStr = {null};
        try {
            SwingUtilities.invokeAndWait(() ->
                selectedStr[0] = (String) JOptionPane.showInputDialog(
                    this,
                    "Select withdrawal amount (" + currency + "):",
                    "Withdrawal - Amount",
                    JOptionPane.QUESTION_MESSAGE,
                    null, amtLabels, amtLabels[0])
            );
        } catch (Exception e) { return false; }
        if (selectedStr[0] == null) return false; // user cancelled
    
        // --- Handle fixed vs Other Amount ---
        int amount;
        if ("Other Amount".equals(selectedStr[0])) {
            String customStr = JOptionPane.showInputDialog(
                this,
                "Enter custom withdrawal amount (" + currency + "):",
                "Withdrawal - Custom Amount",
                JOptionPane.QUESTION_MESSAGE
            );
            if (customStr == null || customStr.trim().isEmpty()) return false;
            try {
                amount = Integer.parseInt(customStr.trim());
            } catch (NumberFormatException e) {
                guiScreen.displayMessageLine("✖ Invalid amount entered.");
                return false;
            }
        } else {
            try {
                amount = Integer.parseInt(selectedStr[0].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) { return false; }
        }
    
        // --- Convert to HKD for balance check ---
        double amountHKD = currency.equals("HKD") ? amount : amount * 1.13;
    
        // --- Check account balance ---
        double available = bankDatabase.getAvailableBalance(currentAccountNumber);
        if (amountHKD > available) {
            guiScreen.displayMessageLine("");
            guiScreen.displayMessageLine("✖ Insufficient funds in account.");
            guiScreen.displayMessage("Available: ");
            guiScreen.displayDollarAmount(available);
            guiScreen.displayMessageLine("");
            sleep(2000);
            return false;
        }
    
        // --- Check cash dispenser ---
        if (!cashDispenser.isSufficientCashAvailable(amount)) {
            guiScreen.displayMessageLine("✖ ATM has insufficient cash. Choose a smaller amount.");
            sleep(2000);
            return false;
        }
    
        // --- Confirmation dialog ---
        final int[] confirm = {JOptionPane.NO_OPTION};
        String confirmMsg = String.format(
            "Confirm withdrawal of %s%d?\n\nYour account will be debited HK$%.2f.",
            sym, amount, amountHKD
        );
        try {
            SwingUtilities.invokeAndWait(() ->
                confirm[0] = JOptionPane.showConfirmDialog(
                    this, confirmMsg,
                    "Confirm Withdrawal", JOptionPane.YES_NO_OPTION)
            );
        } catch (Exception e) { return false; }
    
        if (confirm[0] != JOptionPane.YES_OPTION) {
            guiScreen.displayMessageLine("Transaction cancelled.");
            return false;
        }
    
        // --- Execute debit and dispense ---
        bankDatabase.debit(currentAccountNumber, amountHKD);
        cashDispenser.dispenseCash(amount);
    
        guiScreen.displayMessageLine("");
        guiScreen.displayMessageLine("☒ Withdrawal successful!");
        guiScreen.displayMessage("Amount dispensed: " + sym);
        guiScreen.displayMessageLine(String.valueOf(amount));
        guiScreen.displayMessage("New balance: ");
        guiScreen.displayDollarAmount(bankDatabase.getAvailableBalance(currentAccountNumber));
        guiScreen.displayMessageLine("");
    
        return true; // signals caller to show "take cash" message
    }


    /**
     * Handles fund transfer: target account → currency →
     * amount → balance check → confirmation → debit/credit.
     */
    private void performTransfer() {

        // --- Target account ---
        final String[] targetStr = {null};
        try {
            SwingUtilities.invokeAndWait(() ->
                targetStr[0] = JOptionPane.showInputDialog(
                    this,
                    "Enter target account number\n(Test accounts: 12345, 98765):",
                    "Transfer — Target Account",
                    JOptionPane.QUESTION_MESSAGE)
            );
        } catch (Exception e) { return; }

        if (targetStr[0] == null || targetStr[0].trim().isEmpty()) return;

        int targetAcc;
        try { targetAcc = Integer.parseInt(targetStr[0].trim()); }
        catch (NumberFormatException e) {
            guiScreen.displayMessageLine("  ✖  Invalid account number."); return;
        }

        // Validate target account exists
        if (!bankDatabase.isAccountExist(targetAcc)) {
            guiScreen.displayMessageLine("  ✖  Target account does not exist.");
            return;
        }

        // Cannot transfer to own account
        if (targetAcc == currentAccountNumber) {
            guiScreen.displayMessageLine(
                "  ✖  Cannot transfer to your own account.");
            return;
        }

        // --- Currency ---
        String[] currencies = {"HKD", "RMB"};
        final int[] curIdx = {-1};
        try {
            SwingUtilities.invokeAndWait(() ->
                curIdx[0] = JOptionPane.showOptionDialog(
                    this, "Select transfer currency:",
                    "Transfer — Currency",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null, currencies, currencies[0])
            );
        } catch (Exception e) { return; }

        if (curIdx[0] == JOptionPane.CLOSED_OPTION) return;
        String currency = currencies[curIdx[0]];

        // --- Amount ---
        final String[] amtStr = {null};
        try {
            SwingUtilities.invokeAndWait(() ->
                amtStr[0] = JOptionPane.showInputDialog(
                    this,
                    "Enter transfer amount in " + currency + ":",
                    "Transfer — Amount",
                    JOptionPane.QUESTION_MESSAGE)
            );
        } catch (Exception e) { return; }

        if (amtStr[0] == null) return;

        double amount;
        try { amount = Double.parseDouble(amtStr[0].trim()); }
        catch (NumberFormatException e) {
            guiScreen.displayMessageLine("  ✖  Invalid amount."); return;
        }

        double amountHKD = currency.equals("HKD") ? amount : amount * 1.13;

        // --- Balance check ---
        double available = bankDatabase.getAvailableBalance(currentAccountNumber);
        if (amountHKD > available) {
            guiScreen.displayMessageLine("  ✖  Insufficient funds.");
            guiScreen.displayMessage("     Available: ");
            guiScreen.displayDollarAmount(available);
            guiScreen.displayMessageLine("");
            return;
        }

        // --- Confirmation ---
        final int[] confirm = {JOptionPane.NO_OPTION};
        String sym = currency.equals("HKD") ? "HK$" : "RMB ";
        String confirmMsg = String.format(
            "Transfer %s%.2f to account %d?\n\nAccount debited: HK$%.2f",
            sym, amount, targetAcc, amountHKD);
        try {
            SwingUtilities.invokeAndWait(() ->
                confirm[0] = JOptionPane.showConfirmDialog(
                    this, confirmMsg,
                    "Confirm Transfer", JOptionPane.YES_NO_OPTION)
            );
        } catch (Exception e) { return; }

        if (confirm[0] != JOptionPane.YES_OPTION) {
            guiScreen.displayMessageLine("  Transfer cancelled."); return;
        }

        // --- Execute transfer ---
        bankDatabase.debit(currentAccountNumber, amountHKD);
        bankDatabase.credit(targetAcc, amountHKD);

        guiScreen.displayMessageLine("");
        guiScreen.displayMessageLine("  ✔  Transfer successful!");
        guiScreen.displayMessage("     Amount:      " + sym);
        guiScreen.displayMessageLine(String.format("%.2f", amount));
        guiScreen.displayMessageLine("     To account: " + targetAcc);
        guiScreen.displayMessage("     New balance: ");
        guiScreen.displayDollarAmount(
            bankDatabase.getAvailableBalance(currentAccountNumber));
        guiScreen.displayMessageLine("");
        guiScreen.displayMessageLine("  Press ENTER to return to menu.");
        guiKeypad.getInput(); // wait for acknowledgement
    }

    // =========================================================
    // DISPLAY HELPERS  (safe to call from any thread)
    // =========================================================

    /** Clear the display area (runs on EDT). */
    private void clearDisplay() {
        SwingUtilities.invokeLater(() -> displayArea.setText(""));
    }

    /** Append a line to the display area (runs on EDT). */
    private void appendDisplay(String line) {
        SwingUtilities.invokeLater(() -> displayArea.append(line + "\n"));
    }

    /** Update the status bar label (runs on EDT). */
    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(" " + msg));
    }

    /** Enable or disable all keypad buttons. */
    private void setKeypadEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            for (JButton b : numButtons) if (b != null) b.setEnabled(enabled);
            enterBtn.setEnabled(enabled);
            clearBtn.setEnabled(enabled);
            cancelBtn.setEnabled(enabled);
        });
    }

    /** Pause the current thread without checked exception noise. */
    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // =========================================================
    // ENTRY POINT
    // =========================================================

    /**
     * Launch the ATM GUI on the Event Dispatch Thread.
     * The existing ATMCaseStudy.main() can also call this directly.
     */
    public static void main(String[] args) {
        // Apply system look-and-feel for native OS feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { /* fall back to Metal L&F */ }

        // All Swing objects must be created on the EDT
        SwingUtilities.invokeLater(() -> new ATMGUI());
    }
}
