package com.otcdlink.chiron.upend.http.dispatch;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.RenderingAwareDesignator;
import com.otcdlink.chiron.fixture.Monolist;
import com.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import com.otcdlink.chiron.upend.http.dispatch.HttpDispatcherFixture.SecondDuty;
import com.otcdlink.chiron.upend.http.dispatch.HttpDispatcherFixture.TransientCommandOne;
import com.otcdlink.chiron.upend.http.dispatch.HttpDispatcherFixture.TransientCommandTwo;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.FullVerificationsInOrder;
import mockit.Injectable;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.otcdlink.chiron.upend.http.dispatch.HttpDispatcherFixture.BOOBYTRAPPED_RESOLVER;
import static com.otcdlink.chiron.upend.http.dispatch.HttpDispatcherFixture.NULL_RESOLVER;
import static com.otcdlink.chiron.upend.http.dispatch.HttpDispatcherFixture.alwaysResolveTo;
import static com.otcdlink.chiron.upend.http.dispatch.HttpDispatcherFixture.httpRequest;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BareHttpDispatcherTest {

  /**
   * @deprecated {@link HttpResponder.Resolver} is
   *     deprecated.
   */
  @Test
  public void simpleResolver(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext,
      @Injectable final HttpResponder.Resolver resolver
  ) {

    final RichHttpRequest richHttpRequest = httpRequest( channel, "" ) ;

    final Designator designator = fixture.designatorFactory.internal() ;
    final TransientCommandOne command = new TransientCommandOne( designator, "FOO" ) ;

    final Monolist< EvaluationContext > evaluationContextCaptor = new Monolist<>() ;
    final Monolist<RichHttpRequest> richHttpRequestCaptor =
        new Monolist<>() ;

    new Expectations() {{
      resolver.resolve(
          withCapture( evaluationContextCaptor ),
          withCapture( richHttpRequestCaptor )
      ) ;
      result = command ;
    }} ;

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .action( resolver )
        .build()
    ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    assertThat( evaluationContextCaptor.get() ).isNotNull() ;
    assertThat( evaluationContextCaptor.get().contextPath().isRoot() ).isTrue() ;
    assertThat( richHttpRequestCaptor.get() ).isSameAs( richHttpRequest ) ;

    new FullVerificationsInOrder() {{
      channelHandlerContext.fireChannelRead( command ) ;
    }} ;
  }

  @Test
  public void simpleOutbound(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext,
      @Injectable final HttpResponder.Outbound outbound
  ) {

    final RichHttpRequest richHttpRequest = httpRequest( channel, "" ) ;

    final PipelineFeeder pipelineFeeder = ( chc, keepAlive ) -> {
      final ChannelFuture channelFuture = chc.writeAndFlush( "Done" ) ;
      if( ! keepAlive ) {
        channelFuture.addListener( CLOSE ) ;
      }
    } ;

    final Monolist< EvaluationContext > evaluationContextCaptor = new Monolist<>() ;
    final Monolist<RichHttpRequest> richHttpRequestCaptor =
        new Monolist<>() ;

    new Expectations() {{
      outbound.outbound(
          withCapture( evaluationContextCaptor ),
          withCapture( richHttpRequestCaptor )
      ) ;
      result = pipelineFeeder ;
    }} ;

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .response( outbound )
        .build()
    ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    assertThat( evaluationContextCaptor.get() ).isNotNull() ;
    assertThat( evaluationContextCaptor.get().contextPath().isRoot() ).isTrue() ;
    assertThat( richHttpRequestCaptor.get() ).isSameAs( richHttpRequest ) ;

    new FullVerificationsInOrder() {{
      channelHandlerContext.writeAndFlush( "Done" ) ;
    }} ;
  }

  @Test
  public void simpleDutyCaller(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext,
      @Injectable final HttpResponder.Renderer< Result1 > renderer
  ) {

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/whatever" ) ;

    class CapturingDutyCaller implements HttpResponder.DutyCaller< SecondDuty > {
      @Override
      public FullHttpResponse call(
          final EvaluationContext< SecondDuty > evaluationContext,
          final RichHttpRequest httpRequest
      ) {
        assertThat( httpRequest ).isSameAs( richHttpRequest ) ;
        final Designator designator = evaluationContext.designator() ;
        assertThat( designator ).isInstanceOf( RenderingAwareDesignator.class ) ;
        evaluationContext.duty().stuff2( designator, "Stuff" ) ;
        return null ;
      }
    }

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .beginBareActionContext( HttpDispatcherFixture.FirstDutyCommandCrafter::new )
            .beginBareActionContext( HttpDispatcherFixture.SecondDutyCommandCrafter::new )
                .command( new CapturingDutyCaller(), renderer )
            .endActionContext()
        .endActionContext()
        .build()
    ;

    final Monolist< Command > commandCapture = new Monolist<>() ;
    new Expectations() {{
      channelHandlerContext.fireChannelRead( withCapture( commandCapture ) ) ;
    }} ;
    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    new FullVerifications() {{ }} ;
    final TransientCommandTwo transientCommandTwo = ( TransientCommandTwo ) commandCapture.get() ;
    assertThat( transientCommandTwo.parameter ).isEqualTo( "Stuff" ) ;

    final RenderingAwareDesignator renderingAwareDesignator =
        ( RenderingAwareDesignator ) transientCommandTwo.endpointSpecific ;

    new Expectations() {{
      ( ( BiFunction ) renderer ).apply( any, any ) ;
      result = "Rendered" ;
    }} ;
    final Object rendered = renderingAwareDesignator.renderFrom(
        richHttpRequest, "ValueToRender" ) ;
    new FullVerifications() {{ }} ;
    assertThat( rendered ).isEqualTo( "Rendered" ) ;
  }

  @Test
  public void dutyCallerImmediatelyReportingProblem(
      @Injectable final Channel channel,
      @Injectable final ChannelFuture channelFuture,
      @Injectable final ChannelHandlerContext channelHandlerContext,
      @Injectable final HttpResponder.DutyCaller< SecondDuty > dutyCaller,
      @Injectable final HttpResponder.Renderer< Result1 > renderer
  ) {

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/whatever" ) ;

    final FullHttpResponse immediateResponse = HttpDispatcherFixture.fullHttpResponse(
        HttpResponseStatus.SERVICE_UNAVAILABLE, "Problem happened" ) ;

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .beginBareActionContext( HttpDispatcherFixture.FirstDutyCommandCrafter::new )
            .beginBareActionContext( HttpDispatcherFixture.SecondDutyCommandCrafter::new )
                .command( dutyCaller, renderer )
            .endActionContext()
        .endActionContext()
        .build()
    ;

    new Expectations() {{
      dutyCaller.call(
          ( EvaluationContext< SecondDuty > ) any,
          richHttpRequest
      ) ; result = immediateResponse ;
      channelHandlerContext.writeAndFlush( immediateResponse ) ;
//      channelFuture.addListener(
//          ( GenericFutureListener< ? extends Future< ? super Void > > ) any ) ;
    }} ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    new FullVerifications() {{ }} ;

  }


  @Test
  public void simpleCondition(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext,
      @Injectable final HttpResponder.Condition condition
  ) {

    final RichHttpRequest richHttpRequest = httpRequest( channel, "" ) ;

    final Designator designator = fixture.designatorFactory.internal() ;
    final TransientCommandOne command = new TransientCommandOne( designator, "FOO" ) ;

    final Monolist< EvaluationContext > evaluationContextCaptor = new Monolist<>() ;
    final Monolist<RichHttpRequest> richHttpRequestCaptor =
        new Monolist<>() ;

    new Expectations() {{
      condition.shouldAccept(
          withCapture( evaluationContextCaptor ),
          withCapture( richHttpRequestCaptor )
      ) ;
      result = true ;
    }} ;

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .beginCondition( condition )
            .action( alwaysResolveTo( command ) )
        .endCondition()
        .build()
    ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    assertThat( evaluationContextCaptor.get() ).isNotNull() ;
    assertThat( evaluationContextCaptor.get().contextPath().isRoot() ).isTrue() ;
    assertThat( richHttpRequestCaptor.get() ).isSameAs( richHttpRequest ) ;

    new FullVerifications() {{
      channelHandlerContext.fireChannelRead( command ) ;
    }} ;
  }

  @Test
  public void contextPathAtBuildTime() throws Exception {
    final BareHttpDispatcher httpDispatcher = newDispatcher() ;
    assertThat( httpDispatcher.buildContextPath().isRoot() ).isTrue() ;
    httpDispatcher.beginPathSegment( "x" ) ;
    assertThat( httpDispatcher.buildContextPath().fullPath ).isEqualTo( "/x" ) ;
    httpDispatcher.beginPathSegment( "y" ) ;
    assertThat( httpDispatcher.buildContextPath().fullPath ).isEqualTo( "/x/y" ) ;
    httpDispatcher.endPathSegment() ;
    assertThat( httpDispatcher.buildContextPath().fullPath ).isEqualTo( "/x" ) ;
    httpDispatcher.endPathSegment() ;
    assertThat( httpDispatcher.buildContextPath().isRoot() ).isTrue() ;
  }

  @Test
  public void resolveToNullIfThereIsNoResolver(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext
  ) {

    final RichHttpRequest richHttpRequest = httpRequest( channel, "" ) ;

    final HttpRequestRelayer httpRequestRelayer = newDispatcher().build() ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    new FullVerificationsInOrder() {{ }} ;
  }


  @Test
  public void simplePath(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext
  ) {

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/a/b/0.html" ) ;

    final Designator designator = fixture.designatorFactory.internal() ;
    final TransientCommandOne command = new TransientCommandOne( designator, "FOO" ) ;

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .beginPathSegment( "a" )
            .beginPathSegment( "b" )
                .action( alwaysResolveTo( command ) )
            .endPathSegment()
        .endPathSegment()
        .action( BOOBYTRAPPED_RESOLVER )
        .build()
    ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    new FullVerificationsInOrder() {{
      channelHandlerContext.fireChannelRead( command ) ;
    }} ;
  }


  @Test
  public void simpleConditionNever(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext
  ) {

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/a/b/0.html" ) ;

    final Designator designator = fixture.designatorFactory.internal() ;
    final TransientCommandOne command = new TransientCommandOne( designator, "FOO" ) ;

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .beginCondition( UsualConditions.NEVER )
            .action( BOOBYTRAPPED_RESOLVER )
        .endCondition()
        .action( alwaysResolveTo( command ) )
        .build()
    ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    new FullVerificationsInOrder() {{
      channelHandlerContext.fireChannelRead( command ) ;
    }} ;
  }

  @Test
  public void include(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext
  ) {

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/whatever" ) ;

    final Designator designator = fixture.designatorFactory.internal() ;
    final TransientCommandOne command = new TransientCommandOne( designator, "FOO" ) ;

    final Consumer< BareHttpDispatcher > included = bareHttpDispatcher -> bareHttpDispatcher
        .action( alwaysResolveTo( command ) ) ;

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .include( included )
        .action( BOOBYTRAPPED_RESOLVER )
        .build()
    ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    new FullVerificationsInOrder() {{
      channelHandlerContext.fireChannelRead( command ) ;
    }} ;
  }


  @Test
  public void detectWreckedInclude() {
    final Consumer< BareHttpDispatcher > included = bareHttpDispatcher -> bareHttpDispatcher
        .beginCondition( UsualConditions.ALWAYS ) ;

    assertThatThrownBy( () -> newDispatcher().include( included ) )
        .isInstanceOf( BareHttpDispatcher.ContextException.class )
    ;
  }


  @Test
  public void freeze(
      @Injectable final Channel channel,
      @Injectable final ChannelHandlerContext channelHandlerContext
  ) {

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/whatever" ) ;

    final BareHttpDispatcher< ?, ?, Void, Void, ? > conditionBegun =
        newDispatcher().beginCondition( UsualConditions.ALWAYS ) ;

    final HttpRequestRelayer httpRequestRelayer = conditionBegun
        .endCondition()
        .action( NULL_RESOLVER )
        .build()
    ;

    /** Should have no effect, {@link BareHttpDispatcher#build()} performed a copy. */
    conditionBegun.action( BOOBYTRAPPED_RESOLVER ) ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    new FullVerificationsInOrder() {{ }} ;
  }


  @Test
  public void mismatchedEnding() {
    assertThatThrownBy( () -> newDispatcher()
        .beginPathSegment( "a" )
        .endCondition()
        .build()
    ).isInstanceOf( BareHttpDispatcher.ContextException.class ) ;
  }


  @Test
  public void missingEnding() {
    assertThatThrownBy( () -> newDispatcher()
        .beginPathSegment( "a" )
        .build()
    ).isInstanceOf( BareHttpDispatcher.ContextException.class ) ;
  }

  @Test
  public void dutyCallerThrowsAnException(
      @Injectable final Channel channel,
      @Injectable final ChannelFuture channelFuture,
      @Injectable final ChannelHandlerContext channelHandlerContext,
      @Injectable final HttpResponder.DutyCaller< SecondDuty > dutyCaller,
      @Injectable final HttpResponder.Renderer< Result1 > renderer
  ) {

    final RichHttpRequest richHttpRequest =
        httpRequest( channel, "/whatever" ) ;

    final HttpRequestRelayer httpRequestRelayer = newDispatcher()
        .beginBareActionContext( HttpDispatcherFixture.FirstDutyCommandCrafter::new )
            .beginBareActionContext( HttpDispatcherFixture.SecondDutyCommandCrafter::new )
                .command( dutyCaller, renderer )
            .endActionContext()
        .endActionContext()
        .build()
    ;

    final Monolist< FullHttpResponse > immediateResponseCapture = new Monolist<>() ;

    new Expectations() {{
      dutyCaller.call(
          ( EvaluationContext< SecondDuty > ) any,
          richHttpRequest
      ) ; result = new RuntimeException( "Boom" ) ;
      channelHandlerContext.alloc() ; result = new UnpooledByteBufAllocator( false ) ;
      channelHandlerContext.writeAndFlush( withCapture( immediateResponseCapture ) ) ;
//      channelFuture.addListener(
//          ( GenericFutureListener< ? extends Future< ? super Void > > ) any ) ;
    }} ;

    httpRequestRelayer.relay( richHttpRequest, channelHandlerContext ) ;

    new FullVerifications() {{ }} ;

    final FullHttpResponse fullHttpResponse = immediateResponseCapture.get() ;
    assertThat( fullHttpResponse.status() ).isEqualTo( HttpResponseStatus.INTERNAL_SERVER_ERROR ) ;

  }



// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( BareHttpDispatcherTest.class ) ;

  private final HttpDispatcherFixture fixture = new HttpDispatcherFixture() ;

  private BareHttpDispatcher< ?, ?, Void, Void, ? extends BareHttpDispatcher > newDispatcher() {
    return BareHttpDispatcher.newCrudeHttpDispatcher( fixture.designatorFactory ) ;
  }

  private static final class Result1 { }

}