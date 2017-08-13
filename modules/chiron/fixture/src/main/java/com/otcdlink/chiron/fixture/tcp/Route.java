package com.otcdlink.chiron.fixture.tcp;

import com.google.common.collect.ImmutableList;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a sequence of {@link InetSocketAddress}es.
 */
public class Route {

  private final ImmutableList< InetSocketAddress > addresses ;

  public Route( final InetSocketAddress... addresses ) {
    this( ImmutableList.copyOf( addresses ) ) ;
  }

  public Route( final ImmutableList< InetSocketAddress > addresses ) {
    checkArgument( ! addresses.isEmpty() ) ;
    this.addresses = addresses ;
  }

  @SuppressWarnings( "unused" )
  public final InetSocketAddress beginning() {
    return addresses.get( 0 ) ;
  }

  public final InetSocketAddress ending() {
    return addresses.get( addresses.size() - 1 ) ;
  }

  @SuppressWarnings( "unused" )
  public final Route prepend(
      final InetSocketAddress inetSocketAddres,
      final InetSocketAddress... more
  ) {
    final List< InetSocketAddress > prepended = new ArrayList<>( more.length + 1 ) ;
    prepended.add( checkNotNull( inetSocketAddres ) ) ;
    Collections.addAll( prepended, more ) ;
    return new Route( ImmutableList.< InetSocketAddress >builder()
        .addAll( prepended )
        .addAll( addresses )
        .build()
    ) ;
  }

  @SuppressWarnings( "unused" )
  public final Route reverse() {
    final List< InetSocketAddress > mutableList = new ArrayList<>( addresses ) ;
    Collections.reverse( addresses ) ;
    return new Route( ImmutableList.copyOf( mutableList ) ) ;
  }

  public final String asString() {
    final StringBuilder stringBuilder = new StringBuilder() ;
    String lastHost = null ;
    for( int i = 0 ; i < addresses.size() ; i ++ ) {

      final InetSocketAddress currentAddress = addresses.get( i ) ;
      final String currentHost = currentAddress.getHostString() ;

      final String nextHost =
          i < addresses.size() - 1 ? addresses.get( i + 1 ).getHostString() : null ;

      if( lastHost != null && ! currentHost.equals( lastHost ) ) {
        stringBuilder.append( "=>" ) ;
      }

      if( currentHost.equals( nextHost ) ) {
        stringBuilder.append( currentAddress.getPort() ).append( ':' ) ;
      } else {
        stringBuilder
            .append( currentHost )
            .append( ':' )
            .append( currentAddress.getPort() ) ;
      }
      lastHost = currentHost ;
    }
    return stringBuilder.toString() ;
  }

  public final ImmutableList< InetSocketAddress > addresses() {
    return addresses ;
  }

  @Override
  public final String toString() {
    return getClass().getSimpleName() + "{" + asString() + "}" ;
  }
}
