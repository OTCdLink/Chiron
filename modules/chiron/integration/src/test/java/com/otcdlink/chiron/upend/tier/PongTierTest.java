package com.otcdlink.chiron.upend.tier;

import com.otcdlink.chiron.fixture.Monolist;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import mockit.Mock;
import mockit.MockUp;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PongTierTest {

  @Test
  public void justPong() throws Exception {
    final EmbeddedChannel channel = createChannel( 1 ) ;
    final PingWebSocketFrame pingWebSocketFrame = new PingWebSocketFrame() ;
    pingWebSocketFrame.content().writeLong( 0 ) ;
    channel.writeInbound( pingWebSocketFrame ) ;
    assertThat( channel.< Object >readOutbound() ).isInstanceOf( PongWebSocketFrame.class ) ;
  }

  @Test
  public void passthrough() throws Exception {
    final EmbeddedChannel channel = createChannel( 1 ) ;
    channel.writeInbound( new TextWebSocketFrame() ) ;
    assertThat( channel.< Object >readInbound() ).isInstanceOf( TextWebSocketFrame.class ) ;
  }

  @Test( timeout = TIMEOUT )
  public < EXECUTOR extends EventExecutor > void timeout() throws Exception {

    final Monolist< Runnable > runnableCapture = new Monolist<>() ;
    final Semaphore schedulingHappenedSemaphore = new Semaphore( 0 ) ;
    new MockUp< EXECUTOR >() {
      @SuppressWarnings( "unused" )
      @Mock
      public ScheduledFuture< ? > schedule(
          final Runnable command,
          final long delay,
          final TimeUnit timeUnit
      ) {
        assertThat( timeUnit.toMillis( delay ) ).isEqualTo( 1 ) ;
        runnableCapture.add( command ) ;
        schedulingHappenedSemaphore.release() ;
        return null ;
      }
    } ;

    LOGGER.info( "Creating " + CHANNEL_CLASS + " that timeouts very soon." ) ;

    final EmbeddedChannel channel = createChannel( 1 ) ;
    schedulingHappenedSemaphore.acquire() ;

    LOGGER.info( "Timeout scheduling happened." ) ;

    runnableCapture.get().run() ;
    assertThat( channel.isOpen() ).isFalse() ;

    LOGGER.info( "Executing timeout callback caused " + CHANNEL_CLASS + " to close." ) ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( SessionEnforcerTierTest.class ) ;
  
  private static EmbeddedChannel createChannel( final int timeoutIfNoPingMs ) {
    return new EmbeddedChannel( new PongTier( timeoutIfNoPingMs ) ) ;
  }

  private static final long TIMEOUT = 1_000 ;
//  private static final long TIMEOUT = 1_000_000 ;

  private static final String CHANNEL_CLASS = Channel.class.getSimpleName() ;

}