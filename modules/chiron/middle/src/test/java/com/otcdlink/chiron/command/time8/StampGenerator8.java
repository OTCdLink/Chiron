package com.otcdlink.chiron.command.time8;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This is the prototype of an alternative to {@link com.otcdlink.chiron.command.Stamp.Generator}
 * based on {@code java.time.Instant}.
 */
public final class StampGenerator8 {

  private final Clock clock ;

  /**
   * https://stackoverflow.com/a/38658066/1923328
   */
  private final long nanosecondsAtStart ;

  private final AtomicReference< Instant > lastGenerated = new AtomicReference<>() ;
  private final LongSupplier nanosecondSupplier ;

  public StampGenerator8( final Clock clock ) {
    this( clock, System::nanoTime ) ;
  }

  public StampGenerator8(
      final Clock clock,
      LongSupplier nanosecondSupplier
  ) {
    this.clock = checkNotNull( clock ) ;
    this.nanosecondsAtStart = nanosecondSupplier.getAsLong() ;
    this.nanosecondSupplier = nanosecondSupplier ;
  }

  /**
   * Returns an {@code Instant} guaranteed to be strictly "greater" (later) than the one previously
   * returned, if any. The {@link Instant#getNano()} value may not reflect the time elapsed since
   * last call to this method; it should be considered as a discriminator against previously
   * returned value.
   */
  public Instant newInstant() {
    final Instant stamp = lastGenerated.updateAndGet(
        last -> {
          while( true ) {
            final long nanoseconds = nanosecondSupplier.getAsLong() - nanosecondsAtStart ;
            final Instant now = Instant.now( clock ).plusNanos( nanoseconds ) ;
            if( last == null ) {
              return now ;
            }
            if( now.compareTo( last ) > 0 ) {
              return now ;
            }
          }
        }
    ) ;
    return stamp ;
  }
}
