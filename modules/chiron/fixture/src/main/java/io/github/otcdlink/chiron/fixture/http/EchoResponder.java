package io.github.otcdlink.chiron.fixture.http;

import com.google.common.collect.ImmutableMultimap;
import io.github.otcdlink.chiron.toolbox.netty.Hypermessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoResponder implements TinyHttpServer.Responder {

  private static final Logger LOGGER = LoggerFactory.getLogger( EchoResponder.class ) ;

  @Override
  public Hypermessage.Response respondTo( final Hypermessage.Request request ) {
    final Hypermessage.Response response = new Hypermessage.Response(
        HttpResponseStatus.OK,
        ImmutableMultimap.of(),
        "Echoed '" + request.uri + "'."
    ) ;
    LOGGER.info( "Responding to " + request + " with " + response + "." ) ;
    return response ;
  }
}
