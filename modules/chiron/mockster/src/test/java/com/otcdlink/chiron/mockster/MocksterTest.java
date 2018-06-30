package com.otcdlink.chiron.mockster;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.Uninterruptibles;
import com.otcdlink.chiron.testing.NameAwareRunner;
import com.otcdlink.chiron.toolbox.StringWrapper;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import org.junit.After;
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.otcdlink.chiron.mockster.Mockster.nextResult;
import static com.otcdlink.chiron.mockster.Mockster.withCapture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings( "CodeBlock2Expr" )
@RunWith( NameAwareRunner.class )
public class MocksterTest {

  @Test
  public void simpleCapture() {
    try( final Mockster mockster = new Mockster() ) {
      final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
      LOGGER.info( "Created mock " + callback + "." ) ;
      final Stimulator< Value > stimulator = newStimulator( callback ) ;
      stimulator.scheduleConsumption( VALUE_A ) ;
      stimulator.scheduleConsumption( VALUE_B ) ;
      final Value captured0 ;
      callback.call1( captured0 = withCapture() ) ;
      assertThat( captured0 ).isEqualTo( VALUE_A ) ;
      final Value captured1 ;
      callback.call1( captured1 = withCapture() ) ;
      assertThat( captured1 ).isEqualTo( VALUE_B ) ;
    }
  }

  @Test
  public void staticShortcutOutOfScope() {
    assertThatThrownBy( () -> {
      try {
        LOGGER.info( "Using static shortcut when there is no Mockster in scope ..." ) ;
        withCapture() ;
      } catch( final Exception e ) {
        LOGGER.info( "Got this:", e ) ;
        throw e ;
      }
    } ).isInstanceOf( IllegalStateException.class ) ;
  }

  @SuppressWarnings( "Convert2MethodRef" )
  @Test
  public void unverified() {
    assertThatThrownBy( () -> {
      try( final Mockster mockster = new Mockster( 100, TimeUnit.MILLISECONDS ) ) {
        final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
        final Stimulator< Value > stimulator = newStimulator( 0, callback ) ;
        stimulator.scheduleConsumption( VALUE_A ) ;
        stimulator.shutdown() ;
      }
    } ).isInstanceOf( UnverifiedInvocationError.class ) ;
  }

  @Test
  public void supplyResults() {
    try( final Mockster mockster = new Mockster() ) {
      final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
      final Stimulator< Value > stimulator = newStimulator( callback ) ;
      stimulator.scheduleConsumption() ;
      stimulator.scheduleConsumption() ;
      nextResult( VALUE_A ) ;
      callback.supply() ;
      nextResult( VALUE_B ) ;
      callback.supply() ;
      assertThat( stimulator.suppliedValues() ).containsExactly( VALUE_A, VALUE_B ) ;
    }
  }

  @Test
  public void supplyThrowable() {
    try( final Mockster mockster = new Mockster() ) {
      final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
      final Stimulator< Value > stimulator = newStimulator( callback ) ;
      stimulator.scheduleConsumption( VALUE_A ) ;
      Mockster.nextThrowable( new AccessControlException( "Boom" ) ) ;
      callback.call1( VALUE_A ) ;
      assertThat( stimulator.suppliedThrowables() ).hasSize( 1 ) ;
      assertThat( stimulator.suppliedThrowables().iterator().next() ).hasMessageContaining( "Boom" ) ;
    }
  }

  @Test
  public void equalityMatch() {
    try( final Mockster mockster = new Mockster() ) {
      final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
      final Stimulator< Value > stimulator = newStimulator( 1, callback ) ;
      stimulator.scheduleConsumption( VALUE_A ) ;
      callback.call1( VALUE_A ) ;
    }

  }

  @Test
  public void anyMatch() {
    try( final Mockster mockster = new Mockster() ) {
      final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
      final Stimulator< Value > stimulator = newStimulator( 1, callback ) ;
      stimulator.scheduleConsumption( VALUE_A ) ;
      callback.call1( Mockster.any() ) ;
    }
  }

  @Test
  public void equalityNoMatch() {
    try( final Mockster mockster = new Mockster() ) {
      final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
      final Stimulator< Value > stimulator = newStimulator( 1, callback ) ;
      stimulator.scheduleConsumption( VALUE_A ) ;
      assertThatThrownBy( () -> {
        try {
          callback.call1( VALUE_B ) ;
        } catch( final AssertionError e ) {
          LOGGER.info( "Exception for non-match looks like this: ", e ) ;
          throw e ;
        }
      } ).isInstanceOf( ComparisonFailure.class ) ;
    }
  }

  @Test
  public void nullity() {
    try( final Mockster mockster = new Mockster() ) {
      final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
      final Stimulator< Value > stimulator = newStimulator( 1, callback ) ;
      stimulator.scheduleConsumption( ( Value ) null ) ;
      callback.call1( Mockster.withNull() ) ;
    }
  }

  @Test
  public void nullityNoMatch() {
    try( final Mockster mockster = new Mockster() ) {
      final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
      final Stimulator< Value > stimulator = newStimulator( 1, callback ) ;
      stimulator.scheduleConsumption( VALUE_A ) ;
      assertThatThrownBy( () -> {
        try {
          callback.call1( Mockster.withNull() ) ;
        } catch( final AssertionError e ) {
          LOGGER.info( "Exception for non-match looks like this: ", e ) ;
          throw e ;
        }
      } ).isInstanceOf( ComparisonFailure.class ) ;
    }

  }

  @Test
  public void mixedMatch() {
    assertThatThrownBy( () -> {
      try( final Mockster mockster = new Mockster() ) {
        final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
        final Stimulator< Value > stimulator = newStimulator( 1, callback ) ;
        stimulator.scheduleConsumption( VALUE_A, VALUE_B ) ;
        try {
          callback.call2( VALUE_A, withCapture() ) ;
        } catch( final Error e ) {
          LOGGER.info( "Exception for mixed match looks like this: ", e ) ;
          throw e ;
        }
      }
    } ) .isInstanceOf( IllegalDeclarationException.class )
        .hasMessageContaining( "mix between " + ArgumentTrap.class.getSimpleName() +
            "s and plain values" )
    ;
  }

  @SuppressWarnings( "Convert2MethodRef" )
  @Test
  public void actualAndOperativeMismatch() {
    try( final Mockster mockster = new Mockster() ) {
      final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
      final Stimulator< Value > stimulator = newStimulator( 1, callback ) ;
      stimulator.scheduleConsumption( VALUE_A, VALUE_B ) ;
      assertThatThrownBy( () -> {
        try {
          callback.supply() ;
        } catch( final Exception e ) {
          LOGGER.info( "Exception for mixed match looks like this: ", e ) ;
          throw e ;
        }
      } ) .isInstanceOf( ComparisonFailure.class ) ;
    }
  }

  @Test
  public void captureTimeout() {
    try( final Mockster mockster = new Mockster( 10, TimeUnit.MILLISECONDS ) ) {
      final Callback< Value > callback = mockster.mock( CALLBACK_TYPETOKEN ) ;
      final Stimulator< Value > stimulator = newStimulator( Long.MAX_VALUE, callback ) ;
      stimulator.scheduleConsumption( VALUE_A ) ;
      assertThatThrownBy( () -> { callback.call1( withCapture() ) ; } )
          .isInstanceOf( InvocationTimeoutError.class ) ;
    }

  }

  @Test
  public void nextInvocationIsNonBlockingOperativeSucceeds() {
    applyNextInvocationIsNonBlockingOperative( true ) ;
  }

  @Test
  public void nextInvocationIsNonBlockingOperativeMissing() {
    assertThatThrownBy( () -> {
      try {
        applyNextInvocationIsNonBlockingOperative( false ) ;
      } catch( Exception e ) {
        LOGGER.info( "Got: ", e ) ;
        throw e ;
      }
    } )
        .isInstanceOf( InvocationTimeoutError.class ) ;
  }

  private void applyNextInvocationIsNonBlockingOperative( final boolean doIt ) {
    try( final Mockster mockster = new Mockster() ) {
      final SomeComponent.Subsystem subsystem = mockster.mock( SomeComponent.Subsystem.class ) ;
      final SomeComponent.ValueSink valueSink = mockster.mock( SomeComponent.ValueSink.class ) ;

      final SomeComponent someComponent = new SomeComponent( subsystem, valueSink ) ;

      final Value value = VALUE_A ;

      someComponent.activateSubsystem() ;
      final SomeComponent.ValueConsumer valueConsumer ;
      subsystem.requestValue( valueConsumer = withCapture() ) ;

      Mockster.nextInvocationIsNonBlockingOperative( doIt ) ;
      valueConsumer.consumeValue( value ) ;

      /** Because of {@link #nextInvocationIsNonBlockingOperative} was set to {@code true},
       * the statement above caused an Operative call to
       * {@link SomeComponent.ValueSink#finallyGotValue(Value)},
       * so the statement below verifies a call that happened. */
      valueSink.finallyGotValue( value ) ;
    }

  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( MocksterTest.class ) ;

  @Before
  public void setUp() {
    LOGGER.info( "*** Running " + NameAwareRunner.getTestShortName() + " ***" ) ;
  }

  @After
  public void tearDown() {
    for( final Stimulator< ? > stimulator : stimulators ) {
      stimulator.shutdown() ;
    }
    LOGGER.info( "*** Done with " + NameAwareRunner.getTestShortName() + " ***" ) ;
  }

  private final List< Stimulator > stimulators = new ArrayList<>() ;

  private static class Value extends StringWrapper< Value > {
    public Value( final String wrapped ) {
      super( wrapped ) ;
    }
  }

  private interface Callback< VALUE > {
    void call1( VALUE value ) ;
    void call2( VALUE value1, VALUE value2 ) ;
    VALUE supply() ;
  }


  private interface Stimulator< OBJECT > {
    @SuppressWarnings( "unchecked" )
    void scheduleConsumption( final OBJECT... values ) ;
    ImmutableList< OBJECT > suppliedValues() ;
    ImmutableList< Throwable > suppliedThrowables() ;
    void shutdown() ;
  }

  private < OBJECT > Stimulator< OBJECT > newStimulator( final Callback< OBJECT > callback ) {
    return newStimulator( 100, callback ) ;
  }

  private < OBJECT > Stimulator< OBJECT > newStimulator(
      final long delayBeforeStimulationMs,
      final Callback< OBJECT > callback
  ) {
    final Stimulator< OBJECT > stimulator = new Stimulator< OBJECT >() {

      private final ExecutorService executorService = Executors.newSingleThreadExecutor(
          ExecutorTools.newThreadFactory( MocksterTest.class.getSimpleName() ) ) ;

      private final List< OBJECT > suppliedValuesRecorder =
          Collections.synchronizedList( new ArrayList<>() ) ;

      private final List< Throwable > suppliedThrowablesRecorder =
          Collections.synchronizedList( new ArrayList<>() ) ;

      @SafeVarargs
      @Override
      public final void scheduleConsumption( final OBJECT... values ) {
        executorService.execute( () -> {
          final List< OBJECT > valuesAsList = Arrays.asList( values ) ;
          LOGGER.info( "Scheduling consumption of " + valuesAsList + " in " +
              delayBeforeStimulationMs + " ms ..." ) ;
          Uninterruptibles.sleepUninterruptibly( delayBeforeStimulationMs, TimeUnit.MILLISECONDS ) ;
          OBJECT suppliedValue = null ;
          Throwable suppliedThrowable = null ;
          try {
            switch( values.length ) {
              case 0 :
                suppliedValuesRecorder.add( suppliedValue = callback.supply() ) ;
                break ;
              case 1 :
                callback.call1( values[ 0 ] ) ;
                break ;
              case 2 :
                callback.call2( values[ 0 ], values[ 1 ] ) ;
                break ;
              default :
                throw new IllegalArgumentException( "Unsupported value count: " + values.length ) ;
            }
          } catch( final IllegalArgumentException e ) {
            throw e ;
          } catch( final Throwable throwable ) {
            suppliedThrowablesRecorder.add( suppliedThrowable = throwable ) ;
          }
          LOGGER.info(
              "Consumed " + valuesAsList + " after a delay of " + delayBeforeStimulationMs + " ms" +
              ( suppliedValue == null ? "" : ", supplied value: " + suppliedValue ) +
              ( suppliedThrowable == null ? "" : ", supplied throwable: " + suppliedThrowable ) +
              "."
          ) ;
        } ) ;
      }

      @Override
      public ImmutableList< OBJECT > suppliedValues() {
        return ImmutableList.copyOf( suppliedValuesRecorder ) ;
      }

      @Override
      public ImmutableList< Throwable > suppliedThrowables() {
        return ImmutableList.copyOf( suppliedThrowablesRecorder ) ;
      }

      public void shutdown() {
        executorService.shutdown() ;
        try {
          executorService.awaitTermination( 1, TimeUnit.SECONDS ) ;
        } catch( InterruptedException e ) {
          throw new RuntimeException( e ) ;
        }
      }

    } ;
    stimulators.add( stimulator ) ;
    return stimulator ;
  }

  private static final Value VALUE_A = new Value( "A" ) ;
  private static final Value VALUE_B = new Value( "B" ) ;


  /**
   * Simulates a System Under Test with several levels of callback.
   */
  private static class SomeComponent {

    /**
     * Mimics {@code Consumer< Credential >} in
     * {@code SignonMaterializer#readCredential( Consumer< Credential > )}.
     */
    interface ValueConsumer {
      void consumeValue( Value value ) ;
    }

    /**
     * Mimics {@code SignonMaterializer}.
     */
    interface Subsystem {
      void requestValue( ValueConsumer valueConsumer ) ;
    }

    /**
     * Mimics another subsystem to which the {@link Value} passed to
     * {@link ValueConsumer#consumeValue(Value)} is handed back.
     */
    interface ValueSink {
      void finallyGotValue( Value value ) ;
    }

    private final Subsystem subsystem ;
    private final ValueSink valueSink ;
    private final ThreadFactory threadFactory =
        ExecutorTools.newThreadFactory( SomeComponent.class.getSimpleName() ) ;

    public SomeComponent( final Subsystem subsystem, final ValueSink valueSink ) {
      this.subsystem = checkNotNull( subsystem ) ;
      this.valueSink = checkNotNull( valueSink ) ;
    }

    public void activateSubsystem() {
      threadFactory.newThread(
          () -> subsystem.requestValue( valueSink::finallyGotValue )
      ).start() ;
    }

    @Override
    public String toString() {
      return ToStringTools.nameAndHash( this ) + "{}" ;
    }
  }

  private static final TypeToken< Callback< Value > > CALLBACK_TYPETOKEN =
      new TypeToken< Callback< Value > >() { } ;


}