package io.github.otcdlink.chiron.fixture.tcp;

import io.github.otcdlink.chiron.toolbox.text.ByteArrayEmbellisher;
import io.github.otcdlink.chiron.toolbox.text.Plural;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public final class EchoServer extends BioSocketServer {
  
  private static final Logger LOGGER = LoggerFactory.getLogger( EchoServer.class ) ;

  public static EchoServer newStarted( final InetSocketAddress listenAddress ) {
    try {
      final EchoServer echoServer = new EchoServer( listenAddress ) ;
      echoServer.start() ;
      return echoServer ;
    } catch( final IOException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public EchoServer( final InetSocketAddress listenAddress ) {
    super( listenAddress, EchoServer::respond ) ;
  }

  public EchoServer(
      final InetSocketAddress listenAddress,
      final int socketBacklog,
      final int bufferSize
  ) {
    super( listenAddress, EchoServer::respond, socketBacklog, bufferSize ) ;
  }

  private static final boolean LOG_BYTES = false ;


  private static void respond(
      final byte[] readBuffer,
      final int bytesAvailableInBuffer,
      final OutputStream outputStream
  ) throws IOException {
    if( LOG_BYTES ) {
      final ByteArrayEmbellisher byteArrayEmbellisher =
          new ByteArrayEmbellisher( readBuffer, 0, bytesAvailableInBuffer ) ;
      LOGGER.debug(
          "Echoing " + Plural.bytes( bytesAvailableInBuffer ) + ": " +
              "\n " + byteArrayEmbellisher.hexadecimalString +
              "\n " + byteArrayEmbellisher.humanReadableString
      ) ;
    } else {
      LOGGER.debug( "Echoing " + bytesAvailableInBuffer + " bytes." ) ;
    }
    outputStream.write( readBuffer, 0, bytesAvailableInBuffer ) ;
    outputStream.flush() ;

  }

}
