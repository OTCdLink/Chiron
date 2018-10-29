package com.otcdlink.chiron.upend.session.implementation;

import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.upend.session.SessionIdentifierGenerator;

import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Uses pseudo-random and Taboo to avoid any chance of collision.
 * (Chances of collision are basically quite low so we are completely paranoid here.)
 */
public class DefaultSessionIdentifierGenerator
    extends GeneratorWithTaboo< SessionIdentifier >
    implements SessionIdentifierGenerator
{

  public DefaultSessionIdentifierGenerator() {
    this( 1000 ) ;
  }

  public DefaultSessionIdentifierGenerator( final int tabooSize ) {
    this( 16, new SecureRandom(), tabooSize ) ;
  }

  public DefaultSessionIdentifierGenerator(
      final int byteCount,
      final Random random,
      final int tabooSize
  ) {
    super(
        tabooSize,
        valueGenerator( byteCount, random ),
//        monotonicValueGenerator(),
        ( s1, s2 ) -> s1.asString().equals( s2.asString() )
    ) ;
  }

  private static final AtomicLong counter = new AtomicLong( 0 ) ;
  private static Supplier< SessionIdentifier > monotonicValueGenerator() {
    return () -> new SessionIdentifier( Long.toString( counter.getAndIncrement() ) ) ;
  }

  private static Supplier< SessionIdentifier > valueGenerator(
      final int byteCount,
      final Random random
  ) {
    checkArgument( byteCount > 0 ) ;
    checkNotNull( random ) ;
    return () -> {
      final byte[] bytes = new byte[ byteCount ] ;
      random.nextBytes( bytes ) ;
      final StringBuilder stringBuilder = new StringBuilder( byteCount * 2 ) ;
      for( final byte b : bytes ) {
        stringBuilder.append( String.format( "%02X", b ) ) ;
      }
      return new SessionIdentifier( stringBuilder.toString() ) ;
    } ;
  }

}
