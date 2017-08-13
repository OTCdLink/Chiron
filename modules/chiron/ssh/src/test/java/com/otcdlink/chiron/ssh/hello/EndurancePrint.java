package com.otcdlink.chiron.ssh.hello;

import com.otcdlink.chiron.ssh.SshJavaDriver;

import java.util.Random;

/**
 * Demonstrates {@link SshJavaDriver} usage.
 * Can't keep this class in "src/test" because we don't deploy test classes. Or should we?
 */
public final class EndurancePrint {

  public static final char FIRST_ASCII_CHAR = '!' ;
  public static final char LAST_ASCII_CHAR = '~' ;

  private EndurancePrint() { }

  public static void main( final String... arguments ) throws InterruptedException {
    final Random random = new Random( 0 ) ;
    final int iterations = Integer.parseInt( arguments[ 0 ] ) ;
    final int delay = Integer.parseInt( arguments[ 1 ] ) ;
    for( int iteration = 0 ; iteration < iterations ; iteration ++ ) {
      final StringBuilder noiseBuilder = new StringBuilder() ;
      for( int i = 0 ; i < 1000 ; i ++ ) {
        final int character = FIRST_ASCII_CHAR +
            random.nextInt( LAST_ASCII_CHAR - FIRST_ASCII_CHAR ) ;
        noiseBuilder.append( ( char ) character ) ;
      }
      // if( iteration > 10 ) iteration = 0 ; Uncomment to check discrepancy detection.

      System.out.println(
          "Iteration " + iteration + " (Random noise follows) " + noiseBuilder.toString() ) ;

      Thread.sleep( delay ) ;
    }
    System.exit( 0 ) ;
  }
}
