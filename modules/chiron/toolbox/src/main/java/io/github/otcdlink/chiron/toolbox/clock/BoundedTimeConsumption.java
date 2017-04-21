package io.github.otcdlink.chiron.toolbox.clock;

import io.github.otcdlink.chiron.toolbox.ToStringTools;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class BoundedTimeConsumption {

  private final Clock clock ;
  public final long durationMilliseconds ;

  /**
   * Updated only once, upon a call to {@link #start()}, so we can check state
   * in a thread-safe manner.
   */
  private final AtomicLong start = new AtomicLong( -1 ) ;

  private final AtomicLong remainder = new AtomicLong() ;

  public BoundedTimeConsumption(
      final Clock clock,
      final long durationMilliseconds
  ) {
    this.clock = checkNotNull( clock ) ;
    checkArgument( durationMilliseconds >= 0 ) ;
    this.durationMilliseconds = durationMilliseconds ;
  }

  public static BoundedTimeConsumption newStarted( final long durationMilliseconds ) {
    return new BoundedTimeConsumption( Clock.SYSTEM_CLOCK, durationMilliseconds ).start() ;
  }

  public BoundedTimeConsumption start() {
    final long now = clock.currentTimeMillis() ;
    checkState( start.getAndSet( now ) == -1, "Already started " + this ) ;
    remainder.set( durationMilliseconds ) ;
    return this ;
  }

  private void checkStarted() {
    checkState( start.get() >= 0, "Not started " + this ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + "{" +
        durationMilliseconds +
        "}"
    ;
  }

  /**
   * @return how much time remains.
   */
  public long consume() {
    checkStarted() ;
    final long now = clock.currentTimeMillis() ;
    final long elapsed = now - start.get() ;
    checkState( elapsed >= 0, "Getting back in time, now is " + now + " but start is " + start.get() ) ;

    /** We could just keep the last instant this method was called, but it would break consistency
     * of returned value because another {@link #consume} could have occured meanwhile. */
    return remainder.updateAndGet( old -> old - elapsed ) ;
  }

  /**
   * @return {@link #durationMilliseconds} minus time elapsed since call to {@link #start()}.
   */
  public long remainder() {
    checkStarted() ;
    return remainder.get() ;
  }

  public boolean timeRemains() {
    return remainder() > 0 ;
  }

  public void checkTimeRemains() throws TimeoutException {
    if( ! timeRemains() ) {
      throw new TimeoutException( "More than " + durationMilliseconds + " ms elapsed since " +
          DATE_TIME_FORMATTER.print( start.get() )
      ) ;
    }
  }

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormat.forPattern( "YYYY-MM-dd HH:mm:ss SSS" ).withZoneUTC() ;

}