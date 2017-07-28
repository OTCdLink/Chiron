package io.github.otcdlink.chiron.toolbox.internet;

import org.junit.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Hostname}.
 */
public class HostnameTest {

  @Test
  public void goodHostname() throws Hostname.ParseException {
    assertThat( Hostname.parse( "winXpSp2.local" ).asString() ).isEqualTo( "winXpSp2.local" ) ;
    assertThat( Hostname.parse( "somehost" ).asString() ).isEqualTo( "somehost" ) ;
    assertThat( Hostname.parse( "somehost.doma.in" ).asString() ).isEqualTo( "somehost.doma.in" ) ;
    assertThat( Hostname.parse( "10.0.2.2" ).asString() ).isEqualTo( "10.0.2.2" ) ;
  }

  @Test
  public void toInetAddress() throws Exception {
    assertThat( Hostname.parse( "127.0.0.1" ).asInetAddress() )
        .isEqualTo( InetAddress.getByAddress( new byte[] { 127, 0, 0, 1 } ) ) ;
    assertThat( Hostname.parse( "0.0.0.0" ).asInetAddress() )
        .isEqualTo( InetAddress.getByAddress( new byte[] { 0, 0, 0, 0 } ) ) ;
    assertThat( Hostname.parse( "255.199.0.10" ).asInetAddress() )
        .isEqualTo( InetAddress.getByAddress( new byte[] { ( byte ) 255, ( byte ) 199, 0, 10 } ) ) ;
  }

  @Test( expected = EmailAddressFormatException.class )
  public void badHostname() throws EmailAddressFormatException {
    new EmailAddress( "-bad-" ) ;
  }

}
