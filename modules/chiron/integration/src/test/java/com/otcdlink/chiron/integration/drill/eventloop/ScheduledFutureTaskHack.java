package com.otcdlink.chiron.integration.drill.eventloop;

import com.otcdlink.chiron.toolbox.ToStringTools;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 *
 * Got the delegation working with the help of
 * https://www.infoq.com/articles/Easily-Create-Java-Agents-with-ByteBuddy
 */
final class ScheduledFutureTaskHack {

  private static final Logger LOGGER = LoggerFactory.getLogger( ScheduledFutureTaskHack.class ) ;

  private static final Class< ? > SCHEDULEDFUTURETASK_CLASS ;
  private static final Method SCHEDULEDFUTURETASK_NANOTIME_METHOD ;
  private static final Method SCHEDULEDFUTURETASK_DEADLINENANOS_METHOD ;
  private static final Field SCHEDULEDFUTURETASK_DEADLINENANOS_FIELD ;
  private static final Field SCHEDULEDFUTURETASK_STARTTIME_FIELD ;
  static {
    try {
      SCHEDULEDFUTURETASK_CLASS = Class.forName( "io.netty.util.concurrent.ScheduledFutureTask" ) ;
      SCHEDULEDFUTURETASK_NANOTIME_METHOD =
          SCHEDULEDFUTURETASK_CLASS.getDeclaredMethod( "nanoTime" ) ;
      SCHEDULEDFUTURETASK_NANOTIME_METHOD.setAccessible( true ) ;
      SCHEDULEDFUTURETASK_DEADLINENANOS_METHOD =
          SCHEDULEDFUTURETASK_CLASS.getDeclaredMethod( "deadlineNanos") ;
      SCHEDULEDFUTURETASK_DEADLINENANOS_METHOD.setAccessible( true ) ;
      SCHEDULEDFUTURETASK_DEADLINENANOS_FIELD =
          SCHEDULEDFUTURETASK_CLASS.getDeclaredField( "deadlineNanos" ) ;
      SCHEDULEDFUTURETASK_DEADLINENANOS_FIELD.setAccessible( true ) ;
      SCHEDULEDFUTURETASK_STARTTIME_FIELD =
          SCHEDULEDFUTURETASK_CLASS.getDeclaredField( "START_TIME" ) ;
      SCHEDULEDFUTURETASK_STARTTIME_FIELD.setAccessible( true ) ;
    } catch( ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e ) {
      throw new Error( e ) ;
    }
  }

  /**
   * Everything is this class must be visible from the redefined class.
   */
  @SuppressWarnings( "unused" )
  public static final class StaticMethodDelegate {
    /**
     * Calls to {@link io.netty.util.concurrent.ScheduledFutureTask#nanoTime()} are redirected
     * to this method.
     * Sadly we can't use parameter annotated with {@link @This} or something giving a hint
     * about the call context. It looks like a consequence of JVMTI reload not supporting method
     * addition (adding a parameter would imply creating a new method).
     */
    public static long nanoTime() {
      final long supplied = longSupplier.getAsLong() ;
      LOGGER.debug( "Called " + StaticMethodDelegate.class.getSimpleName() + "#nanoTime(), " +
          "returns " + supplied + "." ) ;
      return supplied ;
    }

  }

  private static LongSupplier longSupplier = null ;

  static void install( final LongSupplier longSupplier ) {
    install( longSupplier, true ) ;
  }

  /**
   *
   * @param longSupplier
   * @param suppliedNanosRelativeToClassloadingTime if {@code true}, supplied nanoseconds are
   *     relative to {@link io.netty.util.concurrent.ScheduledFutureTask#START_TIME}.
   *     Original behavior of the hacked method is to substract
   *     {@link io.netty.util.concurrent.ScheduledFutureTask#START_TIME} from value returned
   *     by {@link System#nanoTime()} (probably to make number more readable and reduce the risk
   *     of an overflow). During tests we prefer to not care about start time so there is this
   *     option to add it automatically.
   */
  static void install(
      final LongSupplier longSupplier,
      final boolean suppliedNanosRelativeToClassloadingTime
  ) {
    checkState( ScheduledFutureTaskHack.longSupplier == null ) ;
    if( suppliedNanosRelativeToClassloadingTime ) {
      final long startTime = START_TIME ;
      LOGGER.debug(
          "Installing with value of " +
          SCHEDULEDFUTURETASK_STARTTIME_FIELD.toGenericString() +
          " = " + startTime + " automatically added to the values supplied."
      ) ;
      class AdjustedLongSupplier implements LongSupplier {
        @Override
        public long getAsLong() {
          return longSupplier.getAsLong() + startTime ;
        }
        @Override
        public String toString() {
          return ToStringTools.getNiceClassName( this ) + "{startTime=" + startTime + "}" ;
        }
      }
      ScheduledFutureTaskHack.longSupplier = new AdjustedLongSupplier() ;
    } else {
      ScheduledFutureTaskHack.longSupplier = checkNotNull( longSupplier ) ;
    }
    ByteBuddyAgent.install() ;
    LOGGER.info( "Successfully installed ByteBuddy Agent." ) ;
    redefineClass() ;
    LOGGER.info( "Successfully redefined static method implementation." ) ;
  }

  private static void redefineClass() {
    new ByteBuddy()
        .redefine( SCHEDULEDFUTURETASK_CLASS )
        .method( named( "nanoTime" )
            .and( isStatic() )
            .and( isPackagePrivate() )
            .and( takesArguments( 0 ) )
            .and( returns( long.class ) )
        )
        .intercept( MethodDelegation.to( StaticMethodDelegate.class ) )
        .make()
        .load( ScheduledFutureTaskHack.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent() )
    ;
  }

  /**
   * Invokes method replacing {@link io.netty.util.concurrent.ScheduledFutureTask#nanoTime()}.
   */
  public static long invokeNanoTime() {
    try {
      return ( long ) SCHEDULEDFUTURETASK_NANOTIME_METHOD.invoke( null ) ;
    } catch( IllegalAccessException | InvocationTargetException e ) {
      throw new Error( e ) ;
    }
  }

  /**
   * The {@link io.netty.util.concurrent.ScheduledFutureTask#deadlineNanos()} method returns
   * the value made from {@link System#nanoTime()},
   * minus {@link io.netty.util.concurrent.ScheduledFutureTask#START_TIME},
   * plus the delay before executing the task.
   */
  public static Long invokeDeadlineNanos( final Future future ) {
    try {
      if( SCHEDULEDFUTURETASK_DEADLINENANOS_METHOD.getDeclaringClass()
          .isAssignableFrom( future.getClass() )
      ) {
        return ( long ) SCHEDULEDFUTURETASK_DEADLINENANOS_METHOD.invoke( future ) ;
      } else {
        return null ;
      }
    } catch( IllegalAccessException | InvocationTargetException e ) {
      throw new Error(
          "Could not access method " + SCHEDULEDFUTURETASK_DEADLINENANOS_METHOD + " in " + future,
          e
      ) ;
    }
  }

  private static long readStartTime() {
    try {
      return ( long ) SCHEDULEDFUTURETASK_STARTTIME_FIELD.get( null ) ;
    } catch( IllegalAccessException e ) {
      throw new Error(
          "Could not access static field " + SCHEDULEDFUTURETASK_STARTTIME_FIELD,
          e
      ) ;
    }
  }

  public static final long START_TIME = readStartTime() ;


}
