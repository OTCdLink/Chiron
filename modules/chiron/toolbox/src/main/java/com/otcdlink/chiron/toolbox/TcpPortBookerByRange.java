package com.otcdlink.chiron.toolbox;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Finds available TCP ports for opening server sockets, with a cooperative algorithm which works with other
 * processes using the same algorithm with same parameters.
 */
public class TcpPortBookerByRange implements TcpPortBooker {

  private static final Logger LOGGER = LoggerFactory.getLogger( TcpPortBookerByRange.class ) ;

  private final Object lock = new Object() ;
  private boolean open = true ;
  private final int firstUsablePort ;
  private final int lastUsablePort ;
  private final int rangeSize ;
  private int nextAttempt ;
  private final List< Bookable > sentinels = Lists.newLinkedList() ;


  public static final TcpPortBooker THIS = new TcpPortBookerByRange() ;

  /**
   * Constructor with defaults, including termination at JVM shutdown.
   */
  public TcpPortBookerByRange() {
    this( TcpPortBooker.LOWEST_PORT, TcpPortBooker.HIGHEST_PORT, 10 ) ;
    registerForTerminationAtShutdown( this ) ;
  }

  public TcpPortBookerByRange(
      final int firstUsablePort,
      final int lastUsablePort,
      final int rangeSize
  ) {
    checkArgument( firstUsablePort > 0,
        "Inconrrect value of %s for first usable port, should be > 0", firstUsablePort ) ;
    checkArgument(
        lastUsablePort > firstUsablePort,
        "Inconrrect value of %s for last usable port, should be > %s",
        lastUsablePort,
        firstUsablePort
    ) ;
    final int maximumRange = lastUsablePort - firstUsablePort + 1 ;
    checkArgument( rangeSize > 1, "Inconrrect value of %s for range, should be > 1", rangeSize ) ;
    checkArgument( rangeSize <= maximumRange,
        "Inconrrect value of %s for range, should be <= %s", rangeSize, maximumRange ) ;
    this.firstUsablePort = firstUsablePort ;
    this.lastUsablePort = lastUsablePort ;
    this.rangeSize = rangeSize ;
    this.nextAttempt = firstUsablePort ;

    LOGGER.info( "Created " + this + "." ) ;
  }

  @Override
  public String toString() {
    return
        getClass().getSimpleName() + "{" +
        "first=" + firstUsablePort + ";" +
        "last=" + lastUsablePort + ";" +
        "range=" + rangeSize +
        "}"
    ;
  }

  /**
   * Tries to find an available TCP port.
   *
   * @return the number of a TCP port that was available at the time of this method call.
   * @throws UnexpectedOpenPortException if the port was available during range booking,
   *     and it's no longer the case when calling this method.
   * @throws NoMorePortAvailableException after exhausting all ranges up to last usable port.
   */
  @Override
  public final int find() {
    synchronized( lock ) {
      Preconditions.checkState( open ) ;

      while( true ) {
        if( nextAttempt > lastUsablePort ) {
          throw new NoMorePortAvailableException( lastUsablePort ) ;
        }
        if( isSentinel( nextAttempt ) ) {
          final Bookable sentinel = tryBookingRange( nextAttempt ) ;
          if( sentinel == null ) {
            nextAttempt += rangeSize ;
          } else {
            sentinels.add( sentinel ) ;
            nextAttempt = sentinel.number() + 1 ;
            LOGGER.debug( "Booked range from " + sentinel.number() + " to " +
                ( sentinel.number() + rangeSize - 1 ) + "." ) ;
          }
        } else {
          if( tryOpen( createBookable( nextAttempt ) ) ) {
            return nextAttempt ++ ;
          } else {
            throw new UnexpectedOpenPortException( nextAttempt ) ;
          }
        }
      }
    }
  }

  private boolean isSentinel( final int port ) {
    final int shift = firstUsablePort % rangeSize ;
    return ( port - shift ) % rangeSize == 0 ;
  }


  /**
   * Tries to open all the {@link Bookable} inside a given range; in case of success it returns
   * the "sentinel" which stays open, or {@code null} if couldn't open some {@link Bookable}.
   *
   * @return a possibly null object.
   */
  private Bookable tryBookingRange( final int first ) {
    if( first + rangeSize - 1 > lastUsablePort ) {
      return null ;
    }

    final List< Bookable > range = Lists.newArrayList() ;
    final int last = first + rangeSize - 1 ;
    boolean success = true ;
    try {
      for( int i = first ; i <= last ; i ++ ) {
        final Bookable bookable = createBookable( i ) ;
        if( bookable.open() ) {
          range.add( bookable ) ;
        } else {
          success = false ;
          break ;
        }
      }
      return success ? range.get( 0 ) : null ;
    } finally {
      if( success ) {
        // Don't close the sentinel port.
        range.remove( 0 ) ;
      }
      // Make sockets available, booked or not.
      Collections.reverse( range ) ; // Make other bookers fail slightly faster for this range.
      for( final Bookable bookable : range ) {
        bookable.close() ;
      }
    }
  }



  public void terminate() {
    synchronized( lock ) {
      open = false ;
      LOGGER.debug( "Terminating, closing those sentinels: " + sentinels + " ..." ) ;
      for( final Bookable sentinel : sentinels ) {
        sentinel.close() ;
      }
      sentinels.clear() ;
    }
  }

  public static void registerForTerminationAtShutdown( final TcpPortBookerByRange booker ) {
    Runtime.getRuntime().addShutdownHook( new Thread(
        booker::terminate,
        TcpPortBookerByRange.class.getSimpleName() + "-termination"
    ) ) ;
  }

  /**
   * Tries to open, and immediately closes.
   */
  public static boolean tryOpen( final Bookable bookable ) {
    if( bookable.isOpen() ) {
      return false ;
    }
    final boolean didOpen = bookable.open() ;
    if( didOpen ) {
      bookable.close() ;
    }
    return didOpen ;
  }



// ==========
// Exceptions
// ==========

  public static class NoMorePortAvailableException extends RuntimeException {
    public NoMorePortAvailableException( final int lastUsablePort ) {
      super( "Already scanned all ranges up to " + lastUsablePort ) ;
    }
  }

  public static class UnexpectedOpenPortException extends RuntimeException {
    public UnexpectedOpenPortException( final int lastUsablePort ) {
      super( "Port " + lastUsablePort + " was available at a time but it's no longer the case " ) ;
    }
  }


// ==============
// Bookable stuff
// ==============

  protected Bookable createBookable( final int port ) {
    return new SocketBookable( port ) ;
  }


  /**
   * Makes tests faster and easier to write than using plain sockets.
   */
  protected interface Bookable {
    int number() ;

    /**
     * Returns if the port is open.
     */
    boolean isOpen() ;
    /**
     * Tries to open, and doesn't close after.
     */
    boolean open() ;

    /**
     * Does nothing if already closed.
     */
    void close() ;
  }

  protected static class SocketBookable implements Bookable {
    private final int number ;
    private ServerSocket socket = null ;

    public SocketBookable( final int number ) {
      this.number = number ;
    }

    @Override
    public int number() {
      return number ;
    }

    @Override
    public boolean isOpen() {
      return socket != null ;
    }

    @Override
    public boolean open() {
      try {
        socket = new ServerSocket( number ) ;
      } catch( IOException e ) {
        socket = null ;
      }
      return isOpen() ;
    }

    @Override
    public void close() {
      if( socket != null ) {
        try {
          socket.close() ;
        } catch( IOException e ) {
          throw new RuntimeException( "Should not happen", e ) ;
        } finally {
          socket = null ;
        }
      }
    }
  }

}
