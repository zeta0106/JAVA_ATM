// Withdrawal.java
// Represents a withdrawal ATM transaction

public class Withdrawal extends Transaction
{
   private int amount; // amount to withdraw
   private Keypad keypad; // reference to keypad
   private CashDispenser cashDispenser; // reference to cash dispenser

   // constant corresponding to menu option to cancel
   private final static int CANCELED = 7;
   private final static int OTHER_AMOUNT = 6;

   // Withdrawal constructor
   public Withdrawal( int userAccountNumber, Screen atmScreen, 
      BankDatabase atmBankDatabase, Keypad atmKeypad, 
      CashDispenser atmCashDispenser )
   {
      // initialize superclass variables
      super( userAccountNumber, atmScreen, atmBankDatabase );
      
      // initialize references to keypad and cash dispenser
      keypad = atmKeypad;
      cashDispenser = atmCashDispenser;
   } // end Withdrawal constructor

   // perform transaction
   public void execute()
   {
      boolean cashDispensed = false; // cash was not dispensed yet
      double availableBalance; // amount available for withdrawal

      // get references to bank database and screen
      BankDatabase bankDatabase = getBankDatabase(); 
      Screen screen = getScreen();

      // prompt for currency (HKD or RMB)
      String currency = promptForCurrency();
      if (currency.equals("CANCELED"))
      {
         screen.displayMessageLine( "\nCanceling transaction..." );
         return;
      }

      // loop until cash is dispensed or the user cancels
      do
      {
         // obtain a chosen withdrawal amount from the user 
         amount = displayMenuOfAmounts(currency);
         
         // check whether user chose a withdrawal amount or canceled
         if ( amount != CANCELED )
         {
            // convert to HKD for balance check and debit (using given exchange rate)
            double withdrawalInHKD = currency.equals("HKD") ? amount : amount * 1.13;
      
            // get available balance of account involved
            availableBalance = 
               bankDatabase.getAvailableBalance( getAccountNumber() );
      
            // check whether the user has enough money in the account 
            if ( withdrawalInHKD <= availableBalance )
            {   
               // check whether the cash dispenser has enough money
               // (amount passed is in currency units; dispenser uses 100-unit bills)
               if ( cashDispenser.isSufficientCashAvailable( amount ) )
               {
                  // update the account involved to reflect withdrawal (in HKD)
                  bankDatabase.debit( getAccountNumber(), withdrawalInHKD );
                  
                  cashDispenser.dispenseCash( amount ); // dispense cash
                  cashDispensed = true; // cash was dispensed

                  // instruct user to take cash and show amount
                  screen.displayMessageLine( 
                     "\nPlease take your cash now." );
                  screen.displayMessage( "Withdrawn: " );
                  if (currency.equals("HKD"))
                     screen.displayDollarAmount( amount );
                  else
                     screen.displayRMBAmount( amount );
                  screen.displayMessageLine( "" );
               } // end if
               else // cash dispenser does not have enough cash
                  screen.displayMessageLine( 
                     "\nInsufficient cash available in the ATM." +
                     "\n\nPlease choose a smaller amount." );
            } // end if
            else // not enough money available in user's account
            {
               screen.displayMessageLine( 
                  "\nInsufficient funds in your account." +
                  "\n\nPlease choose a smaller amount." );
            } // end else
         } // end if
         else // user chose cancel menu option 
         {
            screen.displayMessageLine( "\nCanceling transaction..." );
            return; // return to main menu because user canceled
         } // end else
      } while ( !cashDispensed );

   } // end method execute

   // prompt for withdrawal currency
   private String promptForCurrency()
   {
      Screen screen = getScreen();
      while (true)
      {
         screen.displayMessageLine( "\nWithdrawal Currency:" );
         screen.displayMessageLine( "1 - HKD" );
         screen.displayMessageLine( "2 - RMB" );
         screen.displayMessageLine( "7 - Cancel transaction" );
         screen.displayMessage( "\nChoose a currency: " );

         int input = keypad.getInput();
         switch (input)
         {
            case 1: return "HKD";
            case 2: return "RMB";
            case CANCELED: return "CANCELED";
            default:
               screen.displayMessageLine( "\nInvalid selection. Try again." );
         }
      }
   }

   // display a menu of withdrawal amounts (currency-specific) and the option to cancel;
   // return the chosen amount or CANCELED if the user chooses to cancel
   private int displayMenuOfAmounts(String currency)
   {
      int userChoice = 0; // local variable to store return value

      Screen screen = getScreen(); // get screen reference
      
      // array of amounts to correspond to menu numbers
      int amounts[];
      if (currency.equals("HKD"))
         amounts = new int[] { 0, 100, 200, 400, 800, 1000 };
      else // RMB
         amounts = new int[] { 0, 100, 200, 400, 800, 1000 };

      String symbol = currency.equals("HKD") ? "HK$" : "RMB ";

      // loop while no valid choice has been made
      while ( userChoice == 0 )
      {
         // display the menu
         screen.displayMessageLine( "\nWithdrawal Menu for " + currency + ":" );
         screen.displayMessageLine( "1 - " + symbol + amounts[1] );
         screen.displayMessageLine( "2 - " + symbol + amounts[2] );
         screen.displayMessageLine( "3 - " + symbol + amounts[3] );
         screen.displayMessageLine( "4 - " + symbol + amounts[4] );
         screen.displayMessageLine( "5 - " + symbol + amounts[5] );
         screen.displayMessageLine( "6 - Other Amount" );
         screen.displayMessageLine( "7 - Cancel transaction" );
         screen.displayMessage( "\nChoose a withdrawal amount: " );

         int input = keypad.getInput(); // get user input through keypad

         // determine how to proceed based on the input value
         switch ( input )
         {
            case 1: // if the user chose a withdrawal amount 
            case 2: // (i.e., chose option 1, 2, 3, 4 or 5), return the
            case 3: // corresponding amount from amounts array
            case 4:
            case 5:
               userChoice = amounts[ input ]; // save user's choice
               break;
            case OTHER_AMOUNT: // user chose "Other Amount"
               userChoice = promptForCustomAmount(currency);
               break;       
            case CANCELED: // the user chose to cancel
               userChoice = CANCELED; // save user's choice
               break;
            default: // the user did not enter a value from 1-7
               screen.displayMessageLine( 
                  "\nInvalid selection. Try again." );
         } // end switch
      } // end while

      return userChoice; // return withdrawal amount or CANCELED
   } // end method displayMenuOfAmounts

   // prompt user for custom withdrawal amount (must be multiple of 100)
   private int promptForCustomAmount(String currency)
   {
      Screen screen = getScreen();
      String symbol = currency.equals("HKD") ? "HK$" : "RMB ";

      while (true)
      {
         screen.displayMessageLine( "\nEnter custom amount (must be multiple of 100):" );
         screen.displayMessage( "Enter amount (" + symbol + "): " );
         
         int input = keypad.getInput();

         // Check if amount is positive and multiple of 100
         if (input > 0 && input % 100 == 0)
         {
            return input; // return valid custom amount
         }
         else if (input == 0)
         {
            // User entered 0, treat as cancel
            screen.displayMessageLine( "\nCanceling custom amount entry..." );
            return 0; // return 0 to go back to menu
         }
         else
         {
            // Invalid amount
            screen.displayMessageLine( 
               "\nInvalid amount. Please enter a positive multiple of 100." );
         } // end else
      } // end while
   } // end method promptForCustomAmount
} // end class Withdrawal
