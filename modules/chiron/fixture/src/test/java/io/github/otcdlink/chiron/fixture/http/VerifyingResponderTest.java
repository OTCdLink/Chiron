package io.github.otcdlink.chiron.fixture.http;

import com.google.common.collect.ImmutableMultimap;
import io.github.otcdlink.chiron.toolbox.UrxTools;
import io.github.otcdlink.chiron.toolbox.netty.Hypermessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VerifyingResponderTest {

  @Test( timeout = TIMEOUT )
  public void justRespond() throws Exception {
    responder.plan( GET_0, RESPONSE_0 ) ;
    assertThat( responder.respondTo( GET_0 ) ).isEqualTo( RESPONSE_0 ) ;
    responder.waitForCompletion() ;
  }

  @Test( timeout = TIMEOUT )
  public void reallyWait() throws Exception {
    final Semaphore waitComplete = new Semaphore( 0 ) ;
    new Thread(
        () -> {
          try {
            LOGGER.info( "Starting to wait for completion ..." ) ;
            responder.waitForCompletion() ;
            LOGGER.info( "Completion reached." ) ;
            waitComplete.release() ;
          } catch( VerifyingResponder.ResponderException e ) {
            LOGGER.error( "Should not happen", e ) ;
          }
        },
        "waitingForCompletion"
    ).start() ;
    responder.plan( GET_0, RESPONSE_0 ) ;
    assertThat( waitComplete.availablePermits() ).isEqualTo( 0 ) ;
    LOGGER.info( "Request-response planned, still waiting ..." );
    LOGGER.info( "Now requesting to respond ..." );
    responder.respondTo( GET_0 ) ;
    waitComplete.acquire() ;
    LOGGER.info( "Completion detected." ) ;
  }

  @Test( timeout = TIMEOUT )
  public void planExhausted() throws Exception {
    responder.respondTo( GET_0 ) ;
    assertThatThrownBy( responder::waitForCompletion )
        .isInstanceOf( VerifyingResponder.PlanExhaustedException.class )
    ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( VerifyingResponderTest.class ) ;
  private static final long TIMEOUT = 1000 ;

  private final VerifyingResponder responder = new VerifyingResponder() ;

  private static final Hypermessage.Request.Get GET_0 =
      new Hypermessage.Request.Get( UrxTools.parseUrlQuiet( "http://localhost" ) ) ;

  private static final Hypermessage.Response RESPONSE_0 =
      new Hypermessage.Response( HttpResponseStatus.OK, ImmutableMultimap.of(), "" ) ;



}