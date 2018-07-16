package com.otcdlink.chiron.mockster;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import junit.framework.AssertionFailedError;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Verifies that method calls do happen on mocks.
 * This is especially suited for testing asynchronous behaviors and callbacks.
 * Test semantic relies on expressing method call expectations at the time they are expected,
 * and block until they happen. This is clearer than the record-replay-verify model.
 * This can work only if system under test runs in another thread.
 *
 * <h1>Usage</h1>
 * <pre>
 * public void void myTest() {
 *   try( final Mockster mockster = new Mockster() ) {
 *     final Whatever captured ;
 *     final Subsystem subsystem = mockster.mock( Subsystem.class ) ;
 *     final SomeComponent = new SomeComponent( subsystem ) ;
 *     assertThat( future.get() ).isEqualTo( 144 ) ;
 *     subsystem.someMethod( captured = withCapture() ) ;
 *     someComponent.callSubsystemWith( 123 ) ) ;
 *     assertThat( captured ).isCorrectRegardingWePassed( 123 ) ;
 *
 *     final Future< Integer > future = someComponent.squareAsync( 12 ) ;
 *     nextResult( 144 ) ;
 *     // Blocks until someComponents calls the method. Then asserts on parameter values.
 *     // Then lets the method called by someComponent return 144.
 *     subsystem.power( 12, 2 ) ;
 *     // The caller is unblocked so we can get the result.
 *   } // Automatic release asserts there is no pending call.
 * }
 * </pre>
 * <h1>Yet another mocking framework, why?</h1>
 * <p>
 * JMockit can not block a test until an expectation or a timeout get reached.
 * So its record-replay-verify model doesn't play well with multithreaded code and callbacks.
 * Getting the callbacks in time needs some hacks that disrupt natural order of events
 * and make the tests unreadable.
 *
 * <h1>Limitations</h1>
 * <ul>
 *   <li>
 *     Mock interfaces only (using {@link Proxy}).
 *   </li><li>
 *     Code under test <em>must</em> run in another thread.
 *   </li><li>
 *     No vararg support yet.
 *   </li>
 * </ul>
 */
public final class Mockster implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger( Mockster.class ) ;

  public static final int DEFAULT_TIMEOUT_MS = 1000 ;
  public static final ThreadFactory COUNTING_DAEMON_THREAD_FACTORY = ExecutorTools.newCountingDaemonThreadFactory( Mockster.class.getSimpleName() + "-side-executor" );
  private final long invocationTimeoutMs ;

  public Mockster() {
    this( DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS ) ;
  }

  public Mockster( final int invocationTimeoutDuration, final TimeUnit invocationTimeoutUnit ) {
    checkArgument( invocationTimeoutDuration >= 0 ) ;
    this.invocationTimeoutMs = invocationTimeoutUnit.toMillis( invocationTimeoutDuration ) ;
    installNewCoreVerifier() ;
    pushToThreadLocalStack() ;
    LOGGER.info( "Created " + this + "." ) ;
  }

  @Override
  public void close() {
    try {
      coreVerifier.checkNoPendingInvocation() ;
      installNewCoreVerifier() ;
    } finally {
      popFromThreadLocalStack() ;
      LOGGER.info( "Closed " + this + "." ) ;
    }
  }

// ======================
// Out of Verifier thread
// ======================

  private final ExecutorService sideExecutor =
      Executors.newSingleThreadExecutor( COUNTING_DAEMON_THREAD_FACTORY ) ;

  public void runOutOfVerifierThread( final Runnable runnable ) {
    sideExecutor.submit( runnable ) ;
  }

  public void runTaskOutOfVerifierThread( final Task task ) throws Throwable {
    sideExecutor.submit( () -> { task.run() ; return null ; } ) ;
  }

  public interface Task {
    void run() throws Exception ;
  }

// ===============
// Thread-locality
// ===============

  private static final ThreadLocal< List< Mockster > > mocksterStackThreadLocal =
      new ThreadLocal<>() ;

  private static List< Mockster > mocksterStack( final boolean mustExist ) {
    List< Mockster > mocksterStack = mocksterStackThreadLocal.get() ;
    if( mocksterStack == null ) {
      if( mustExist ) {
        throw new IllegalStateException( "No stack of " + Mockster.class.getSimpleName() ) ;
      }
      mocksterStack = new ArrayList<>() ;
      mocksterStackThreadLocal.set( mocksterStack ) ;
    }
    return mocksterStack ;
  }

  /**
   * Call this method only for a {@link Mockster} that is part of some testing framework
   * that knows when verification takes end.
   */
  public void pushToThreadLocalStack() {
    mocksterStack( false ).add( this ) ;
  }

  /**
   * Call this method only for a {@link Mockster} that is part of some testing framework
   * that knows when verification takes end.
   */
  public void popFromThreadLocalStack() {
    final List< Mockster > mocksterStack = mocksterStack( true ) ;
    checkArgument( ! mocksterStack.isEmpty(), "No stack of " + Mockster.class.getSimpleName() ) ;
    final Mockster removed = mocksterStack.remove( mocksterStack.size() - 1 ) ;
    checkState( removed == this,
        "Removed " + removed + " from the top of the stack differs from " + this ) ;
  }

  private static Mockster currentMockster() {
    final List< Mockster > mocksterStack = mocksterStack( true ) ;
    checkState( ! mocksterStack.isEmpty() ) ;
    return mocksterStack.get( mocksterStack.size() - 1 ) ;
  }

  public static void nextResult( final Object object ) {
    currentMockster().nextResult = object ;
  }

  public static void nextThrowable( final Throwable throwable ) {
    currentMockster().nextThrowable = throwable ;
  }

  @Deprecated
  public static void nextInvocationIsNonBlockingOperative( final boolean isNonBlockingOperative ) {
    currentMockster().nextInvocationIsNonBlockingOperative = isNonBlockingOperative ;
  }

  public static < T > T withVerification( final Consumer< T > verifier ) {
    return currentMockster().argumentMatching( verifier ) ;
  }

  public static < T > T exactly( final T expected ) {
    return currentMockster().argumentMatching(
        actualValue -> Assertions.assertThat( actualValue ).isEqualTo( expected ) ) ;
  }

  public static < T > T withCapture() {
    return currentMockster().argumentCapture() ;
  }

  public static < T > T withNull() {
    return currentMockster().argumentNull() ;
  }

  public static < T > T any() {
    return currentMockster().argumentAny() ;
  }

  @SuppressWarnings( "unused" )
  public static int anyInt() {
    any() ;
    return 0 ;
  }

  @SuppressWarnings( "unused" )
  public static boolean anyBoolean() {
    any() ;
    return false ;
  }


// ============
// CoreVerifier
// ============

  /**
   * Mutable, may get {@link CoreVerifier#disable()}'d and recreated after
   * {@link #verify(AssertionBlock, boolean)}.
   */
  private CoreVerifier coreVerifier = null ;

  private void installNewCoreVerifier() {
    if( coreVerifier != null ) {
      coreVerifier.disable() ;
    }
    coreVerifier = new CoreVerifier() ;
    this.coreVerifier.nextResultSupplier( () -> {
      final Object next = nextResult ;
      nextResult = null ;
      return next ;
    } ) ;
    this.coreVerifier.nextThrowableSupplier( () -> {
      final Throwable next = nextThrowable ;
      nextThrowable = null ;
      return next ;
    } ) ;
    this.coreVerifier.nextInvocationIsNonBlockingOperativeSupplier( () -> {
      final boolean next = nextInvocationIsNonBlockingOperative ;
      nextInvocationIsNonBlockingOperative = false ;
      return next ;
    } ) ;
    this.coreVerifier.timeoutMs( invocationTimeoutMs ) ;
  }


  /**
   * The next method call on a mock will use this value in account as return value (if there is
   * one), and clear this value. The value is cleared regardless of the return type of the method.
   */
  private Object nextResult = null ;

  /**
   * The next method call on a mock will throw this {@code Throwable} (if there is
   * one), and clear this value. The value is cleared after every method call.
   */
  private Throwable nextThrowable = null ;

  /**
   * The next method invocation on a mock will be considered as Operative invocation, and
   * it will not block until the corresponding Verifying invocation happens, in order to
   * obtain the value to return (or the {@code Throwable} to throw).
   * This is useful when the test runs a captured callback that invokes some mock method,
   * but this call should be considered as Operative, not Verifying.
   */
  private boolean nextInvocationIsNonBlockingOperative = false ;


// =============
// Mock creation
// =============

  public < OBJECT > OBJECT mock( final Class< OBJECT > objectClass ) {
    if( objectClass.isInterface() ) {
      return mock( new Class< ? >[] { objectClass } ) ;
    } else {
      return mock( new TypeToken< OBJECT >( objectClass ) {} ) ;
    }
  }

  public < OBJECT > OBJECT mock( final TypeToken< OBJECT > typeToken ) {
    final Class< ? super OBJECT > rawType = typeToken.getRawType() ;
    if( rawType.isInterface() ) {
      return mock( new Class< ? >[] { rawType } ) ;
    } else {
      return mock( rawType.getInterfaces() ) ;
    }
  }

  public < OBJECT > OBJECT mock( final Class[] interfaces ) {
    final Object proxy = Proxy.newProxyInstance(
        Mockster.class.getClassLoader(),
        ImmutableList.builder()
            .addAll( Arrays.asList( interfaces ) )
            .add( CoreVerifier.MockLogic.class )
            .build().toArray( new Class< ? >[ 0 ] )
        ,
        coreVerifier
    ) ;
    LOGGER.debug( "Created " + VerifierTools.proxyAwareToString( proxy ) +
        " in " + coreVerifier + "." ) ;
    return ( OBJECT ) proxy ;
  }


// =============
// ArgumentTraps
// =============

  private < T > T argumentCapture() {
    return coreVerifier.withCapture( invocationTimeoutMs ) ;
  }

  private < OBJECT > OBJECT argumentAny() {
    coreVerifier.withMatcher( invocationTimeoutMs, ( location, actualValue ) -> { } ) ;
    return null ;
  }

  private < OBJECT > OBJECT argumentMatching( final Consumer< OBJECT > verifier ) {
    return coreVerifier.withMatcher(
        invocationTimeoutMs,
        ( location, actualValue ) -> verifier.accept( actualValue )
    ) ;
  }

  /**
   * Verifies the nullity of actual argument value.
   */
  private  < T > T argumentNull() {
    return coreVerifier.withMatcher(
        invocationTimeoutMs,
        ( location, actualValue ) -> Assertions.assertThat( actualValue )
            .describedAs( location.asString() ).isNull()
    ) ;
  }


// ============
// Verification
// ============

  /**
   * A lamba-friendly code block doing things with mocks.
   * 
   * <h1>Design note</h1>
   * <p>
   * Moving methods like {@link #withCapture()} to this interface doesn't add more scoping.
   * Static methods in interfaces can be called from anywhere.
   */
  public interface AssertionBlock {
    void run() throws Exception ;
  }

  @SuppressWarnings( "unused" )
  public void ensureAllVerificationsComplete() {
    try {
      coreVerifier.checkNoPendingInvocation() ;
    } catch( Exception e ) {
      LOGGER.error( "Some unexpected call happened: ", e ) ;
      final Throwable rootCause = Throwables.getRootCause( e ) ;
      throw new AssertionFailedError(
          "Some unexpected call happened: " + rootCause.getClass().getName() + ", " +
              rootCause.getMessage()
      ) ;
    }
    LOGGER.debug( "Successfully verified that nothing happened on " + this + " succeeded." ); ;
  }

  @Deprecated
  public void verify( final AssertionBlock assertionBlock ) throws Exception {
    verify( assertionBlock, true ) ;
  }

  @Deprecated
  public void verify(
      final AssertionBlock assertionBlock,
      final boolean cleanupAfterVerification
  ) throws Exception {
    pushToThreadLocalStack() ;
    try {
      assertionBlock.run() ;
      coreVerifier.checkNoPendingInvocation() ;
      if( cleanupAfterVerification ) {
        installNewCoreVerifier() ;
      }
    } finally {
      popFromThreadLocalStack() ;
    }
  }

}
