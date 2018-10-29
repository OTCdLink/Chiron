package com.otcdlink.chiron.toolbox;

import com.google.common.base.Preconditions;

public class NumberTools {

  /**
   * https://www.baeldung.com/java-number-of-digits-in-int
   */
  public static int numberOfDigits( int number ) {
    Preconditions.checkArgument( number <= 1000000000, "Unsupported: " + number ) ;
    if( number < 0 ) {
      number = - number ;
    }
    if( number < 100000 ) {
      if( number < 100 ) {
        if( number < 10 ) {
          return 1 ;
        } else {
          return 2 ;
        }
      } else {
        if( number < 1000 ) {
          return 3 ;
        } else {
          if( number < 10000 ) {
            return 4 ;
          } else {
            return 5 ;
          }
        }
      }
    } else {
      if( number < 10000000 ) {
        if( number < 1000000 ) {
          return 6 ;
        } else {
          return 7 ;
        }
      } else {
        if( number < 100000000 ) {
          return 8 ;
        } else {
          if( number < 1000000000 ) {
            return 9 ;
          } else {
            return 10 ;
          }
        }
      }
    }
  }
}
