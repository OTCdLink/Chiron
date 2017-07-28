package io.github.otcdlink.chiron.middle.throttler;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.middle.tier.CommandInterceptor;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.clock.Clock;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Applies Throttling to one single User session.
 * Able to run on both Upend and Downend, so we can limit traffic preventively, but Upend doesn't
 * trust Downend blindly.
 * <p>
 * A {@link COMMAND} may or may not be {@link Restriction}-aware.
 * If it is, {@link #evaluateAndUpdate(DateTime, Object)} checks if there are some
 * {@link Restriction} aware of it.
 * If there are such {@link Restriction}s, it evaluates {@link COMMAND}
 * validity as long as associated timestamp doesn't make it older than {@link #throttlingDuration}.
 * If no {@link Restriction} applied, the {@link COMMAND} is not throttled,
 * and it creates a new one that will apply next time.
 * <p>
 * TODO: remove locking as soon as we run inside a {@link CommandInterceptor}.
 */
public class SessionScopedThrottler<
    COMMAND,
    RESTRICTION extends SessionScopedThrottler.Restriction< COMMAND >
> {
  private static final Logger LOGGER = LoggerFactory.getLogger( SessionScopedThrottler.class ) ;


  private final Clock clock ;

  private final RestrictionFactory< COMMAND, RESTRICTION > restrictionFactory ;
  private final Map< RESTRICTION, DateTime > restrictions = new HashMap<>() ;
  private Duration throttlingDuration ;

  public SessionScopedThrottler(
      final Clock clock,
      final RestrictionFactory< COMMAND, RESTRICTION > restrictionFactory,
      final Duration initialThrottlingDuration
  ) {
    this.clock = checkNotNull( clock ) ;
    this.restrictionFactory = checkNotNull( restrictionFactory ) ;
    this.throttlingDuration = checkNotNull( initialThrottlingDuration ) ;
  }

  public void throttlingDuration( final Duration throttlingDuration ) {
    checkArgument( throttlingDuration.getMillis() >= 0 ) ;
    this.throttlingDuration = checkNotNull( throttlingDuration ) ;
    LOGGER.info( "Throttling duration set to " + throttlingDuration + " for " + this + "." ) ;
  }

  public enum Throttling {
    /**
     * Given {@link Command} doesn't support throttling.
     */
    NOT_APPLICABLE,

    /**
     * Given {@link Command} supports throttling and was throttled.
     */
    THROTTLED,

    /**
     * Given {@link Command} supports throttling and was not throttled.
     */
    PASSED,
    ;
  }

  /**
   * Evaluate a {@link COMMAND} againts existing {@link #restrictions}, adding some
   * {@link RESTRICTION} if needed to.
   */
  public final Throttling evaluateAndUpdate( final COMMAND command ) {
//    if( Duration.ZERO.equals( throttlingDuration ) ) {
//      return Throttling.PASSED ;
//    } else {
      return evaluateAndUpdate( clock.getCurrentDateTime(), command ) ;
//    }
  }

  protected final Duration throttlingDuration() {
    return throttlingDuration ;
  }

  private Throttling evaluateAndUpdate( final DateTime now, final COMMAND command ) {
    cleanup( now ) ;
    Throttling throttling = Throttling.NOT_APPLICABLE ;
    if( restrictionFactory.supports( command ) ) {
      throttling = Throttling.PASSED ;
      if( ! Duration.ZERO.equals( throttlingDuration ) ) {
        for( final Map.Entry< RESTRICTION, DateTime > entry : restrictions.entrySet() ) {
          if( entry.getKey().appliesTo( command ) ) {
            throttling = Throttling.THROTTLED ;
            break ;
          }
        }
      }
      if( throttling == Throttling.PASSED ) {
        final RESTRICTION restriction = restrictionFactory.createFrom( command ) ;
        restrictions.put( restriction, now ) ;
        LOGGER.debug( "Added " + restriction + " to " + this + "." ) ;
        restrictionAdded( restriction ) ;
      }
    }
    return throttling ;
  }

  protected void restrictionAdded( final RESTRICTION restriction ) { }

  protected final void cleanup() {
    cleanup( clock.getCurrentDateTime() ) ;
  }

  private void cleanup( final DateTime now ) {
    if( ! restrictions.isEmpty() ) {
      final DateTime latestValidCreationTime = now.minus( throttlingDuration ) ;
      final Iterator< Map.Entry< RESTRICTION, DateTime > > iterator =
          restrictions.entrySet().iterator() ;
      while( iterator.hasNext() ) {
        final Map.Entry< RESTRICTION, DateTime > entry = iterator.next() ;
        if( latestValidCreationTime.isAfter( entry.getValue() ) ) {
          iterator.remove() ;
          LOGGER.debug( "Removed " + entry.getKey() + " from " + this + "." ) ;
          restrictionRemoved( entry.getKey() ) ;
        }
      }
    }
  }

  protected void restrictionRemoved( final RESTRICTION restriction ) { }

  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + "{}" ;
  }

  protected final void visitAllRestrictions( final Function< RESTRICTION, Boolean > visitor ) {
    checkNotNull( visitor ) ;
    for( final RESTRICTION restriction : restrictions.keySet() ) {
      if( ! visitor.apply( restriction ) ) {
        break ;
      }
    }
  }

  public interface RestrictionFactory< COMMAND, RESTRICTION extends Restriction< COMMAND > > {
    boolean supports( COMMAND command ) ;
    RESTRICTION createFrom( COMMAND command ) ;
  }

  public interface Restriction< COMMAND > {
    boolean appliesTo( COMMAND command ) ;
  }

}
