package io.github.otcdlink.chiron.fixture.tcp;

import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.internet.HostPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Simple socket server with blocking I/Os. Unrelated to organic food.
 */
class BioSocketServer implements Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger( BioSocketServer.class ) ;

  /**
   * Tests rely on this value so don't change mindlessly.
   */
  private static final int DEFAULT_SOCKET_BACKLOG = 10 ;

  /**
   * Tests rely on this value so don't change mindlessly.
   */
  private static final int DEFAULT_BUFFER_SIZE = 10 ;

  private final InetSocketAddress listenAddress ;
  private final int socketBacklog ;
  private final int bufferSize ;

  private final AtomicInteger responderCounter = new AtomicInteger() ;
  private final Set<SinglePeer> singlePeers =
      Collections.synchronizedSet( Collections.newSetFromMap( new IdentityHashMap() ) ) ;

  private final Responder responder ;

  public static BioSocketServer newStarted(
      final InetSocketAddress listenAddress,
      final Responder responder
  ) throws IOException {
    final BioSocketServer bioSocketServer = new BioSocketServer( listenAddress, responder ) ;
    bioSocketServer.start() ;
    return bioSocketServer;
  }

  public BioSocketServer( final InetSocketAddress listenAddress, final Responder responder ) {
    this( listenAddress, responder, DEFAULT_SOCKET_BACKLOG, DEFAULT_BUFFER_SIZE ) ;
  }

  public BioSocketServer(
      final InetSocketAddress listenAddress,
      final Responder responder,
      final int socketBacklog,
      final int bufferSize
  ) {
    this.listenAddress = checkNotNull( listenAddress ) ;
    this.responder = checkNotNull( responder ) ;
    checkArgument( socketBacklog > 0 ) ;
    this.socketBacklog = socketBacklog ;
    checkArgument( bufferSize > 0 ) ;
    this.bufferSize = bufferSize ;
  }

  private ServerSocket serverSocket = null ;
  private Thread acceptorThread = null ;

  public void start() throws IOException {
    checkState( serverSocket == null, "Already started" ) ;
    serverSocket = new ServerSocket(
        listenAddress.getPort(),
        socketBacklog,
        listenAddress.getAddress()
    ) ;
    acceptorThread = new Thread(
        () -> {
          while( ! Thread.currentThread().isInterrupted() ) {
            try {
              final SinglePeer singlePeer =
                  new SinglePeer( serverSocket.accept(), responder, bufferSize ) ;
              singlePeers.add( singlePeer ) ;
              new Thread(
                  singlePeer,
                  SinglePeer.class.getSimpleName() + "-" + responderCounter.getAndIncrement()
              ).start() ;
            } catch( final IOException e ) {
              if( ! Thread.currentThread().isInterrupted() ) {
                LOGGER.error( "Can't open responder socket.", e ) ;
              }
            }
          }
        },
        BioSocketServer.class.getSimpleName() + "-acceptor"
    ) ;
    acceptorThread.start() ;
    LOGGER.info( "Started " + this + "." ) ;
  }

  /**
   * Only for {@code Closeable} support, prefer {@link #stop()} whenever possible.
   */
  @Override
  public void close() throws IOException {
    stop() ;
  }

  public void stop() throws IOException {
    if( serverSocket == null ) {
      LOGGER.warn( "Already stopped " + this + "." ) ;
    } else {
      LOGGER.info( "Stopping " + this + " ..." ) ;
      acceptorThread.interrupt() ;
      serverSocket.close() ;
      serverSocket = null ;
      for( final SinglePeer singlePeer : singlePeers ) {
        singlePeer.closeAll() ;
      }
      LOGGER.info( "Stopped " + this + "." ) ;
    }
  }

  @Override
  public String toString() {
    return
        ToStringTools.getNiceClassName( this ) + "{" +
        HostPort.niceHumanReadableString( listenAddress ) +
        "}"
    ;
  }

  private static class SinglePeer implements Runnable {

    private final Socket socket ;
    private final InputStream inputStream ;
    private final Responder responder ;
    private final OutputStream outputStream ;
    private final byte[] buffer ;
    private final AtomicBoolean running = new AtomicBoolean( true ) ;

    public SinglePeer(
        final Socket socket,
        final Responder responder,
        final int bufferSize
    ) throws IOException {
      this.socket = checkNotNull( socket ) ;
      this.responder = checkNotNull( responder ) ;
      this.inputStream = socket.getInputStream() ;
      this.outputStream = socket.getOutputStream() ;
      buffer = new byte[ bufferSize ] ;
    }

    @Override
    public void run() {
      try {
        LOGGER.info( "Started " + this + "." ) ;
        while( true ) {
          final int read = inputStream.read( buffer ) ;
          if( read > 0 ) {
            responder.respond( buffer, read, outputStream ) ;
          } else {
            break ;
          }
        }
      } catch( final IOException e ) {
        if( running.get() ) {
          LOGGER.error( "Error while reading, closing everything.", e ) ;
        }
        closeAll() ;
      }
    }

    void closeAll() {
      if( running.compareAndSet( true, false ) ) {
        closeQuietly( socket ) ;
        LOGGER.info( "Closed " + this + "." ) ;
      }
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" + socket + "}" ;
    }
  }

  private static void closeQuietly( final Socket socket ) {
    if( socket != null ) {
      try {
        socket.shutdownInput() ;
      } catch( final IOException ignored ) { }
      try {
        socket.shutdownOutput() ;
      } catch( final IOException ignored ) { }
      try {
        socket.close() ;
      } catch( final IOException ignored ) { }
    }
  }

  public interface Responder {
    void respond(
        final byte[] readBuffer,
        final int bytesAvailableInBuffer,
        final OutputStream outputStream
    ) throws IOException ;
  }


}
