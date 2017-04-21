package io.github.otcdlink.chiron.upend.intraday;

import io.github.otcdlink.chiron.command.Stamp;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.designator.DesignatorForger;
import io.github.otcdlink.chiron.integration.echo.EchoCodecFixture;
import io.github.otcdlink.chiron.integration.echo.EchoUpwardCommandCrafter;
import io.github.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.shaft.MethodCall;
import io.github.otcdlink.chiron.middle.shaft.MethodCallVerifier;
import io.github.otcdlink.chiron.middle.shaft.MethodCaller;
import io.github.otcdlink.chiron.testing.MethodSupport;
import io.github.otcdlink.chiron.toolbox.clock.UpdateableClock;
import io.github.otcdlink.chiron.toolbox.text.LineBreak;
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
