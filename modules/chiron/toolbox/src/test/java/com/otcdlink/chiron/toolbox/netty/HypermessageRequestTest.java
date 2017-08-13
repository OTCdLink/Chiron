package com.otcdlink.chiron.toolbox.netty;

import com.google.common.collect.ImmutableMultimap;
import com.otcdlink.chiron.toolbox.UrxTools;
import com.otcdlink.chiron.toolbox.internet.SchemeHostPort;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

public class HypermessageRequestTest {

  @Test
  public void automaticHost() throws Exception {
    final Hypermessage.Request.Get getRequest = new Hypermessage.Request.Get(
        schemeHostPort( "https://foo.bar" ),
        uri( "/whatever" ),
        ImmutableMultimap.of(
            "foo", "bar",
            HttpHeaderNames.HOST.toString(), Hypermessage.Request.MAGIC_HEADER_HOST
        )
    ) ;
    LOGGER.info( "Created " + getRequest + "." ) ;
    assertThat( getRequest.headers ).isEqualTo( ImmutableMultimap.of(
        "foo", "bar",
        "host", "foo.bar:443"
    ) ) ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( HypermessageRequestTest.class ) ;

  private static URI uri( final String uriFragment ) {
    return UrxTools.parseUriQuiet( uriFragment ) ;
  }

  private SchemeHostPort schemeHostPort( final String uri )
      throws SchemeHostPort.ParseException
  {
    return SchemeHostPort.parse( uri ) ;
  }

}