package com.otcdlink.chiron.fixture.websocket;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.assertj.core.api.AbstractAssert;
import org.slf4j.Logger;

import java.util.Objects;

public class WebSocketFrameAssert<
    ASSERT extends AbstractAssert< ASSERT, WEBSOCKETFRAME >,
    WEBSOCKETFRAME extends WebSocketFrame
    > extends AbstractAssert< ASSERT, WEBSOCKETFRAME >
{
  protected WebSocketFrameAssert( final WEBSOCKETFRAME actual, Class< ASSERT > selfType ) {
    super( actual, selfType ) ;
    if( actual.refCnt() <= 0 ) {
      throw new IllegalArgumentException(
          "Exhausted refCnt for " + actual + ", won't be able to assert" ) ;
    }
  }

  public final ASSERT logTo( final Logger logger ) {
    logger.debug( "Asserting on " + descriptionText() + "." ) ;
    return thisAsAssert() ;
  }

  @SuppressWarnings( "unchecked" )
  protected final ASSERT thisAsAssert() {
    return ( ASSERT ) this ;
  }

  public static TextWebSocketFrameAssert assertThat( final TextWebSocketFrame textWebSocketFrame ) {
    return new TextWebSocketFrameAssert( textWebSocketFrame ) ;
  }

  public static class TextWebSocketFrameAssert
      extends WebSocketFrameAssert< TextWebSocketFrameAssert, TextWebSocketFrame >
  {
    public TextWebSocketFrameAssert( final TextWebSocketFrame actual ) {
      super( actual, TextWebSocketFrameAssert.class ) ;
    }

    @Override
    public String descriptionText() {
      return actual + " \"" + actual.text() + "\"" ;
    }

    public TextWebSocketFrameAssert hasTextContaining( final String fragment ) {
      isNotNull() ;
      if( ! actual.text().contains( fragment ) ) {
        failWithMessage( "Expected text to contain <%s> but found <%s>",
            fragment, actual.text() ) ;
      }
      return this ;
    }

    public TextWebSocketFrameAssert textIsEqualTo( final String text ) {
      isNotNull() ;
      if( ! Objects.equals( actual.text(), text ) ) {
        failWithMessage( "Expected text to be <%s> but found <%s>",
            text, actual.text() ) ;
      }
      return this ;
    }

  }
}
