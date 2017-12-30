package com.otcdlink.chiron.fixture.tcp;

import com.otcdlink.chiron.toolbox.TcpPortBooker;
import com.otcdlink.chiron.toolbox.internet.LocalAddressTools;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class EchoServerTest {

  @Test
  public void echo() throws Exception {
    try(
        final EchoServer ignored = EchoServer.newStarted( listenAddress ) ;
        final EchoClient echoClient = EchoClient.newStarted( listenAddress )
    ) {
      echoClient.write( "Hello" ) ;
      assertThat( echoClient.read() ).isEqualTo( "Hello" ) ;
    }
  }

  @Test
  public void noServer() throws Exception {
    try {
      EchoClient.newStarted( listenAddress, 1 ) ;
      fail( "No exception thrown" ) ;
    } catch( final SocketTimeoutException e1 ) {
      LOGGER.info( "Caught " + e1 + " as expected." );
    } catch( final RuntimeException e2 ) {
      // This happens when disabling Wi-Fi on Mavericks.
      LOGGER.info( "Caught " + e2 + " as expected." ) ;
      assertThat( e2.getCause() ).isInstanceOf( ConnectException.class ) ;
    }
    ;
  }

  /**
   * Holy crap: you don't know if remote socket has closed until you try to read from it.
   * http://stackoverflow.com/questions/10240694/java-socket-api-how-to-tell-if-a-connection-has-been-closed
   */
  @Test
  public void serverCloses() throws Exception {
    try(
        final EchoServer echoServer = EchoServer.newStarted( listenAddress ) ;
        final EchoClient echoClient = EchoClient.newStarted( listenAddress )
    ) {
      echoClient.write( "Hello" ) ;
      assertThat( echoClient.read() ).isEqualTo( "Hello" ) ;
      echoServer.stop() ;
      assertThat( echoClient.read() ).isNull() ;
    }
  }

  @Test
  public void startAndStop() throws Exception {

    @SuppressWarnings( "IOResourceOpenedButNotSafelyClosed" )
    final EchoServer echoServer = new EchoServer( listenAddress ) ;

    for( int i = 1 ; i <= 3 ; i ++ ) {
      LOGGER.info( "Testing Hello sequence pass " + i + " ..." ) ;
      echoServer.start() ;
      try( final EchoClient echoClient = EchoClient.newStarted( listenAddress ) ) {
        final String hello = "Hello " + i ;
        echoClient.write( hello ) ;
        assertThat( echoClient.read() ).isEqualTo( hello ) ;
      } finally {
        echoServer.stop() ;
      }
    }
  }

  @Test
  public void twoClients() throws Exception {
    try(
        final EchoServer ignored = EchoServer.newStarted( listenAddress ) ;
        final EchoClient echoClient1 = EchoClient.newStarted( listenAddress ) ;
        final EchoClient echoClient2 = EchoClient.newStarted( listenAddress )
    ) {
      echoClient1.write( "Hello1" ) ;
      assertThat( echoClient1.read() ).isEqualTo( "Hello1" ) ;
      echoClient2.write( "Hello2" ) ;
      assertThat( echoClient2.read() ).isEqualTo( "Hello2" ) ;
    }
  }



// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( EchoServerTest.class ) ;

  private final InetSocketAddress listenAddress =
      new InetSocketAddress( LocalAddressTools.LOCAL_ADDRESS, TcpPortBooker.THIS.find() ) ;



}