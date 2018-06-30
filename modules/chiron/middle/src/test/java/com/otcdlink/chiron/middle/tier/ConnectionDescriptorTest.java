package com.otcdlink.chiron.middle.tier;

import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionDescriptorTest {

  @Test
  public void serializeAndParse() throws Exception {
    final ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(
        "someVersion",
        true,
        TimeBoundary.newBuilder()
            .pingInterval( 11 )
            .pongTimeoutOnDownend( 12 )
            .reconnectDelay( 13, 14 )
            .pingTimeoutOnUpend( 15 )
            .maximumSessionInactivity( 16 )
            .build()
    ) ;
    final HttpHeaders httpHeaders = connectionDescriptor.httpHeaders() ;

    LOGGER.info(
        "Created " + HttpHeaders.class.getSimpleName() + ": " + httpHeaders.entries() + "." ) ;

    final ConnectionDescriptor deserialized = ConnectionDescriptor.from( httpHeaders ) ;
    assertThat( deserialized.upendVersion ).isEqualTo( "someVersion" ) ;
    assertThat( deserialized.authenticationRequired ).isTrue() ;
    assertThat( deserialized.timeBoundary.pingIntervalMs ).isEqualTo( 11 ) ;
    assertThat( deserialized.timeBoundary.pongTimeoutMs ).isEqualTo( 12 ) ;
    assertThat( deserialized.timeBoundary.reconnectDelayRangeMs.lowerBound ).isEqualTo( 13 ) ;
    assertThat( deserialized.timeBoundary.reconnectDelayRangeMs.upperBound ).isEqualTo( 14 ) ;
    assertThat( deserialized.timeBoundary.pingTimeoutMs ).isEqualTo( 15 ) ;
    assertThat( deserialized.timeBoundary.sessionInactivityMaximumMs ).isEqualTo( 16 ) ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( ConnectionDescriptorTest.class ) ;
}