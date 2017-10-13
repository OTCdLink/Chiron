package com.otcdlink.chiron.upend.tier;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.Files;
import com.otcdlink.chiron.toolbox.TcpPortBooker;
import com.otcdlink.chiron.toolbox.internet.Hostname;
import com.otcdlink.chiron.toolbox.internet.SchemeHostPort;
import com.otcdlink.chiron.toolbox.netty.Hypermessage;
import com.otcdlink.chiron.toolbox.netty.NettyHttpClient;
import com.otcdlink.chiron.toolbox.netty.NettySocketServer;
import com.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import com.otcdlink.chiron.upend.http.dispatch.HttpRequestRelayer;
import com.otcdlink.chiron.upend.http.dispatch.UsualHttpCommands;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpRequestRelayerTierTest {

  @Test
  public void fileUpload() throws Exception {
    try( final NettyHttpClient httpClient = new NettyHttpClient() ;
         final PrivateHttpServer httpServer = new PrivateHttpServer( port )
    ) {
      CompletableFuture.allOf( httpServer.start(), httpClient.start() ).join() ;
      final Hypermessage.FileUploadDefinition fileUploadDefinition =
          new Hypermessage.FileUploadDefinition(
              UPLOAD_PARAMETER_NAME,
              UPLOAD_FILE_NAME,
              uploadedFile
          )
      ;

      httpClient.httpPost(
          schemeHostPort,
          schemeHostPort.uri().resolve( "/fileupload" ),
          null,  // Asks to create header field with correct HOST.
          ImmutableMultimap.of(),
          ImmutableList.of( fileUploadDefinition ),
          clientRecorder
      ) ;
      clientRecorder.nextOutcome() ;

      final RichHttpRequest richHttpRequest = httpRequestCapture.take() ;
      HttpPostRequestDecoder postRequestDecoder = new HttpPostRequestDecoder( richHttpRequest ) ;
      final List< InterfaceHttpData > bodyHttpDatas = postRequestDecoder.getBodyHttpDatas() ;
      assertThat( bodyHttpDatas ).isNotEmpty() ;
      bodyHttpDatas.forEach( data -> {
        FileUpload fileUpload = ( FileUpload ) data ;
        assertThat( fileUpload.getName() ).isEqualTo( UPLOAD_PARAMETER_NAME ) ;
        assertThat( fileUpload.getFilename() ).isEqualTo( UPLOAD_FILE_NAME ) ;
        assertThat( fileUpload.content().toString( Charsets.UTF_8 ) ).isEqualTo( FILE_CONTENT ) ;
      } ) ;
      richHttpRequest.release() ;
    }
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( HttpRequestRelayerTierTest.class ) ;

  private final int port = TcpPortBooker.THIS.find() ;

  private final SchemeHostPort schemeHostPort =
      SchemeHostPort.create( SchemeHostPort.Scheme.HTTP, Hostname.LOCALHOST, port ) ;

  private final BlockingQueue< RichHttpRequest > httpRequestCapture =
      new ArrayBlockingQueue<>( 1 ) ;

  private final File uploadedFile ;
  private static final String FILE_CONTENT = "*** This is the content to upload ***\n" ;
  public HttpRequestRelayerTierTest() throws IOException {
    uploadedFile = File.createTempFile( getClass().getName(), ".txt" ) ;
    Files.write( FILE_CONTENT, uploadedFile, Charsets.UTF_8 ) ;
  }


  private final NettyHttpClient.Recorder clientRecorder = new NettyHttpClient.Recorder() ;

  private final HttpRequestRelayer httpRequestRelayer = ( httpRequest, channelHandlerContext ) -> {
    UsualHttpCommands.Html.outbound( "Handled: " + httpRequest.uri() )
        .outbound( null, httpRequest )
        .feed( channelHandlerContext, false )
    ;
    httpRequest.retain() ;
    httpRequestCapture.add( httpRequest ) ;
    return true ;
  } ;

  private class PrivateHttpServer extends NettySocketServer {

    public PrivateHttpServer( int port ) {
      super( port ) ;
    }

    @Override
    protected void initializeChildChannel( final Channel initiatorChannel ) throws Exception {
      final ChannelPipeline pipeline = initiatorChannel.pipeline() ;
      pipeline.addLast( new HttpServerCodec() ) ;
      pipeline.addLast( new HttpObjectAggregator( 8192 ) ) ;

      // Remove the following line if you don't want automatic content compression.
      ///pipeline.addLast( new HttpContentCompressor() ) ;

      initiatorChannel.pipeline().addLast( new HttpRequestRelayerTier( httpRequestRelayer ) ) ;
    }
  }

  private static final String UPLOAD_PARAMETER_NAME = "upload1" ;
  private static final String UPLOAD_FILE_NAME = "upload.txt" ;


}