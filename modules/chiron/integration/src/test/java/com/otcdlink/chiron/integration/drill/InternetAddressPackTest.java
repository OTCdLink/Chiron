package com.otcdlink.chiron.integration.drill;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class InternetAddressPackTest {

  @Test
  public void creation() {
    final String path = "/websocket" ;
    final InternetAddressPack internetAddressPack = InternetAddressPack.newOnLocalhost(
        true, 999, path, 998 ) ;
    LOGGER.info( "Created " + internetAddressPack + "." ) ;
    final String uriAsString = "https://localhost:999/websocket" ;
    final String wsUrlAsString = "wss://localhost:999/websocket" ;
    assertThat( internetAddressPack.upendWebSocketUriWithHttpScheme().toASCIIString() )
        .isEqualTo( uriAsString ) ;
    assertThat( internetAddressPack.upendWebSocketUrlWithWsScheme().toExternalForm() )
        .isEqualTo( wsUrlAsString ) ;
    assertThat( internetAddressPack.upendWebSocketUri().toASCIIString() ).isEqualTo( path ) ;
    assertThat( internetAddressPack.internetProxyAccess().hostPort.port ).isEqualTo( 998 ) ;
  }


// =======
// Fixture
// =======

  private final Logger LOGGER = LoggerFactory.getLogger( InternetAddressPackTest.class ) ;

}