package io.github.otcdlink.chiron.fixture.tcp;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static java.net.InetSocketAddress.createUnresolved;
import static org.assertj.core.api.Assertions.assertThat;

public class RouteTest {

  /**
   * Check that unresolved addresses behave correctly.
   * @throws Exception
   */
  @Test
  public void testHealth() throws Exception {
    assertThat( ADDRESS_X_1.getHostString() ).isEqualTo( "x" ) ;
  }

  @Test
  public void oneAddress() throws Exception {
    verify( "x:1", ADDRESS_X_1 ) ;
  }

  @Test
  public void unrelatedAddresses() throws Exception {
    verify( "x:1=>y:3", ADDRESS_X_1, ADDRESS_Y_3 ) ;
  }

  @Test
  public void twoAddressesOnTheSameHost() throws Exception {
    verify( "1:x:2", ADDRESS_X_1, ADDRESS_X_2 ) ;
  }

  @Test
  public void twoAddressesOnTheSameHostThenAThird() throws Exception {
    verify( "1:x:2=>y:3", ADDRESS_X_1, ADDRESS_X_2, ADDRESS_Y_3 ) ;
  }

  @Test
  public void someAddressThenTwoOnTheSameHost() throws Exception {
    verify( "x:1=>3:y:4", ADDRESS_X_1, ADDRESS_Y_3, ADDRESS_Y_4 ) ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( RouteTest.class ) ;
  private static final InetSocketAddress ADDRESS_X_1 = createUnresolved( "x", 1 ) ;
  private static final InetSocketAddress ADDRESS_X_2 = createUnresolved( "x", 2 ) ;
  private static final InetSocketAddress ADDRESS_Y_3 = createUnresolved( "y", 3 ) ;
  private static final InetSocketAddress ADDRESS_Y_4 = createUnresolved( "y", 4 ) ;

  private static void verify( final String asString, final InetSocketAddress... addresses ) {
    final ImmutableList< InetSocketAddress > addressList = ImmutableList.copyOf( addresses ) ;
    final Route route = new Route( addresses ) ;
    assertThat( route.asString() )
        .describedAs( "Addresses: " + addressList.toString() )
        .isEqualTo( asString )
    ;
    LOGGER.info( "Successfully verified '" + asString + "'." ) ;
  }
}