package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.integration.echo.EchoCodecFixture;
import com.otcdlink.chiron.integration.echo.EchoUpwardCommandCrafter;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.middle.shaft.MethodCall;
import com.otcdlink.chiron.middle.shaft.MethodCallVerifier;
import com.otcdlink.chiron.middle.shaft.MethodCaller;
import com.otcdlink.chiron.testing.MethodSupport;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersisterReplayerTest {
  @Test
  public void justOneCommand() throws Exception {
    final IntradayFileShaft<
        Designator,
        EchoUpwardDuty< Designator >
    > intradayFileShaft = new IntradayFileShaft<>(
        clock,
        methodSupport.getDirectory(),
        EchoUpwardCommandCrafter::new,
        new FileDesignatorCodecTools.InwardDesignatorCodec(),
        new EchoCodecFixture.PartialUpendDecoder(),
        LineBreak.CR_UNIX
    ) ;
    intradayFileShaft.submit( METHOD_CALLER, VERIFIER ) ;
  }

// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( PersisterReplayerTest.class ) ;

  @Rule
  public final MethodSupport methodSupport = new MethodSupport() ;

  private final UpdateableClock clock = UpdateableClock.newClock( Stamp.FLOOR_MILLISECONDS ) ;


  private static final DesignatorForger.CounterStep FORGER = DesignatorForger.newForger()
      .session( new SessionIdentifier( "7he5e5510n" ) )
      .flooredInstant( 0 )
  ;
  private static final Designator DESIGNATOR_0 = FORGER.counter( 0 ).upward() ;
  private static final Designator DESIGNATOR_1 = FORGER.counter( 0 ).upward() ;

  private static final MethodCaller< EchoUpwardDuty< Designator > > METHOD_CALLER =
      new MethodCaller.Default< EchoUpwardDuty< Designator > >() {
        @Override
        public void callMethods( final EchoUpwardDuty< Designator > duty ) {
          duty.requestEcho( DESIGNATOR_0, "Hello 1" ) ;
          duty.requestEcho( DESIGNATOR_1, "Hello 2" ) ;
        }
      }
  ;

  private static final MethodCallVerifier VERIFIER = new MethodCallVerifier.Skipping( 0 ) {
    @Override
    public void verifyEquivalence( final MethodCall expected, final MethodCall actual ) {
      final Designator expectedDesignator = ( Designator ) expected.parameters.get( 0 ) ;
      final Designator actualDesignator = ( Designator ) actual.parameters.get( 0 ) ;
      Designator.COMMON_FIELDS_COMPARATOR.compare( expectedDesignator, actualDesignator ) ;
      super.verifyEquivalence( expected, actual ) ;
    }
  } ;

}
