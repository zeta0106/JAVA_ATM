import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Scanner;

public class Transfer extends Transaction
{
   private double transferAmount;
   private int targetAccountNumber;
   private Keypad keypad;
   private final static int CANCELED = 0;

   // ====== Exchange Rate (No Fee) ======
   private final static double RMB_TO_HKD_RATE = 1.13;

   // ====== High-Value Threshold for PIN Re-verification ======
   private final static double HIGH_VALUE_THRESHOLD_HKD = 500.0;

   // ====== Suspicious Activity Detection ======
   private final static int SUSPICIOUS_TRANSFER_THRESHOLD = 3;

   // ====== Daily Tracking & Transaction History ======
   private static double dailyTransferTotal = 0.0;
   private static ArrayList<TransferRecord> transferHistory = new ArrayList<>();

   // ====== PIN Re-verification ======
   private final static int MAX_PIN_ATTEMPTS = 3;

   public Transfer( int userAccountNumber, Screen atmScreen,
      BankDatabase atmBankDatabase, Keypad atmKeypad )
   {
      super( userAccountNumber, atmScreen, atmBankDatabase );
      keypad = atmKeypad;
   }

   @Override
   public void execute()
   {
      BankDatabase bankDatabase = getBankDatabase();
      Screen screen = getScreen();

      // --- Step 1: Get target account ---
      targetAccountNumber = promptForTargetAccount();
      if ( targetAccountNumber == CANCELED )
      {
         screen.displayMessageLine( "\nCanceling transaction..." );
         return;
      }

      // --- Step 2: Validate target account ---
      if ( !bankDatabase.isAccountExist( targetAccountNumber ) )
      {
         screen.displayMessageLine( "\nError: Target account does not exist." );
         logSecurityEvent( "Transfer to non-existent account: " + targetAccountNumber );
         return;
      }

      if ( targetAccountNumber == getAccountNumber() )
      {
         screen.displayMessageLine( "\nError: Cannot transfer to your own account." );
         return;
      }

      // --- Step 3: Suspicious activity check ---
      if ( detectSuspiciousActivity( targetAccountNumber ) )
      {
         screen.displayMessageLine(
            "\n[Security Alert] Multiple transfers to the same account detected." );
         screen.displayMessageLine(
            "For your protection, please confirm you wish to proceed." );
         screen.displayMessageLine( "Press 1 to continue, 0 to cancel." );
         int securityConfirm = keypad.getInput();
         if ( securityConfirm != 1 )
         {
            screen.displayMessageLine( "\nTransaction canceled for security." );
            return;
         }
      }

      // --- Step 4: Currency selection ---
      String currency = promptForCurrency();
      if ( currency.equals( "CANCELED" ) )
      {
         screen.displayMessageLine( "\nCanceling transaction..." );
         return;
      }

      // --- Step 5: Get transfer amount (NOW SUPPORTS DECIMAL VALUES) ---
      transferAmount = promptForTransferAmount();
      if ( transferAmount == CANCELED )
      {
         screen.displayMessageLine( "\nCanceling transaction..." );
         return;
      }

      // --- Step 6: Currency conversion ---
      double transferInHKD;
      if ( currency.equals( "HKD" ) )
      {
         transferInHKD = transferAmount;
      }
      else
      {
         transferInHKD = transferAmount * RMB_TO_HKD_RATE;
         screen.displayMessageLine( "\n--- Currency Conversion ---" );
         screen.displayMessage( "Exchange Rate: 1 RMB = " );
         screen.displayDollarAmount( RMB_TO_HKD_RATE );
         screen.displayMessageLine( " HKD" );
         screen.displayMessage( "Amount in RMB: " );
         screen.displayDollarAmount( transferAmount );
         screen.displayMessageLine( "" );
         screen.displayMessage( "Converted Amount in HKD: " );
         screen.displayDollarAmount( transferInHKD );
         screen.displayMessageLine( "\n---------------------------" );
      }

      // --- Step 7: Check sufficient funds ---
      double totalDebit = transferInHKD;
      double availableBalance = bankDatabase.getAvailableBalance( getAccountNumber() );
      if ( totalDebit > availableBalance )
      {
         screen.displayMessageLine( "\nInsufficient funds." );
         screen.displayMessage( "Available balance: " );
         screen.displayDollarAmount( availableBalance );
         screen.displayMessageLine( "" );
         screen.displayMessage( "Transfer amount:   " );
         screen.displayDollarAmount( totalDebit );
         screen.displayMessageLine( "" );
         screen.displayMessageLine( "\nTransaction canceled." );
         return;
      }

      // --- Step 8: PIN Re-verification for high-value transfers ---
      if ( transferInHKD > HIGH_VALUE_THRESHOLD_HKD )
      {
         screen.displayMessageLine( "\n*** HIGH-VALUE TRANSFER ***" );
         screen.displayMessage( "Transfer amount " );
         screen.displayDollarAmount( transferInHKD );
         screen.displayMessage( " exceeds " );
         screen.displayDollarAmount( HIGH_VALUE_THRESHOLD_HKD );
         screen.displayMessageLine( "." );
         screen.displayMessageLine(
            "For security, please re-enter your PIN to confirm." );

         if ( !pinReVerification( bankDatabase ) )
         {
            screen.displayMessageLine(
               "\nPIN verification failed. Transfer canceled for security." );
            logSecurityEvent(
               "PIN re-verification failed for high-value transfer of HK$"
               + String.format( "%.2f", transferInHKD ) );
            return;
         }
         screen.displayMessageLine( "\nPIN verified successfully." );
      }

      // --- Step 9: Confirmation summary ---
      screen.displayMessageLine( "\n========== Transfer Summary ==========" );
      screen.displayMessageLine( "From Account: " + getAccountNumber() );
      screen.displayMessageLine( "To Account:   " + targetAccountNumber );
      if ( currency.equals( "RMB" ) )
      {
         screen.displayMessage( "Amount (RMB):     " );
         screen.displayDollarAmount( transferAmount );
         screen.displayMessageLine( "" );
         screen.displayMessage( "Exchange Rate:    1 RMB = " );
         screen.displayDollarAmount( RMB_TO_HKD_RATE );
         screen.displayMessageLine( " HKD" );
      }
      screen.displayMessage( "Transfer (HKD):   " );
      screen.displayDollarAmount( transferInHKD );
      screen.displayMessageLine( "" );
      screen.displayMessageLine( "Transfer Fee:     HK$0.00 (No Fee)" );
      screen.displayMessage( "Total Debit:      " );
      screen.displayDollarAmount( totalDebit );
      screen.displayMessageLine( "\n======================================" );
      screen.displayMessageLine( "\nPress 1 to confirm, 0 to cancel." );

      int confirmation = keypad.getInput();
      if ( confirmation != 1 )
      {
         screen.displayMessageLine( "\nTransfer canceled." );
         return;
      }

      // --- Step 10: Execute the transfer ---
      bankDatabase.debit( getAccountNumber(), totalDebit );
      bankDatabase.credit( targetAccountNumber, transferInHKD );

      // --- Step 11: Update daily total ---
      dailyTransferTotal += transferInHKD;

      // --- Step 12: Record transaction history ---
      TransferRecord record = new TransferRecord(
         getAccountNumber(), targetAccountNumber,
         transferAmount, currency, transferInHKD, 0.0,
         LocalDateTime.now()
      );
      transferHistory.add( record );

      // --- Step 13: Print receipt ---
      screen.displayMessageLine( "\n====== Transfer Successful! ======" );
      screen.displayMessageLine( "Date: " +
         LocalDateTime.now().format(
            DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ) ) );
      screen.displayMessageLine( "To Account: " + targetAccountNumber );
      if ( currency.equals( "RMB" ) )
      {
         screen.displayMessage( "Amount (RMB): " );
         screen.displayDollarAmount( transferAmount );
         screen.displayMessageLine( "" );
      }
      screen.displayMessage( "Transferred (HKD): " );
      screen.displayDollarAmount( transferInHKD );
      screen.displayMessageLine( "" );
      screen.displayMessageLine( "Fee: HK$0.00" );
      screen.displayMessage( "New Balance: " );
      screen.displayDollarAmount(
         bankDatabase.getAvailableBalance( getAccountNumber() ) );
      screen.displayMessageLine( "\n==================================" );

      // --- Step 14: Security logging ---
      logSecurityEvent( "Transfer completed: HK$"
         + String.format( "%.2f", transferInHKD )
         + " to account " + targetAccountNumber );
   }

   // ====== PIN Re-verification Method ======
   private boolean pinReVerification( BankDatabase bankDatabase )
   {
      Screen screen = getScreen();

      for ( int attempt = 1; attempt <= MAX_PIN_ATTEMPTS; attempt++ )
      {
         screen.displayMessage( "\nEnter your PIN (Attempt "
            + attempt + " of " + MAX_PIN_ATTEMPTS + "): " );
         int pinInput = keypad.getInput();

         if ( bankDatabase.authenticateUser( getAccountNumber(), pinInput ) )
         {
            return true;
         }
         else
         {
            screen.displayMessageLine( "Incorrect PIN." );
            if ( attempt < MAX_PIN_ATTEMPTS )
            {
               screen.displayMessageLine( "Please try again." );
            }
         }
      }

      return false;
   }

   // ====== Prompt for target account ======
   private int promptForTargetAccount()
   {
      Screen screen = getScreen();
      screen.displayMessageLine(
         "\nPlease enter the target account number" );
      screen.displayMessageLine( "(or 0 to cancel): " );
      return keypad.getInput();
   }

   // ====== Prompt for currency ======
   private String promptForCurrency()
   {
      Screen screen = getScreen();
      screen.displayMessageLine( "\nSelect transfer currency:" );
      screen.displayMessageLine( "1 - HKD (Hong Kong Dollar)" );
      screen.displayMessageLine( "2 - RMB (Chinese Yuan)" );
      screen.displayMessageLine( "0 - Cancel" );

      int choice = keypad.getInput();
      switch ( choice )
      {
         case 1:
            return "HKD";
         case 2:
            return "RMB";
         default:
            return "CANCELED";
      }
   }

   // ====== UPDATED: Prompt for transfer amount (NOW SUPPORTS DECIMAL VALUES) ======
   /**
    * Prompts the user to enter a transfer amount that can include decimal values.
    * 
    * CHANGES FROM ORIGINAL:
    * - Original: Only accepted integers via keypad.getInput()
    * - Updated: Uses Scanner for decimal input (e.g., 9.90, 99.99, 1000.50)
    * 
    * VALIDATION:
    * - Amount must be positive (> 0)
    * - Decimal places are allowed and preserved
    * - Entering 0 will cancel the transaction
    * - Invalid input shows error and prompts again
    * 
    * EXAMPLES OF VALID INPUT:
    * - 100 (interpreted as 100.00)
    * - 9.90 (nine dollars and ninety cents)
    * - 9.99 (nine dollars and ninety-nine cents)
    * - 1500.50 (fifteen hundred and fifty cents)
    * - 0.99 (ninety-nine cents)
    * 
    * @return double - The transfer amount with decimal precision, or 0 if canceled
    */
   private double promptForTransferAmount()
   {
      Screen screen = getScreen();
      
      screen.displayMessageLine(
         "\nPlease enter the transfer amount" );
      screen.displayMessageLine( "(Decimal amounts accepted, e.g., 9.90)" );
      screen.displayMessageLine( "(or 0 to cancel): " );

      // Use Scanner to read decimal input from System.in
      // This allows the user to input values like 9.90, 99.99, etc.
      Scanner scanner = new Scanner( System.in );
      
      while ( true )
      {
         try
         {
            // Attempt to read the next double from user input
            if ( scanner.hasNextDouble() )
            {
               double amount = scanner.nextDouble();
               
               // Cancel if user enters 0
               if ( amount == 0 )
               {
                  return CANCELED; // Return CANCELED sentinel (0)
               }
               
               // Validate: amount must be positive
               if ( amount < 0 )
               {
                  screen.displayMessageLine(
                     "\nError: Amount cannot be negative. Please try again." );
                  continue;
               }
               
               // Validate: amount must not exceed a reasonable maximum
               if ( amount > 999999.99 )
               {
                  screen.displayMessageLine(
                     "\nError: Amount exceeds maximum limit (HK$999,999.99)." );
                  screen.displayMessageLine( "Please try again." );
                  continue;
               }
               
               // Round to 2 decimal places to avoid floating-point precision issues
               // Example: 9.995 becomes 10.00, 9.994 becomes 9.99
               amount = Math.round( amount * 100.0 ) / 100.0;
               
               // Valid amount received
               screen.displayMessageLine(
                  "\nAmount entered: HK$" + String.format( "%.2f", amount ) );
               return amount;
            }
            else
            {
               // Invalid input (not a number)
               String invalid = scanner.next(); // consume the invalid input
               screen.displayMessageLine(
                  "\nError: '" + invalid
                  + "' is not a valid number. Please enter a decimal amount." );
               screen.displayMessage( "Try again: " );
            }
         }
         catch ( Exception e )
         {
            // Handle any unexpected exceptions
            screen.displayMessageLine(
               "\nError reading input. Please try again." );
            scanner.nextLine(); // Clear the input buffer
         }
      }
   }

   // ====== Suspicious Activity Detection ======
   private boolean detectSuspiciousActivity( int targetAccount )
   {
      int recentCount = 0;
      LocalDateTime oneHourAgo = LocalDateTime.now().minusHours( 1 );

      for ( TransferRecord record : transferHistory )
      {
         if ( record.getFromAccount() == getAccountNumber()
            && record.getToAccount() == targetAccount
            && record.getTimestamp().isAfter( oneHourAgo ) )
         {
            recentCount++;
         }
      }

      return recentCount >= SUSPICIOUS_TRANSFER_THRESHOLD;
   }

   // ====== Security Event Logging ======
   private void logSecurityEvent( String event )
   {
      System.out.println( "[SECURITY LOG] "
         + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ) )
         + " | Account: " + getAccountNumber() + " | " + event );
   }

   // ====== View Transfer History ======
   public static void viewTransferHistory( int accountNumber, Screen screen )
   {
      screen.displayMessageLine( "\n====== Transfer History ======" );
      boolean found = false;
      for ( TransferRecord record : transferHistory )
      {
         if ( record.getFromAccount() == accountNumber
            || record.getToAccount() == accountNumber )
         {
            screen.displayMessageLine( record.toString() );
            found = true;
         }
      }
      if ( !found )
      {
         screen.displayMessageLine( "No transfer records found." );
      }
      screen.displayMessageLine( "==============================\n" );
   }

   // ====== Reset daily limit ======
   public static void resetDailyLimit()
   {
      dailyTransferTotal = 0.0;
   }

   // ====== Inner class for Transfer Record ======
   /**
    * UPDATED: TransferRecord now stores decimal amounts with precision
    * 
    * Changes:
    * - originalAmount: Changed from int to double (supports 9.90, 99.99, etc.)
    * - All amount fields now use double for decimal precision
    * - toString() method formats amounts to 2 decimal places
    */
   private static class TransferRecord
   {
      private int fromAccount;
      private int toAccount;
      private double originalAmount;  // CHANGED: from int to double
      private String currency;
      private double amountInHKD;
      private double fee;
      private LocalDateTime timestamp;

      public TransferRecord( int from, int to, double originalAmt,
         String curr, double hkdAmt, double transferFee,
         LocalDateTime time )
      {
         fromAccount = from;
         toAccount = to;
         originalAmount = originalAmt;  // Now accepts decimal values
         currency = curr;
         amountInHKD = hkdAmt;
         fee = transferFee;
         timestamp = time;
      }

      public int getFromAccount() { return fromAccount; }
      public int getToAccount() { return toAccount; }
      public LocalDateTime getTimestamp() { return timestamp; }

      @Override
      public String toString()
      {
         String timeStr = timestamp.format(
            DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ) );
         // Format decimal amounts to 2 decimal places
         return String.format(
            "[%s] From: %d -> To: %d | %s %.2f (HK$%.2f) | Fee: HK$%.2f",
            timeStr, fromAccount, toAccount, currency,
            originalAmount, amountInHKD, fee );
      }
   }
}
