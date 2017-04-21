package io.github.otcdlink.chiron.toolbox;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scans local TCP ports for getting one available.
 */
public interface TcpPortBooker {

  /**
   * @deprecated kept for sentimental reasons.
   */
  TcpPortBooker OLD_THIS = new TcpPortBooker() {
    private final AtomicInteger counter = new AtomicInteger( LOWEST_PORT ) ;

    @Override
    public int find() {
      while( true ) {
        counter.compareAndSet( HIGHEST_PORT, LOWEST_PORT ) ;
        final ServerSocket serverSocket ;
        try {
          final int port = counter.incrementAndGet() ;
          serverSocket = new ServerSocket( port ) ;
          serverSocket.close() ;
          return port ;
        } catch( final IOException ignore ) { }
      }
    }

  } ;

  TcpPortBooker THIS = OLD_THIS ; // TcpPortBookerByRange.THIS ;


  /**
   * TODO: support a start port and return an {@code Integer} to tell looping or security exception
   * happened.
   */
  int find() ;


  /**
   * Don't use port 1024 which is default for RMI registry.
   * Ports below 10000 are reserved; using them can cause problems in some cases.
   * (Like Firefox which doesn't want to connect to them, maybe annoying for projects
   * reusing that stuff.
   */
  int LOWEST_PORT = 10000 ;

  int HIGHEST_PORT = 65535 ;

}
