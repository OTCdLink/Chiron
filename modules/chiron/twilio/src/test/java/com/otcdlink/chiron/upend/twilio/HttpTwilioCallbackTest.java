package com.otcdlink.chiron.upend.twilio;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import com.otcdlink.chiron.toolbox.netty.NettySocketServer;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import com.otcdlink.chiron.upend.TimeKit;
import com.otcdlink.chiron.upend.http.dispatch.HttpDispatcher;
import com.otcdlink.chiron.upend.http.dispatch.HttpRequestRelayer;
import com.otcdlink.chiron.upend.tier.HttpRequestRelayerTier;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpTwilioCallbackTest extends AbstractTwilioCallbackTest {

  private static final Logger LOGGER = LoggerFactory.getLogger( HttpTwilioCallbackTest.class ) ;

  private final TimeKit< UpdateableClock > timeKit = TimeKit.instrumentedTimeKit( Stamp.FLOOR ) ;
  private NettySocketServer nettySocketServer = null ;

  @Override
  protected void start(
      final int port,
      final String contextPath,
      final TwilioHttpCallback twilioHttpCallback
  ) throws Exception {

    final HttpRequestRelayer httpRequestRelayer =
        HttpDispatcher.newDispatcher( timeKit.designatorFactory )
        .beginPathSegment( "twilio" )
            .include( new HttpTwilioCallback( twilioHttpCallback ).setup() )
        .endPathSegment()
        .build()
    ;

    nettySocketServer = new NettySocketServer( port ) {
      @Override
      protected void initializeChildChannel( final Channel initiatorChannel ) throws Exception {
        initiatorChannel.pipeline().addLast( new HttpServerCodec() ) ;
        initiatorChannel.pipeline().addLast( new HttpObjectAggregator( 1024 ) ) ;
        initiatorChannel.pipeline().addLast( new HttpRequestRelayerTier( httpRequestRelayer ) ) ;
      }
    } ;

    nettySocketServer.start().join() ;
  }

  @Override
  protected void stop() throws Exception {
    nettySocketServer.stop() ;
    nettySocketServer = null ;
  }

  static {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.info( "=== Netty classes loaded, tests begin here ===" ) ;
  }
}