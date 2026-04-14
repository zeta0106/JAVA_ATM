public class Screen
{
   // displays a message without a carriage return
   public void displayMessage( String message ) 
   {
      System.out.print( message ); 
   } // end method displayMessage

   // display a message with a carriage return
   public void displayMessageLine( String message ) 
   {
      System.out.println( message );   
   } // end method displayMessageLine

   // display a dollar amount (now adapted for HKD)
   public void displayDollarAmount( double amount )
   {
      System.out.printf( "HK$%,.2f", amount );   
   } // end method displayDollarAmount 

   // display an RMB amount
   public void displayRMBAmount( double amount )
   {
      System.out.printf( "RMB %,.2f", amount );   
   } // end method displayRMBAmount 
} 