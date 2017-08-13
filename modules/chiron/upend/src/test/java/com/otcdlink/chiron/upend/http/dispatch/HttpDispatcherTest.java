package com.otcdlink.chiron.upend.http.dispatch;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.otcdlink.chiron.fixture.Monolist;
import com.otcdlink.chiron.testing.NameAwareRunner;
import com.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import com.otcdlink.chiron.upend.http.content.StaticContent;
import com.otcdlink.chiron.upend.http.content.caching.StaticContentCache;
import com.otcdlink.chiron.upend.http.content.caching.StaticContentCacheFactory;
import com.otcdlink.chiron.upend.http.content.file.StaticFileContentProvider;
import com.otcdlink.chiron.upend.http.dispatch.HttpDispatcherFixture.FirstDutyCommandCrafter;
import com.otcdlink.chiron.upend.http.dispatch.HttpDispatcherFixture.SecondDutyCommandCrafter;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import mockit.Injectable;
import mockit.StrictExpectations;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import static com.otcdlink.chiron.upend.http.dispatch.HttpDispatcherFixture.httpRequest;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith( NameAwareRunner.class )
public class HttpDispatcherTest {

  @Test
  public void notFound(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext
  ) {
    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .notFound()
        .redirect( "http://redirection.target" ) // Does nothing but guarantees type propagation.
        .build()
    ;

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/not-here" ) ;

    final Monolist< FullHttpResponse > commandCaptor = new Monolist<>() ;
    new StrictExpectations() {{
      channelHandlerContext.alloc() ;
      result = new UnpooledByteBufAllocator( false, true ) ;
      channelHandlerContext.writeAndFlush( withCapture( commandCaptor ) ) ;
    }} ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    final FullHttpResponse actual = commandCaptor.get() ;
    assertThat( actual.content().toString( Charsets.US_ASCII ) ).contains( "/not-here" ) ;

  }


  @Test
  public void include(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext
  ) {
    final Consumer< HttpDispatcher< ?, ?, Void, Void, ? extends HttpDispatcher > > included =
        httpDispatcher -> httpDispatcher
            .notFound()
            .beginActionContext( FirstDutyCommandCrafter::new )
                .redirect( "" )  // Does nothing but shows that type propagation happens.
            .endActionContext()
    ;
    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .include( included )
        .build()
    ;

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/not-here" ) ;

    final Monolist< FullHttpResponse > commandCaptor = new Monolist<>() ;
    new StrictExpectations() {{
      channelHandlerContext.alloc() ;
      result = new UnpooledByteBufAllocator( false, true ) ;
      channelHandlerContext.writeAndFlush( withCapture( commandCaptor ) ) ;
    }} ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    final FullHttpResponse actual = commandCaptor.get();
    assertThat( actual.content().toString( Charsets.US_ASCII ) ).contains( "/not-here" ) ;

  }


  @Test
  public void actionContext(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext
  ) {
    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .beginActionContext( FirstDutyCommandCrafter::new )
            .notFound()
            .beginActionContext( SecondDutyCommandCrafter::new )
                .notFound()
            .endActionContext()
        .endActionContext()
        .build()
    ;

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/not-here" ) ;

    final Monolist< FullHttpResponse > commandCaptor = new Monolist<>() ;
    new StrictExpectations() {{
      channelHandlerContext.alloc() ;
      result = new UnpooledByteBufAllocator( false, true ) ;
      channelHandlerContext.writeAndFlush( withCapture( commandCaptor ) ) ;
    }} ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    final FullHttpResponse actual = commandCaptor.get() ;
    assertThat( actual.content().toString( Charsets.US_ASCII ) ).contains( "/not-here" ) ;

  }

  @Test
  public < CHANNEL_MEMBER extends ChannelPipeline & ChannelHandler > void file(
      @Injectable final Channel channel,
      @SuppressWarnings( "unused" ) @Injectable
          final CHANNEL_MEMBER channelPipeline,  // Cascading mock needs this mixin.
      @Injectable final ChannelHandlerContext channelHandlerContext,
      @Injectable final StaticFileContentProvider staticFileContentProvider
  ) throws IOException {

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .beginPathSegment( "x" )
          .file( staticFileContentProvider )
        .endPathSegment()
        .build()
    ;

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/x/some-file" ) ;

    final File file = new File( NameAwareRunner.testDirectory(), "some-file" ) ;
    Files.write( "Some content", file, Charsets.US_ASCII ) ;
    final StaticContent.FromFile fileContent = new StaticContent.FromFile(
        file, "some-mime-type") ;


    final Monolist< HttpResponse > commandCaptor = new Monolist<>() ;
    new StrictExpectations() {{
      staticFileContentProvider.fileContent( "some-file" ) ; result = fileContent ;
      channelHandlerContext.write( withCapture( commandCaptor ) ) ;
      channelHandlerContext.pipeline() ;
      channelHandlerContext.newProgressivePromise() ;
    }} ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    final HttpResponse actual = commandCaptor.get() ;
    assertThat( actual.headers().get( HttpHeaderNames.CONTENT_TYPE ) )
        .isEqualTo( "some-mime-type" ) ;

  }


  @Test
  public void resource(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext
  ) throws IOException {
    final StaticContentCache staticContentCache = StaticContentCacheFactory.newCacheWithPreload(
        ImmutableMap.of( "x", "xxx" ),
        ImmutableMap.of( "w/x.x", ByteSource.wrap( "Some content".getBytes( Charsets.US_ASCII ) ) )
    ) ;

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .beginPathSegment( "a/b" )
          .resourceMatch( staticContentCache )
        .endPathSegment()
        .build()
    ;

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/a/b/w/x.x" ) ;

    final Monolist< HttpResponse > commandCaptor = new Monolist<>() ;
    new StrictExpectations() {{
      channelHandlerContext.writeAndFlush( withCapture( commandCaptor ) ) ;
    }} ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    final HttpResponse actual = commandCaptor.get() ;
    assertThat( actual.headers().get( HttpHeaderNames.CONTENT_TYPE ) ).isEqualTo( "xxx" ) ;

  }


// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( BareHttpDispatcherTest.class ) ;

  private final HttpDispatcherFixture fixture = new HttpDispatcherFixture() ;

  private HttpDispatcher< ?, ?, Void, Void, ? extends HttpDispatcher > newDispatcher() {
    return HttpDispatcher.newDispatcher( fixture.designatorFactory ) ;
  }

}