package io.github.otcdlink.chiron.fixture.tcp;

import com.google.common.base.Charsets;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import io.github.otcdlink.chiron.toolbox.text.Plural;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class EchoClient implements Closeable, AsciiStringReader {

  private static final Logger LOGGER = LoggerFactory.getLogger( EchoClient.class ) ;

  private static final Charset CHARSET = Charsets.US_ASCII ;

  public final InetSocketAddress remoteAddress ;

  private final int connectTimeoutMs ;

  private final AtomicLong writeTotal = new AtomicLong( 0 ) ;
  private final AtomicLong readTotal = new AtomicLong( 0 ) ;

  /**
   * Start with infinite connect timeout.
   */
  public static EchoClient newStarted( final InetSocketAddress remoteAddress ) {
    try {
      return newStarted( remoteAddress, 0 ) ;
    } catch( final SocketTimeoutException e ) {
      throw new RuntimeException( "Should not happen", e ) ;
    }
  }

  public static EchoClient newStarted(
      final InetSocketAddress remoteAddress,
      final int connectTimeoutMs
  ) throws SocketTimeoutException {
    final EchoClient echoClient = new EchoClient( remoteAddress, connectTimeoutMs ) ;
    try {
      echoClient.start() ;
    } catch( final SocketTimeoutException e ) {
      throw e ;
    } catch( final IOException e ) {
      throw new RuntimeException( e ) ;
    }
    return echoClient ;
  }

  /**
   *
   * @param connectTimeoutMs zero means infinite timeout.
   */
  public EchoClient( final InetSocketAddress remoteAddress, final int connectTimeoutMs ) {
    this.remoteAddress = checkNotNull( remoteAddress ) ;
    checkArgument( connectTimeoutMs >= 0 ) ;
    this.connectTimeoutMs = connectTimeoutMs ;
  }

  private Socket socket = null ;

  public void start() throws IOException {
    checkState( socket == null, "Already started" ) ;
    writeTotal.set( 0 ) ;
    readTotal.set( 0 ) ;
    socket = new Socket() ;
    socket.connect( remoteAddress, connectTimeoutMs ) ;
    LOGGER.info( "Started " + this + " with " + socket + "." ) ;
  }

  public void write( final String string ) throws IOException {
    write( string.getBytes( CHARSET ) ) ;
  }

  public void write( final byte[] bytes ) throws IOException {
    socket.getOutputStream().write( bytes ) ;
    socket.getOutputStream().flush() ;
    LOGGER.debug(
        "Wrote and flushed " + Plural.bytes( bytes.length ) + " from " + this + " " +
        "(total so far: " + Plural.bytes( writeTotal.addAndGet( bytes.length ) ) + ")."
    ) ;
  }

  public String read() throws IOException {
    final byte[] read = read( 1000 ) ;
    return read == null ? null : new String( read, CHARSET ) ;
  }

  public byte[] read( final int maximumBytes ) throws IOException {
    final byte[] buffer = new byte[ maximumBytes ] ;
    final int readCount = read( buffer ) ;
    if( readCount > 0 ) {
      final byte[] read = new byte[ readCount ] ;
      System.arraycopy( buffer, 0, read, 0, readCount ) ;
      return read ;
    } else {
      return null ;
    }
  }

  public int read( final byte[] readBuffer ) throws IOException {
    final int readCount = socket.getInputStream().read( readBuffer ) ;
    if( readCount > 0 ) {
      LOGGER.debug( "Read " + Plural.bytes( readCount ) + " from " + this + " (total so far: " +
          Plural.bytes( readTotal.addAndGet( readCount ) ) + ")." ) ;
    }
    return readCount ;
  }


  /**
   * Only for {@code Closeable} support, prefer {@link #stop()} whenever possible.
   */
  @Override
  public void close() throws IOException {
    stop() ;
  }

  public static void stopQuiet( final EchoClient echoClient ) {
    if( echoClient != null ) {
      try {
        echoClient.stop() ;
      } catch( final IOException e ) {
        LOGGER.debug( "Failed to stop " + echoClient + ".", e ) ;
      }
    }
  }

  public void stop() throws IOException {
    if( socket == null ) {
      LOGGER.warn( "Already stopped " + this + "." ) ;
    } else {
      try {
        socket.close() ;
      } finally {
        socket = null ;
        LOGGER.info(
            "Stopped " + this + " " +
            "(wrote " + Plural.bytes( writeTotal.get() ) + " at all, " +
            "read " + readTotal.get() + ")."
        ) ;
      }
    }
  }

  @Override
  public String toString() {
    return
        ToStringTools.nameAndCompactHash( this ) + "{" +
        HostPort.niceHumanReadableString( remoteAddress ) +
        "}"
    ;
  }


}
