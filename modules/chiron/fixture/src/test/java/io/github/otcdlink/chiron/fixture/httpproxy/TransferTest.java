package io.github.otcdlink.chiron.fixture.httpproxy;

import com.google.common.base.Charsets;
import io.github.otcdlink.chiron.toolbox.internet.Hostname;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.ScheduledFuture;
import mockit.Injectable;
import org.junit.Test;

import java.net.UnknownHostException;

import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Edge.TARGET;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Watcher.Progress.DELAYED;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Watcher.Progress.EMITTED;
import static io.github.otcdlink.chiron.fixture.httpproxy.HttpProxy.Watcher.Progress.RECEIVED;
import static org.assertj.core.api.Assertions.assertThat;

public class TransferTest {


  @Test
  public void wholeChain( @Injectable final ScheduledFuture< ? > scheduledFuture )
      throws Exception
  {
    final HttpProxy.Transfer.IngressEntry transferAtIngressEntry =
        new HttpProxy.Transfer.IngressEntry( ROUTE, TARGET, true, asByteBuf( "Hello" ) ) ;
    assertThat( transferAtIngressEntry.forwardingRoute() ).isSameAs( ROUTE ) ;
    assertThat( transferAtIngressEntry.onset() ).isEqualTo( TARGET ) ;
    assertThat( transferAtIngressEntry.payloadSize() ).isEqualTo( 5 ) ;
    assertThat( transferAtIngressEntry.payload() ).isEqualTo( new byte[] { 72, 101, 108, 108, 111 } ) ;
    assertThat( transferAtIngressEntry.delayMs() ).isNull() ;
    assertThat( transferAtIngressEntry.progress() ).isEqualTo( RECEIVED ) ;

    final HttpProxy.Transfer.IngressExit transferAtIngressExit = new HttpProxy.Transfer.IngressExit(
        ROUTE, TARGET, true, asByteBuf( "H3llo" ) ) ;
    assertThat( transferAtIngressExit.forwardingRoute() ).isSameAs( ROUTE ) ;
    assertThat( transferAtIngressExit.onset() ).isEqualTo( TARGET ) ;
    assertThat( transferAtIngressExit.payloadSize() ).isEqualTo( 5 ) ;
    assertThat( transferAtIngressExit.payload() ).isEqualTo( byteArray( "H3llo" ) ) ; // No change.
    assertThat( transferAtIngressExit.delayMs() ).isNull() ;

    final HttpProxy.Transfer.EgressExit transferAtEgressExit =
        new HttpProxy.Transfer.EgressExit( ROUTE, TARGET, true, asByteBuf( "HELlo" ) ) ;
    assertThat( transferAtEgressExit.forwardingRoute() ).isSameAs( ROUTE ) ;
    assertThat( transferAtEgressExit.onset() ).isEqualTo( TARGET ) ;
    assertThat( transferAtEgressExit.payloadSize() ).isEqualTo( 5 ) ;
    assertThat( transferAtEgressExit.payload() ).isEqualTo( byteArray( "HELlo" ) ) ;
    assertThat( transferAtEgressExit.delayMs() ).isNull() ;
    assertThat( transferAtEgressExit.progress() ).isEqualTo( EMITTED ) ;

    final HttpProxy.Transfer.Delayed transferDelayed =
        new HttpProxy.Transfer.Delayed<>( scheduledFuture, transferAtIngressExit, 33 ) ;
    assertThat( transferDelayed.forwardingRoute() ).isSameAs( ROUTE ) ;
    assertThat( transferDelayed.onset() ).isEqualTo( TARGET ) ;
    assertThat( transferDelayed.payloadSize() ).isEqualTo( 5 ) ;
    assertThat( transferDelayed.payload() ).isEqualTo( byteArray( "H3llo" ) ) ;
    assertThat( transferDelayed.delayMs() ).isEqualTo( 33 ) ;
    assertThat( transferDelayed.progress() ).isEqualTo( DELAYED ) ;
    assertThat( transferDelayed.toString() ).contains( "33" ) ;


  }

// =======
// Fixture
// =======

  private static final Route ROUTE ;

  static {
    try {
      ROUTE = new Route( Hostname.LOCALHOST.hostPort( 1 ).asInetSocketAddress() );
    } catch( final UnknownHostException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  private static ByteBuf asByteBuf( final String string ) {
    return Unpooled.copiedBuffer( byteArray( string ) );
  }

  private static byte[] byteArray( String string ) {
    return string.getBytes( Charsets.US_ASCII );
  }


}