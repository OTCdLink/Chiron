package com.otcdlink.chiron.mockster;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.reflect.AbstractInvocationHandler;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contains the real logic behing {@link Mockster}.
 */
class CoreVerifier extends AbstractInvocationHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger( CoreVerifier.class ) ;
  private final Thread verifierThread ;

  private final ScheduledExecutorService scheduledExecutorService =
      Executors.newSingleThreadScheduledExecutor(
          ExecutorTools.newThreadFactory( Mockster.class.getSimpleName() ) )
  ;

  private final ReentrantLock lock = new ReentrantLock() ;
  private final Condition condition = lock.newCondition() ;

  private final List< Object > methodParametersStock = new ArrayList<>() ;
  private final List< OperativeInvocation > operativeInvocations = new ArrayList<>() ;
  private final List< VerifyingInvocation > verifyingInvocations = new ArrayList<>() ;

  private int lastVerifiedInvocationIndex = -1 ;

  private boolean enabled = true ;

  CoreVerifier() {
    this.verifierThread = Thread.currentThread() ;
  }

  @Override
  public String toString() {
    return ToStringTools.nameAndHash( this ) ;
  }

  private long timeoutMs ;

  public void timeoutMs( final long timeoutMs ) {
    lock.lock() ;
    try {
      checkArgument( timeoutMs >= 0 ) ;
      this.timeoutMs = timeoutMs ;
    } finally {
      lock.unlock() ;
    }
  }

  private Supplier< Object > nextResultSupplier ;

  public void nextResultSupplier( final Supplier< Object > supplier ) {
    this.nextResultSupplier = checkNotNull( supplier ) ;
  }

  private Supplier< Throwable > nextThrowableSupplier ;

  public void nextThrowableSupplier( final Supplier< Throwable > supplier ) {
    this.nextThrowableSupplier = checkNotNull( supplier ) ;
  }

  private BooleanSupplier nextInvocationIsNonBlockingOperativeSupplier;

  public void nextInvocationIsNonBlockingOperativeSupplier( final BooleanSupplier supplier ) {
    this.nextInvocationIsNonBlockingOperativeSupplier = checkNotNull( supplier ) ;
  }

  private void checkEnabled() {
    checkState( enabled ) ;
  }

  public void disable() {
    lock.lock() ;
    try {
      enabled = false ;
      scheduledExecutorService.shutdown() ;
      LOGGER.debug( "Disabled " + this + "." ) ;
    } finally {
      lock.unlock() ;
    }
  }

  protected Object handleInvocation(
      @Nonnull final Object proxy,
      @Nonnull final Method method,
      @Nonnull final Object... arguments
  ) throws Throwable {
    lock.lock() ;
    checkEnabled() ;
    try {
      if( isRunningInVerifierThread() /*&& VerifierTools.isRunningInInitializer()*/ ) {
        final boolean nonBlockingOperative =
            nextInvocationIsNonBlockingOperativeSupplier.getAsBoolean() ;
        if( nonBlockingOperative ) {
          return operativeInvocation( proxy, method, true, arguments ) ;
        } else {
          invocationFromVerifierThread(
              proxy, method, arguments, nextResultSupplier.get(), nextThrowableSupplier.get() ) ;
          return null ;
        }
      } else {
        return operativeInvocation( proxy, method, false, arguments ) ;
      }
    } finally {
      lock.unlock() ;
    }
  }

  /**
   * Caller must own the {@link #lock}.
   *
   * @param arguments may feed the {@link CapturingEvaluator}s that are currently blocking
   *     the {@link #verifyingInvocations}.
   */
  private Object operativeInvocation(
      @Nonnull final Object proxy,
      @Nonnull final Method method,
      final boolean nextInvocationIsNonBlockingOperative,
      @Nonnull final Object[] arguments
  ) throws Throwable {
    final int invocationIndex = operativeInvocations.size() ;
    final OperativeInvocation operativeInvocation = new OperativeInvocation(
        invocationIndex,
        proxy,
        method,
        Arrays.asList( arguments ),
        null,
        null
    ) ;
    operativeInvocations.add( operativeInvocation ) ;
    LOGGER.debug( "Recorded " + operativeInvocation + "." ) ;
    condition.signalAll() ;
    if( nextInvocationIsNonBlockingOperative ) {
      LOGGER.debug(
          "Not waiting for next " + VerifyingInvocation.class.getSimpleName() + " " +
          "with index of " + invocationIndex + " " +
          "because of nextInvocationIsNonBlockingOperative set on " + operativeInvocation + "."
      ) ;
      if( Void.TYPE.equals( method.getReturnType() ) ) {
        return null ;
      } else {
        final String message = "Can't make Operative call non-blocking for a non-void method: " +
            method ;
        LOGGER.error( message + "." ) ;
        throw new IllegalDeclarationException( message ) ;
      }
      /** The {@link VerifyingInvocation} will occur later in the same thread, so we can't
       * wait for it here or it would cause a deadlock. */
    } else {
      while( invocationIndex >= verifyingInvocations.size() ) {
        try {
          LOGGER.debug(
              "Waiting for new " + VerifyingInvocation.class.getSimpleName() + ". " +
              "It will have an index of " + invocationIndex + ". " +
              "Current " + VerifyingInvocation.class.getSimpleName() + " count: " +
              verifyingInvocations.size() + ", current " +
              OperativeInvocation.class.getSimpleName() + " count: " +
              " " + operativeInvocations.size() + "."
          ) ;
          condition.await() ;
        } catch( InterruptedException e ) {
          throw new IllegalDeclarationException(
              "Could find no match for " + operativeInvocation + " in " + verifyingInvocations ) ;
        }
      }
      final VerifyingInvocation verifyingInvocation = verifyingInvocations.get(
          invocationIndex ) ;
      LOGGER.debug( "Done waiting, got " + verifyingInvocation + "." ) ;
      condition.signalAll() ;
      if( verifyingInvocation.throwable != null ) {
        throw verifyingInvocation.throwable ;
      }
      return verifyingInvocation.result ;
    }
  }

  /**
   * Caller must acquire {@link #lock}.
   */
  private void invocationFromVerifierThread(
      @Nonnull final Object proxy,
      @Nonnull final Method method,
      @Nonnull final Object[] arguments,
      final Object result,
      final Throwable throwable
  ) {
    final List< Object > argumentEvaluators ;
    final int verificationIndex = verifyingInvocations.size() ;
    final boolean usingLiteralArguments = methodParametersStock.isEmpty() ;
    if( usingLiteralArguments ) {
      argumentEvaluators = toArgumentMatchers( arguments, verificationIndex ) ;
    } else {
      if( method.getParameterCount() != methodParametersStock.size() ) {
        throw new IllegalDeclarationException(
            "Number of arguments to verify (" + methodParametersStock.size() + ") " +
            "differs from argument matcher count " +
            "(" + method.getParameterTypes().length + " in " +
            ImmutableList.copyOf( method.getParameterTypes() ) + ") for " +
            method + ", this can happen with a mix between " +
            ArgumentTrap.class.getSimpleName() + "s and plain values"
        ) ;
      }
      argumentEvaluators = methodParametersStock ;
    }
    final VerifyingInvocation verifyingInvocation = new VerifyingInvocation(
        verificationIndex,
        proxy,
        method,
        argumentEvaluators,
        result,
        throwable
    ) ;
    methodParametersStock.clear() ;
    verifyingInvocations.add( verifyingInvocation ) ;
    LOGGER.debug( "Recorded " + verifyingInvocation + "." ) ;
    condition.signalAll() ; // Wake up caller that expects a return value, maybe.
    waitForOperativeInvocation( verificationIndex, timeoutMs ) ;
  }

  private static List< Object > toArgumentMatchers(
      final Object[] arguments,
      final int verificationIndex
  ) {
    return Streams.mapWithIndex(
        Stream.of( arguments ),
        ( a, i ) -> new ArgumentEquality(
            verificationIndex, ( int ) i, a ) )
        .collect( Collectors.toList() )
    ;
  }


  /**
   * Caller must acquire {@link #lock}. This code runs in {@link #verifierThread}.
   *
   * @param highestInvocationIndex the maximum index in {@link #verifyingInvocations},
   *     we may have to verify several ones in a row because more than one
   *     {@link OperativeInvocation}s occured in a burst.
   */
  private void waitForOperativeInvocation(
      final int highestInvocationIndex,
      final long timeoutMs
  ) {
    checkState( Thread.currentThread() == verifierThread ) ;
    waitUntilOperativeInvocationOrTimeout( highestInvocationIndex, timeoutMs ) ;

    for( int invocationIndex = lastVerifiedInvocationIndex + 1 ;
        invocationIndex <= highestInvocationIndex ;
        invocationIndex ++
    ) {
      try {
        final OperativeInvocation operativeInvocation = operativeInvocations.get( invocationIndex ) ;
        final VerifyingInvocation verifyingInvocation = verifyingInvocations.get( invocationIndex ) ;
        LOGGER.info( "Verifying " + Invocation.class.getSimpleName() +
            " with index of " + invocationIndex + " ..." ) ;
        assertThat( operativeInvocation.method ).isEqualTo( verifyingInvocation.method ) ;
        for( int argumentIndex = 0 ;
             argumentIndex < verifyingInvocation.arguments.size() ;
             argumentIndex ++
        ) {
          final ArgumentTrap argumentTrap =
              verifyingInvocation.argumentTrapAt( argumentIndex ) ;
          if( argumentTrap instanceof ArgumentEvaluator ) {
            /** Evaluate now, so we unblock an {@link OperativeInvocation} waiting for a
             * {@link CapturingEvaluator}. */
            ( ( ArgumentEvaluator ) argumentTrap )
                .evaluate( operativeInvocation.arguments.get( argumentIndex ) ) ;
          }
        }
        LOGGER.info( "Verified " + Invocation.class.getSimpleName() + " with index of " +
            invocationIndex + "." ) ;
      } catch( final Throwable throwable ) {
        LOGGER.error(
            "Verification failed at index " + invocationIndex + ". " +
            OperativeInvocation.class.getSimpleName() + ": " +
            operativeInvocations.get( invocationIndex ) + ", " +
            VerifyingInvocation.class.getSimpleName() + ": " +
            verifyingInvocations.get( invocationIndex ) + "."
            ,
            throwable
        ) ;
        Throwables.throwIfUnchecked( throwable ) ;
        throw new RuntimeException( throwable ) ;
      }
    }
    lastVerifiedInvocationIndex = highestInvocationIndex ;
  }

  /**
   * Caller must hold {@link #lock}.
   */
  private void waitUntilOperativeInvocationOrTimeout( int invocationIndex, long timeoutMs ) {
    final ScheduledFuture< ? > timeoutTask = scheduledExecutorService.schedule(
        new TimeoutTask( invocationIndex, Thread.currentThread() ),
        timeoutMs,
        TimeUnit.MILLISECONDS
    ) ;
    while( invocationIndex >= operativeInvocations.size() ) {
      try {
        LOGGER.debug(
            "Waiting for " + OperativeInvocation.class.getSimpleName() + " with " +
            "an index of " + invocationIndex + ". " +
            "Current " + VerifyingInvocation.class.getSimpleName() + " count: " +
            verifyingInvocations.size() + ", current " +
            OperativeInvocation.class.getSimpleName() + " count: " +
            " " + operativeInvocations.size() + "."
        ) ;
        condition.await() ;
      } catch( final InterruptedException e ) {
        throw new InvocationTimeoutError() ;
      }
    }
    timeoutTask.cancel( false ) ;
  }


  private boolean isRunningInVerifierThread() {
    return Thread.currentThread() == verifierThread ;
  }

  private class TimeoutTask implements Runnable {

    private final int invocationIndex ;
    private final Thread threadWaiting ;

    private TimeoutTask( final int invocationIndex, final Thread threadWaiting ) {
      this.invocationIndex = invocationIndex ;
      this.threadWaiting = checkNotNull( threadWaiting ) ;
    }

    @Override
    public void run() {
      lock.lock() ;
      checkEnabled() ;
      try {
        if( invocationIndex >= operativeInvocations.size() ) {
          threadWaiting.interrupt() ;
        }
      } finally {
        lock.unlock() ;
      }
    }
  }


  < ARGUMENT > ARGUMENT withCapture( long invocationTimeoutMs ) {
    lock.lock() ;
    checkEnabled() ;
    try {
      final ArgumentCapture argumentCapture = new ArgumentCapture(
          this,
          verifyingInvocations.size(),
          methodParametersStock.size(),
          invocationTimeoutMs
      ) ;
      methodParametersStock.add( argumentCapture ) ;
      return waitForOperativeArgumentValue(
          argumentCapture.methodCallIndex,
          argumentCapture.parameterIndex,
          invocationTimeoutMs
      ) ;
    } finally {
      lock.unlock() ;
    }
  }

  < ARGUMENT > ARGUMENT withMatcher(
      final long invocationTimeoutMs,
      final ArgumentVerifier< ARGUMENT > argumentVerifier
  ) {
    lock.lock() ;
    checkEnabled() ;
    try {
      final CapturingEvaluator capturingEvaluator = new CapturingEvaluator(
          this,
          verifyingInvocations.size(),
          methodParametersStock.size(),
          invocationTimeoutMs,
          argumentVerifier
      ) ;
      methodParametersStock.add( capturingEvaluator ) ;
      return waitForOperativeArgumentValue(
          capturingEvaluator.methodCallIndex,
          capturingEvaluator.parameterIndex,
          invocationTimeoutMs
      ) ;
    } finally {
      lock.unlock() ;
    }
  }

  /**
   * Blocks in {@link #withCapture(long)} until operative method call happens.
   */
  < VALUE > VALUE waitForOperativeArgumentValue(
      final int invocationIndex,
      final int argumentIndex,
      final long timeoutMs
  ) {
    lock.lock() ;
    checkEnabled() ;
    try {
      waitUntilOperativeInvocationOrTimeout( invocationIndex, timeoutMs ) ;
      final OperativeInvocation operativeInvocation = operativeInvocations.get( invocationIndex ) ;
      final Object operativeValue = operativeInvocation.arguments.get( argumentIndex ) ;
      final Class< ? > argumentType =
          operativeInvocation.method.getParameterTypes()[ argumentIndex ] ;
      return VerifierTools.safeValue( argumentType, operativeValue ) ;
    } finally {
      lock.unlock() ;
    }
  }

  void checkNoPendingInvocation() {
    lock.lock() ;
    checkEnabled() ;
    try {
      if( verifyingInvocations.size() < operativeInvocations.size() ) {
        final List< OperativeInvocation > unverified = this.operativeInvocations.subList(
            verifyingInvocations.size(), this.operativeInvocations.size() ) ;
        throw new UnverifiedInvocationError( "Unverified invocations: " +
            unverified ) ;
      }
//      logger.debug( "Verified there is no pending invocation." ) ;
//      final List< Runnable > runnables = scheduledExecutorService.shutdownNow() ;
//      condition.signalAll() ;
//      if( ! runnables.isEmpty() ) {
//        for( final Runnable runnable : runnables ) {
//          runnable.run() ;
//        }
//      }
    } finally {
      lock.unlock() ;
    }
  }


  /**
   * Tagging interface.
   */
  interface MockLogic { }
}
