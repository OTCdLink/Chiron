package com.otcdlink.chiron.ssh.hello;

import com.google.common.base.Joiner;
import com.otcdlink.chiron.ssh.SshJavaDriver;

/**
 * Demonstrates {@link SshJavaDriver} usage.
 * Can't keep this class in "src/test" because we don't deploy test classes. Or should we?
 */
public final class JustHello {

  private JustHello() { }

  public static void main( final String... arguments ) {
    System.out.println( "Hello " + Joiner.on( ' ' ).join( arguments ) ) ;
//    System.out.println( "Now sleeping for a long ..." ) ;
//    Uninterruptibles.sleepUninterruptibly( 1, TimeUnit.DAYS ) ;

    System.exit( 1 ) ;
  }
}
