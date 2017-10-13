package com.otcdlink.chiron.toolbox.concurrent.freeze;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;

public class ThreadFreezerTest {

  @Test
  public void empty() throws Exception {
    final ThreadFreezer< MyKey > threadFreezer = new ThreadFreezer<>( ImmutableSet.of() ) ;
    assertThat( threadFreezer.waitForAllFrozen() ).isEmpty() ;
    threadFreezer.unfreezeAll() ;
  }

  @Test
  public void justFreezeAndUnfreeze() throws Exception {
    final ThreadFreezer< MyKey > threadFreezer =
        new ThreadFreezer<>( ImmutableSet.of( MyKey.FIRST ) ) ;
    justFreezeAndUnfreeze( threadFreezer );
  }

  @Test
  public void continueWhenWarm() throws Exception {
    final ThreadFreezer< MyKey > threadFreezer =
        new ThreadFreezer<>( ImmutableSet.of( MyKey.FIRST ) ) ;
    final Semaphore done = new Semaphore( 0 ) ;
    new Thread( () -> {
      threadFreezer.internalControl( MyKey.FIRST ).continueWhenWarm() ;
      done.release() ;
    } ).start() ;
    done.acquireUninterruptibly( 1 ) ;
  }

  @Test
  public void reuse() throws Exception {
    final ThreadFreezer< MyKey > threadFreezer =
        new ThreadFreezer<>( ImmutableSet.of( MyKey.FIRST ) ) ;
    justFreezeAndUnfreeze( threadFreezer ) ;
    justFreezeAndUnfreeze( threadFreezer ) ;
  }

  @Test
  public void checkReallyFrozen() throws Exception {
    final ThreadFreezer< MyKey > threadFreezer =
        new ThreadFreezer<>( ImmutableSet.of( MyKey.FIRST ) ) ;
    final FreezeControl freezeControl = threadFreezer.internalControl( MyKey.FIRST ) ;
    final FreezerOperator freezerOperator = new FreezerOperator( freezeControl, "first" ) ;
    freezerOperator.start() ;
    freezerOperator.allowFreeze() ;
    freezerOperator.waitForFreezeDone() ;
    freezerOperator.allowContinueWhenWarm() ;
    for( int i = 0 ; i < 10 ; i ++ ) {
      // Any better idea to verify that freezing truly happened?
      assertThat( freezerOperator.waitingToBeWarm() ).isTrue() ;
      Uninterruptibles.sleepUninterruptibly( 10, TimeUnit.NANOSECONDS ) ;
    }
    threadFreezer.unfreezeAll() ;
    freezerOperator.waitForContinued() ;
  }
  
  @Test
  public void concurrency() throws Exception {
    final ThreadFreezer< MyKey > threadFreezer = new ThreadFreezer<>( MyKey.ALL ) ;
    final FreezeControl freezeControl1 = threadFreezer.internalControl( MyKey.FIRST ) ;
    final FreezeControl freezeControl2 = threadFreezer.internalControl( MyKey.SECOND ) ;
    final FreezerOperator freezerOperator1 = new FreezerOperator( freezeControl1, "first" ) ;
    final FreezerOperator freezerOperator2 = new FreezerOperator( freezeControl2, "second" ) ;

    freezerOperator1.start() ;
    freezerOperator2.start() ;
    freezerOperator1.allowFreeze() ;

    LOGGER.info( "Waiting for all frozen ..." ) ;
    freezerOperator2.allowFreeze() ;
    LOGGER.info( "All frozen for " + threadFreezer + ": " + threadFreezer.waitForAllFrozen() ) ;

    freezerOperator1.waitForFreezeDone() ;
    freezerOperator2.waitForFreezeDone() ;
    freezerOperator1.allowContinueWhenWarm() ;
    freezerOperator2.allowContinueWhenWarm() ;

    threadFreezer.unfreezeAll() ;
    LOGGER.info( "All unfrozen for " + threadFreezer + "." ) ;
    freezerOperator1.waitForContinued() ;
    freezerOperator2.waitForContinued() ;
  }


// =======
// Fixture
// =======


  private static final Logger LOGGER = LoggerFactory.getLogger( ThreadFreezerTest.class ) ;

  private enum MyKey {
    FIRST,
    SECOND,
    ;

    public static final ImmutableSet< MyKey > ALL = ImmutableSet.copyOf( values() ) ;
  }

  private static void justFreezeAndUnfreeze( final ThreadFreezer< MyKey > threadFreezer ) {
    final FreezeControl freezeControl = threadFreezer.internalControl( MyKey.FIRST ) ;
    final String frozen = "first" ;
    final FreezerOperator freezerOperator = new FreezerOperator( freezeControl, frozen ) ;
    freezerOperator.start() ;
    freezerOperator.allowFreeze() ;
    freezerOperator.waitForFreezeDone() ;
    assertThat( threadFreezer.waitForAllFrozen().get( MyKey.FIRST ) ).isEqualTo( frozen ) ;
    threadFreezer.unfreezeAll() ;
    freezerOperator.allowContinueWhenWarm() ;
    freezerOperator.waitForContinued() ;
  }

  private static class FreezerOperator {
    final FreezeControl freezeControl;
    private final String frozen ;

    private FreezerOperator(
        final FreezeControl freezeControl,
        final String frozen
    ) {
      this.freezeControl = checkNotNull( freezeControl ) ;
      Preconditions.checkArgument( ! Strings.isNullOrEmpty( frozen ) ) ;
      this.frozen = frozen ;
    }

    private final Semaphore allowFreeze = new Semaphore( 0 ) ;
    private final Semaphore notifyFreezeDone = new Semaphore( 0 ) ;

    private final Semaphore allowContinueWhenWarm = new Semaphore( 0 ) ;
    private final Semaphore notifyContinued = new Semaphore( 0 ) ;

    public void start() {
      final Semaphore notifyThreadStarted = new Semaphore( 0 ) ;
      new Thread( () -> {
        notifyThreadStarted.release() ;
        LOGGER.info( "Started " + freezeControl + "." ) ;
        allowFreeze.acquireUninterruptibly( 1 ) ;
        freezeControl.freeze( frozen ) ;
        LOGGER.info( "Frozen " + freezeControl + "." ) ;
        notifyFreezeDone.release() ;

        allowContinueWhenWarm.acquireUninterruptibly( 1 ) ;
        LOGGER.info( "Continuing " + freezeControl + " when warm ..." ) ;
        freezeControl.continueWhenWarm() ;
        LOGGER.info( "Continued " + freezeControl + " because it was warm." ) ;

        notifyContinued.release() ;
      }, frozen ).start() ;
      notifyThreadStarted.acquireUninterruptibly( 1 ) ;
    }

    public void allowFreeze() {
      allowFreeze.release() ;
    }

    public void waitForFreezeDone() {
      notifyFreezeDone.acquireUninterruptibly( 1 ) ;
    }

    public void allowContinueWhenWarm() {
      allowContinueWhenWarm.release() ;
    }

    public void waitForContinued() {
      notifyContinued.acquireUninterruptibly( 1 ) ;
    }

    public boolean waitingToBeWarm() {
      return notifyContinued.availablePermits() == 0 ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{" + freezeControl.toString() + "}" ;
    }
  }

}