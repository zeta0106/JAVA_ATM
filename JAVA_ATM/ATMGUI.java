// ATMGUI.java
// Main GUI class for the ATM system, extends JFrame
// Replaces the console-based ATM class and Screen/Keypad classes

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ATMGUI extends JFrame {
    private BankDatabase bankDatabase;
    private CashDispenser cashDispenser;
    private boolean userAuthenticated;
    private int currentAccountNumber;

    // GUI components
    private JTextArea displayArea;
    private JTextField inputField;
    private JPasswordField pinField;
    private JButton[] numButtons;
    private JButton enterButton, clearButton, cancelButton;
    private StringBuilder inputBuffer;

    // Constants for transaction types
    private static final int BALANCE_INQUIRY = 1;
    private static final int WITHDRAWAL = 2;
    private static final int TRANSFER = 3;
    private static final int EXIT = 4;

    // Current transaction state
    private int currentState;
    private static final int STATE_WELCOME = 0;
    private static final int STATE_ENTER_ACCOUNT = 1;
    private static final int STATE_ENTER_PIN = 2;
    private static final int STATE_MAIN_MENU = 3;
    private static final int STATE_WAITING_CARD_REMOVAL = 5;

    public ATMGUI() {
        super("ATM Machine");
        bankDatabase = new BankDatabase();
        cashDispenser = new CashDispenser();
        userAuthenticated = false;
        currentAccountNumber = 0;
        inputBuffer = new StringBuilder();

        initializeGUI();
        setAtmState(STATE_WELCOME);
    }

    private void initializeGUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);
        setResizable(false);

        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Display area (simulates ATM screen)
        displayArea = new JTextArea(10, 40);
        displayArea.setEditable(false);
        displayArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        displayArea.setBackground(Color.BLACK);
        displayArea.setForeground(Color.GREEN);
        JScrollPane scrollPane = new JScrollPane(displayArea);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Input panel (keypad + fields)
        JPanel inputPanel = createInputPanel();
        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setVisible(true);
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Text input field (for account number, amounts)
        inputField = new JTextField(15);
        inputField.setFont(new Font("Arial", Font.PLAIN, 16));
        inputField.setHorizontalAlignment(JTextField.CENTER);
        inputField.setEditable(false);

        // PIN field (masked)
        pinField = new JPasswordField(15);
        pinField.setFont(new Font("Arial", Font.PLAIN, 16));
        pinField.setHorizontalAlignment(JTextField.CENTER);
        pinField.setEchoChar('*');

        JPanel fieldPanel = new JPanel(new GridLayout(2, 1));
        fieldPanel.add(inputField);
        fieldPanel.add(pinField);
        panel.add(fieldPanel, BorderLayout.NORTH);

        // Keypad
        JPanel keypadPanel = new JPanel(new GridLayout(4, 3, 5, 5));
        numButtons = new JButton[10];
        for (int i = 1; i <= 9; i++) {
            numButtons[i] = new JButton(String.valueOf(i));
            numButtons[i].setFont(new Font("Arial", Font.BOLD, 18));
            keypadPanel.add(numButtons[i]);
        }
        numButtons[0] = new JButton("0");
        numButtons[0].setFont(new Font("Arial", Font.BOLD, 18));
        keypadPanel.add(new JLabel(""));
        keypadPanel.add(numButtons[0]);
        keypadPanel.add(new JLabel(""));

        // Action buttons
        JPanel actionPanel = new JPanel(new GridLayout(1, 3, 5, 5));
        enterButton = new JButton("Enter");
        clearButton = new JButton("Clear");
        cancelButton = new JButton("Cancel");
        actionPanel.add(enterButton);
        actionPanel.add(clearButton);
        actionPanel.add(cancelButton);

        panel.add(keypadPanel, BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);

        // Register listeners
        for (int i = 0; i <= 9; i++) {
            final int digit = i;
            numButtons[i].addActionListener(e -> {
                inputBuffer.append(String.valueOf(digit));
                updateInputDisplay();
            });
        }
        enterButton.addActionListener(e -> processEnter());
        clearButton.addActionListener(e -> {
            inputBuffer.setLength(0);
            updateInputDisplay();
        });
        cancelButton.addActionListener(e -> processCancel());

        return panel;
    }

    private void processEnter() {
        String input = inputBuffer.toString().trim();
        inputBuffer.setLength(0);
        updateInputDisplay();

        if (currentState == STATE_ENTER_ACCOUNT) {
            try {
                int acc = Integer.parseInt(input);
                currentAccountNumber = acc;
                setAtmState(STATE_ENTER_PIN);
            } catch (NumberFormatException e) {
                displayMessage("Invalid account number. Try again.");
                setAtmState(STATE_ENTER_ACCOUNT);
            }
        } else if (currentState == STATE_ENTER_PIN) {
            String pinStr = new String(pinField.getPassword());
            pinField.setText("");
            try {
                int pin = Integer.parseInt(pinStr);
                userAuthenticated = bankDatabase.authenticateUser(currentAccountNumber, pin);
                if (userAuthenticated) {
                    displayMessage("Authentication successful. Welcome!");
                    setAtmState(STATE_MAIN_MENU);
                } else {
                    displayMessage("Invalid PIN. Please try again.");
                    currentAccountNumber = 0;
                    setAtmState(STATE_ENTER_ACCOUNT);
                }
            } catch (NumberFormatException e) {
                displayMessage("Invalid PIN format.");
                setAtmState(STATE_ENTER_ACCOUNT);
            }
        } else if (currentState == STATE_MAIN_MENU) {
            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= 4) {
                    handleMainMenuChoice(choice);
                } else {
                    displayMessage("Invalid selection. Choose 1-4.");
                }
            } catch (NumberFormatException e) {
                displayMessage("Invalid input.");
            }
        }
    }

    private void processCancel() {
        inputBuffer.setLength(0);
        updateInputDisplay();
        pinField.setText("");
        if (currentState == STATE_MAIN_MENU || currentState == STATE_ENTER_ACCOUNT || currentState == STATE_ENTER_PIN) {
            displayMessage("Transaction cancelled. Thank you for using the ATM.");
            simulateEjectCard();
        } else {
            setAtmState(STATE_WELCOME);
        }
    }

    private void handleMainMenuChoice(int choice) {
        switch (choice) {
            case BALANCE_INQUIRY:
                performBalanceInquiry();
                break;
            case WITHDRAWAL:
                performWithdrawal();
                break;
            case TRANSFER:
                performTransfer();
                break;
            case EXIT:
                displayMessage("Thank you for using the ATM. Goodbye!");
                simulateEjectCard();
                break;
        }
    }

    private void performBalanceInquiry() {
        double available = bankDatabase.getAvailableBalance(currentAccountNumber);
        double total = bankDatabase.getTotalBalance(currentAccountNumber);
        displayMessage("Balance Information:\nAvailable: HK$" + String.format("%.2f", available) +
                       "\nTotal: HK$" + String.format("%.2f", total) +
                       "\n\nPress Enter to return to main menu.");
        // Wait for Enter press to go back
        currentState = STATE_MAIN_MENU; // Actually we need a temporary state, but for simplicity:
        // Better: set a flag that next Enter returns to menu
    }

    private void performWithdrawal() {
        displayMessage("Select withdrawal currency:\n1 - HKD\n2 - RMB\nPress Cancel to abort.");
        // Use option dialog
        String[] options = {"HKD", "RMB"};
        int currencyChoice = JOptionPane.showOptionDialog(this, "Choose currency", "Withdrawal",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (currencyChoice == JOptionPane.CLOSED_OPTION) {
            setAtmState(STATE_MAIN_MENU);
            return;
        }
        String currency = (currencyChoice == 0) ? "HKD" : "RMB";
        int[] amounts = currency.equals("HKD") ? new int[]{100, 500, 1000, 2000, 5000} : new int[]{100, 200, 500, 1000, 2000};
        String[] amountStrs = new String[amounts.length];
        for (int i = 0; i < amounts.length; i++) {
            amountStrs[i] = currency + " " + amounts[i];
        }
        String selected = (String) JOptionPane.showInputDialog(this, "Select amount", "Withdrawal",
                JOptionPane.QUESTION_MESSAGE, null, amountStrs, amountStrs[0]);
        if (selected == null) {
            setAtmState(STATE_MAIN_MENU);
            return;
        }
        int amount = Integer.parseInt(selected.split(" ")[1]);
        double withdrawalInHKD = currency.equals("HKD") ? amount : amount * 1.13;
        double available = bankDatabase.getAvailableBalance(currentAccountNumber);
        if (withdrawalInHKD > available) {
            displayMessage("Insufficient funds.");
            setAtmState(STATE_MAIN_MENU);
            return;
        }
        if (!cashDispenser.isSufficientCashAvailable(amount)) {
            displayMessage("ATM has insufficient cash. Choose smaller amount.");
            setAtmState(STATE_MAIN_MENU);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Confirm withdrawal of " + selected + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            setAtmState(STATE_MAIN_MENU);
            return;
        }
        bankDatabase.debit(currentAccountNumber, withdrawalInHKD);
        cashDispenser.dispenseCash(amount);
        displayMessage("Please take your card.\n(Simulated: Card ejected)");
        // Simulate card removal then cash
        Timer timer = new Timer(2000, e -> {
            displayMessage("Please take your cash: " + selected);
            Timer timer2 = new Timer(2000, e2 -> {
                displayMessage("Cash dispensed. Thank you!\n\nPress Enter to return to main menu.");
                currentState = STATE_MAIN_MENU;
            });
            timer2.setRepeats(false);
            timer2.start();
        });
        timer.setRepeats(false);
        timer.start();
        currentState = STATE_WAITING_CARD_REMOVAL;
    }

    private void performTransfer() {
        String targetAccStr = JOptionPane.showInputDialog(this, "Enter target account number:");
        if (targetAccStr == null) { setAtmState(STATE_MAIN_MENU); return; }
        int targetAcc;
        try { targetAcc = Integer.parseInt(targetAccStr); } catch (NumberFormatException e) {
            displayMessage("Invalid account number.");
            setAtmState(STATE_MAIN_MENU); return;
        }
        if (!bankDatabase.isAccountExist(targetAcc)) {
            displayMessage("Target account does not exist.");
            setAtmState(STATE_MAIN_MENU); return;
        }
        if (targetAcc == currentAccountNumber) {
            displayMessage("Cannot transfer to your own account.");
            setAtmState(STATE_MAIN_MENU); return;
        }

        String[] currencies = {"HKD", "RMB"};
        int curChoice = JOptionPane.showOptionDialog(this, "Select currency", "Transfer",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, currencies, currencies[0]);
        if (curChoice == JOptionPane.CLOSED_OPTION) { setAtmState(STATE_MAIN_MENU); return; }
        String currency = (curChoice == 0) ? "HKD" : "RMB";

        String amtStr = JOptionPane.showInputDialog(this, "Enter amount in " + currency + ":");
        if (amtStr == null) { setAtmState(STATE_MAIN_MENU); return; }
        double amount;
        try { amount = Double.parseDouble(amtStr); } catch (NumberFormatException e) {
            displayMessage("Invalid amount.");
            setAtmState(STATE_MAIN_MENU); return;
        }
        double transferInHKD = currency.equals("HKD") ? amount : amount * 1.13;
        double available = bankDatabase.getAvailableBalance(currentAccountNumber);
        if (transferInHKD > available) {
            displayMessage("Insufficient funds.");
            setAtmState(STATE_MAIN_MENU); return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Transfer " + currency + " " + amount + " to account " + targetAcc + "?",
                "Confirm Transfer", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) { setAtmState(STATE_MAIN_MENU); return; }

        bankDatabase.debit(currentAccountNumber, transferInHKD);
        bankDatabase.credit(targetAcc, transferInHKD);
        displayMessage("Transfer successful!\nAmount: " + currency + " " + amount +
                       "\nTo account: " + targetAcc +
                       "\n\nPress Enter to return to main menu.");
        currentState = STATE_MAIN_MENU;
    }

    private void simulateEjectCard() {
        displayMessage("Please take your card.");
        Timer timer = new Timer(2000, e -> {
            displayMessage("Card ejected. Thank you for using the ATM.");
            userAuthenticated = false;
            currentAccountNumber = 0;
            setAtmState(STATE_WELCOME);
        });
        timer.setRepeats(false);
        timer.start();
    }

    // Renamed from setState to avoid conflict with Frame.setState
    private void setAtmState(int state) {
        currentState = state;
        inputBuffer.setLength(0);
        updateInputDisplay();
        pinField.setText("");
        pinField.setVisible(state == STATE_ENTER_PIN);
        inputField.setVisible(state != STATE_ENTER_PIN);

        switch (state) {
            case STATE_WELCOME:
                displayMessage("Welcome!\nPlease enter your account number:");
                currentState = STATE_ENTER_ACCOUNT;
                break;
            case STATE_ENTER_ACCOUNT:
                displayMessage("Enter account number:");
                break;
            case STATE_ENTER_PIN:
                displayMessage("Enter PIN:");
                break;
            case STATE_MAIN_MENU:
                displayMessage("Main Menu:\n1 - View Balance\n2 - Withdraw Cash\n3 - Transfer Funds\n4 - Exit");
                break;
            default:
                break;
        }
    }

    private void displayMessage(String msg) {
        displayArea.setText(msg);
    }

    private void updateInputDisplay() {
        inputField.setText(inputBuffer.toString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ATMGUI());
    }
}