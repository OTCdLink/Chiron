package io.github.otcdlink.chiron.toolbox.lifecycle;

import io.github.otcdlink.chiron.toolbox.StringWrapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractLifecycledTest {

  @Test
  public void startAndStop() throws Exception {
    final PrivateLifecycled lifecycled = new PrivateLifecycled( LOGGER ) ;
    lifecycled.setup( PrivateSetup.simple() ) ;
    lifecycled.start().join() ;
    assertThat( lifecycled.stop().get() ).isEqualTo( PrivateCompletion.OK ) ;
  }

  @Test
  public void restart() throws Exception {
    final PrivateLifecycled lifecycled = new PrivateLifecycled( LOGGER ) ;
    lifecycled.setup( PrivateSetup.simple() ) ;
    lifecycled.setup( PrivateSetup.simple() ) ; // Yes, twice.
    lifecycled.start().join() ;
    lifecycled.stop().join() ;
    lifecycled.start().join() ;
    assertThat( lifecycled.stop().get() ).isEqualTo( PrivateCompletion.OK ) ;
  }

  @Test
  public void run() throws Exception {
    final PrivateLifecycled lifecycled = new PrivateLifecycled( LOGGER ) ;
    lifecycled.setup( PrivateSetup.automaticCompletion() ) ;
    assertThat( lifecycled.run().get() ).isEqualTo( PrivateCompletion.OK ) ;
  }

  @Test
  public void badStateTransition() throws Exception {
    final PrivateLifecycled lifecycled = new PrivateLifecycled( LOGGER ) ;
    assertThatThrownBy( lifecycled::start ).isInstanceOf( IllegalStateException.class ) ;
    assertThatThrownBy( lifecycled::stop ).isInstanceOf( IllegalStateException.class ) ;
    lifecycled.setup( PrivateSetup.simple() ) ;
    lifecycled.start().join() ;
    assertThatThrownBy( lifecycled::start ).isInstanceOf( IllegalStateException.class ) ;
    lifecycled.startFuture().join() ;
    lifecycled.stop().join() ;
    assertThatThrownBy( lifecycled::stop ).isInstanceOf( IllegalStateException.class ) ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( AbstractLifecycledTest.class ) ;

  private static final class PrivateSetup {
    public final boolean automaticCompletion ;


    private PrivateSetup( boolean automaticCompletion ) {
      this.automaticCompletion = automaticCompletion ;
    }

    public static PrivateSetup automaticCompletion() {
      return new PrivateSetup( true ) ;
    }

    public static PrivateSetup simple() {
      return new PrivateSetup( false ) ;
    }
  }

  private static final class PrivateCompletion extends StringWrapper< PrivateCompletion > {
    protected PrivateCompletion( String wrapped ) {
      super( wrapped ) ;
    }

    public static final PrivateCompletion OK = new PrivateCompletion( "OK" ) ;
  }

  private static class PrivateLifecycled
      extends AbstractLifecycled< PrivateSetup, PrivateCompletion >
  {

    public PrivateLifecycled( Logger logger ) {
      super( logger ) ;
    }

    @Override
    protected void customStart() throws Exception {
      super.customStart() ;
      if( setup().automaticCompletion ) {
        stop() ;
      }
    }

    @Override
    protected void customStop() throws Exception {
      completion( PrivateCompletion.OK ) ;
      super.customStop() ;
    }
  }

}