package io.github.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PartialHostAccessTest {

  @Test
  public void parse() throws Exception {
    final PartialHostAccess partialHostAccess =
        PartialHostAccess.parse( "otcdlink@trader.otcdlink.com", 22 ) ;
    assertThat( partialHostAccess.getHostname().asString() ).isEqualTo( "trader.otcdlink.com" ) ;
    assertThat( partialHostAccess.getPort() ).isEqualTo( 22 ) ;
    assertThat( partialHostAccess.getLogin() ).isEqualTo( "otcdlink" ) ;
  }
}