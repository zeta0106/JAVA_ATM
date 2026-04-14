// CashDispenser.java
// Represents the cash dispenser of the ATM

public class CashDispenser 
{
   // the default initial number of bills in the cash dispenser
   private final static int INITIAL_COUNT = 500;
   private int count; // number of 100-unit bills remaining
   
   // no-argument CashDispenser constructor initializes count to default
   public CashDispenser()
   {
      count = INITIAL_COUNT; // set count attribute to default
   } // end CashDispenser constructor

   // simulates dispensing of specified amount of cash
   // amount is in the chosen currency units (multiple of 100)
   public void dispenseCash( int amount )
   {
      int billsRequired = amount / 100; // number of 100-unit bills required
      count -= billsRequired; // update the count of bills
   } // end method dispenseCash

   // indicates whether cash dispenser can dispense desired amount
   public boolean isSufficientCashAvailable( int amount )
   {
      int billsRequired = amount / 100; // number of 100-unit bills required

      if ( count >= billsRequired  )
         return true; // enough bills available
      else 
         return false; // not enough bills available
   } // end method isSufficientCashAvailable
} // end class CashDispenser