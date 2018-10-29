package com.otcdlink.chiron.integration.drill.story;

import com.otcdlink.chiron.fixture.NettyLeakDetectorExtension;
import com.otcdlink.chiron.integration.drill.ConnectorDrill;
import com.otcdlink.chiron.integration.drill.SketchLibrary;
import com.otcdlink.chiron.middle.tier.WebsocketFrameSizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.otcdlink.chiron.integration.drill.SketchLibrary.Interceptor.MAGIC_INTERCEPTED;
import static com.otcdlink.chiron.integration.drill.SketchLibrary.Interceptor.MAGIC_PLEASE_INTERCEPT;
import static com.otcdlink.chiron.integration.drill.SketchLibrary.TAG_TR0;

@ExtendWith( NettyLeakDetectorExtension.class )
class TwoEndStory {

  @Test
  void fragmentAgregation() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .withWebsocketFrameSizer( WebsocketFrameSizer.explicitSizer( 1, 200 ) )
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .forDownendConnector().done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
            .done()
        .build()
    ) {
      drill.play( SketchLibrary.ECHO_ROUNDTRIP ) ;
    }

  }


  @Test
  void upendCommandInterceptor() throws Exception {
    try( final ConnectorDrill drill = ConnectorDrill.newBuilder()
        .withMocksterTimeout( 1, TimeUnit.HOURS )
        .withTimeBoundary( SketchLibrary.PASSIVE_TIME_BOUNDARY )
        .forDownendConnector().done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
            .withCommandInterceptor( SketchLibrary.Interceptor.commandInterceptor( LOGGER ) )
            .done()
        .build()
    ) {
      drill.forSimpleDownend().upwardDuty().requestEcho( TAG_TR0, MAGIC_PLEASE_INTERCEPT ) ;
      drill.forSimpleDownend().downwardDutyMock().echoResponse( TAG_TR0, MAGIC_INTERCEPTED ) ;

    }

  }




// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( TwoEndStory.class ) ;


}
