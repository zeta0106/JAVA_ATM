// Keypad.java
// Represents the keypad of the ATM

import java.util.Scanner;

public class Keypad
{
   private Scanner input; // reads data from the command line
                         
   // no-argument constructor initializes the Scanner
   public Keypad()
   {
      input = new Scanner( System.in );    
   } // end no-argument Keypad constructor

   // return an integer value entered by user 
   // Returns the valid integer or -1 if input is invalid / non-numeric
   public int getInput()
   {
      while (true)
      {
         String line = input.nextLine().trim();
         
         if (line.isEmpty())
         {
            System.out.println("Please enter a number.");
            continue;
         }
         
         try
         {
            return Integer.parseInt(line);
         }
         catch (NumberFormatException e)
         {
            System.out.println("Invalid input. Please enter a valid number.");
         }
      }
   } // end method getInput
} // end class Keypad