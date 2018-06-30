package com.otcdlink.chiron.upend.tier;

import com.otcdlink.chiron.fixture.Monolist;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.ScheduledFuture;
import mockit.Mock;
import mockit.MockUp;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PongTierTest {

  @Test
  public void justPong() {
    final EmbeddedChannel channel = createChannel( 1000 ) ;
    final PingWebSocketFrame pingWebSocketFrame = new PingWebSocketFrame() ;
    pingWebSocketFrame.content().writeLong( 0 ) ;
    channel.writeInbound( pingWebSocketFrame ) ;
    assertThat( channel.< Object >readOutbound() ).isInstanceOf( PongWebSocketFrame.class ) ;
  }

  @Test
  public void passthrough() {
    final PongTier pongTier = new PongTier( 1000 ) ;
    final EmbeddedChannel channel = new EmbeddedChannel( pongTier ) ;
    assertThat( channel.isOpen() ).isTrue() ;
    channel.writeInbound( new TextWebSocketFrame() ) ;
    assertThat( channel.< Object >readInbound() ).isInstanceOf( TextWebSocketFrame.class ) ;
  }


//  @Ignore( "Broken by Netty 4.1.24.Final, EmbeddedChannel doesn't seem to register ChannelHandlers properly" )
  @Test( /*timeout = TIMEOUT*/ )
  public < EXECUTOR extends ScheduledExecutorService > void timeout() throws Exception {

    final int timeout = 100 ;
    final Monolist< Runnable > runnableCapture = new Monolist<>() ;
    final Semaphore schedulingHappenedSemaphore = new Semaphore( 0 ) ;

    new MockUp< EXECUTOR >() {
      /**
       * Same as {@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit)}
       */
      @SuppressWarnings( "unused" )
      @Mock
      public ScheduledFuture< ? > schedule(
          final Runnable command,
          final long delay,
          final TimeUnit timeUnit
      ) {
        LOGGER.info( "Scheduling timeout in the mock ..." ) ;
        assertThat( timeUnit.toMillis( delay ) ).isEqualTo( timeout ) ;
        runnableCapture.add( command ) ;
        schedulingHappenedSemaphore.release() ;
        return null ;
      }
    } ;

    LOGGER.info( "Creating " + CHANNEL_CLASS + " that timeouts very soon." ) ;

    final EmbeddedChannel channel = new EmbeddedChannel( new PongTier( timeout ) ) ;

    assertThat( channel.isOpen() ).isTrue() ;

//    Executors.newSingleThreadScheduledExecutor().schedule( () -> {}, 1, TimeUnit.MILLISECONDS ) ;
    schedulingHappenedSemaphore.acquire() ;

    LOGGER.info( "Timeout scheduling happened." ) ;

    runnableCapture.get().run() ;
    LOGGER.info( "Executed timeout callback caused " + CHANNEL_CLASS + " to close." ) ;
    assertThat( channel.isOpen() ).isFalse() ;

  }



// =======
// Fixture
// =======

  private static final Logger LOGGER =
      LoggerFactory.getLogger( SessionEnforcerTierTest.class ) ;
  
  private static EmbeddedChannel createChannel( final int timeoutIfNoPingMs ) {
    final PongTier pongTier = new PongTier( timeoutIfNoPingMs ) ;
    final EmbeddedChannel channel = new EmbeddedChannel( pongTier ) ;

    // Required by some obscure bug appeared between Netty 4.1.10.Final and 4.1.24.Final.
//    channel.pipeline().addFirst( pongTier ) ;

    assertThat( channel.isOpen() ).isTrue() ;
    return channel ;
  }

  private static final long TIMEOUT = 1_000 ;
//  private static final long TIMEOUT = 1_000_000 ;

  private static final String CHANNEL_CLASS = Channel.class.getSimpleName() ;

}