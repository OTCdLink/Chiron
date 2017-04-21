package io.github.otcdlink.chiron.toolbox.internet;

import com.google.common.base.Equivalence;
import io.github.otcdlink.chiron.toolbox.ToStringTools;

import javax.annotation.ParametersAreNonnullByDefault;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Ties a {@link Hostname} and a port together.
 *
 * <h1>Design notes</h1>
 * <p>
 * This class contains the good Java pattern for such value objects with parsing:
 * <ul>
 *   <li>
 *     Validation made once and once only.
 *   </li><li>
 *     Static factory methods instead of public constructors:
 *     {@link #parse(String)}, {@link #parseOrNull(String)}, and {@code create*}.
 *   </li><li>
 *     Nested {@link ParseException} which is a checked {@code Exception}: parsing is likely
 *     to fail, if failure is welcome, use {@link #parseOrNull(String)}.
 *   </li><li>
 *     Nested {@link CreationException} which is a {@code RuntimeException} for creating a
 *     {@link HostPort} from other objects thought to be valid enough.
 *   </li><li>
 *     {@link #asString()} method for useful payload.
 *   </li><li>
 *     {@link #EQUIVALENCE} for equality and hashing.
 *     (For a {@link Comparable} object we would define a {@code COMPARATOR} using the same
 *     delegation mechanism.)
 *   </li><li>
 *     {@code final} as we don't need to derive it in any way.
 *   </li>
 * </ul>
 *
 * <h1>Naming and Alternatives</h1>
 * <p>
 * References:
 * <a href="https://en.wikipedia.org/wiki/Uniform_Resource_Identifier">URI specification</a>
 * <a href="https://en.wikipedia.org/wiki/Uniform_Resource_Locator">URL specification</a>
 * <p>
 * It's tempting to see this class as URI/URL's Authority but Authority may include credentials.
 */
public final class HostPort {

  public final Hostname hostname ;
  public final int port ;

  private HostPort( final Hostname hostname, final int port ) {
    this.hostname = checkNotNull( hostname ) ;
    checkArgument( port > 0 ) ;
    this.port = port ;
  }

  public static HostPort createForLocalhost( final int port ) {
    return create( Hostname.LOCALHOST, port ) ;
  }

  public static HostPort create( final Hostname hostname, final int port )
      throws CreationException
  {
    if( port <= 0 ) {
      throw new CreationException( "Invalid port: " + port ) ;
    }
    if( hostname == null ) {
      throw new CreationException( "Hostname can't be null" ) ;
    }
    return new HostPort( hostname, port ) ;
  }

  public static HostPort parse( final String string ) throws ParseException {
    final HostPort hostPort = parseOrNull( string ) ;
    if( hostPort == null ) {
      throw new ParseException( "Bad format: '" + string + "'" ) ;
    }
    return hostPort;
  }

  public static HostPort parseOrNull( final String string ) {
    final Matcher matcher = InternetAddressValidator.hostPortMatcher( string ) ;
    if( matcher.matches() ) {
      final Hostname hostname = new Hostname( matcher.group( 1 ), false ) ;
      final int port = Integer.parseInt( matcher.group( 2 ) ) ;
      return new HostPort( hostname, port ) ;
    } else {
      return null ;
    }
  }

  public static HostPort create( final InetSocketAddress inetSocketAddress )
      throws CreationException
  {
    try {
      return new HostPort(
          Hostname.parse( inetSocketAddress.getHostString() ),
          inetSocketAddress.getPort()
      ) ;
    } catch( final Hostname.ParseException e ) {
      throw new CreationException( "Cound not parse '" + inetSocketAddress.getHostName() + "'" ) ;
    }
  }

  public static String niceHumanReadableString( final InetSocketAddress address ) {
    final StringBuilder builder = new StringBuilder() ;
    builder.append( address.getHostName() ) ;
    if( ! address.getHostString().equals( address.getHostName() ) ) {
      builder.append( '/' ) ;
      builder.append( address.getAddress() ) ;
    }
    builder.append( ':' ) ;
    builder.append( address.getPort() ) ;
    return builder.toString() ;
  }


  public InetSocketAddress asInetSocketAddress() throws UnknownHostException {
    return new InetSocketAddress( hostname.asInetAddress(), port ) ;
  }

  public InetSocketAddress asInetSocketAddressQuiet() {
    try {
      return asInetSocketAddress() ;
    } catch( final UnknownHostException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  public static final class CreationException extends RuntimeException {
    public CreationException( final String message ) {
      super( message ) ;
    }
  }

  public static final class ParseException extends Exception {
    public ParseException( final String message ) {
      super( message ) ;
    }
  }

  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + "{" + asString() + "}" ;
  }

  public String asString() {
    return hostname.asString() + ":" + port ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final HostPort that = ( HostPort ) other ;

    return EQUIVALENCE.equivalent( this, that ) ;

  }

  @Override
  public int hashCode() {
    return EQUIVALENCE.hash( this ) ;
  }

  @SuppressWarnings( "WeakerAccess" )
  @ParametersAreNonnullByDefault
  public static final Equivalence< HostPort > EQUIVALENCE = new Equivalence< HostPort >() {
    @Override
    protected boolean doEquivalent( final HostPort first, final HostPort second ) {
      if( first.port == second.port ) {
        if( first.hostname.equals( second.hostname ) ) {
          return true ;
        } else {
          return false ;
        }
      } else {
        return false ;
      }
    }

    @ParametersAreNonnullByDefault
    @Override
    protected int doHash( final HostPort hostPort ) {
      int result = hostPort.hostname.hashCode() ;
      result = 31 * result + hostPort.port ;
      return result ;
    }
  } ;

}
