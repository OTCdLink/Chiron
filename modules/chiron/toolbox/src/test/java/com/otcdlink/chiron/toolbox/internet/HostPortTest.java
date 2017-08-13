package com.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HostPortTest {

  @Test
  public void justParse() throws Exception {
    final HostPort hostPort = HostPort.parse( "foo.bar:80" ) ;
    assertThat( hostPort.hostname.asString() ).isEqualTo( "foo.bar" ) ;
    assertThat( hostPort.port ).isEqualTo( 80 ) ;
    assertThat( hostPort.asString() ).isEqualTo( "foo.bar:80" ) ;
  }

  @Test
  public void parseOrNull() throws Exception {
    assertThat( HostPort.parseOrNull( "missing.port" ) ).isNull() ;
    assertThat( HostPort.parseOrNull( "not@host!name:8888" ) ).isNull() ;
  }

  @Test( expected = HostPort.ParseException.class )
  public void parseThrowsException() throws Exception {
    HostPort.parse( "missing.port" ) ;
  }
}