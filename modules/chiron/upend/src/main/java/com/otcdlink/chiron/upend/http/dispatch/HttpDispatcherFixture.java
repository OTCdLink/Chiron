package com.otcdlink.chiron.upend.http.dispatch;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import com.otcdlink.chiron.upend.TimeKit;
import com.otcdlink.chiron.upend.session.command.TransientCommand;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.net.InetSocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;

class HttpDispatcherFixture {

  public final Designator.Factory designatorFactory =
      TimeKit.instrumentedTimeKit( Stamp.FLOOR ).designatorFactory ;

  private static final InetSocketAddress REMOTE_ADDRESS = new InetSocketAddress(
      "127.0.0.1", 1111 ) ;

  private static final InetSocketAddress LOCAL_ADDRESS = new InetSocketAddress(
      "127.0.0.1", 2222 ) ;

  public static RichHttpRequest httpRequest(
      final Channel channel,
      final String uriPath
  ) {
    return RichHttpRequest.from(
        "http://127.0.0.1" + uriPath, channel, REMOTE_ADDRESS, LOCAL_ADDRESS, false ) ;
  }

  public static FullHttpResponse fullHttpResponse(
      final HttpResponseStatus responseStatus,
      final String htmlBoy
  ) {
    return UsualHttpCommands.newHttpResponse( ByteBufAllocator.DEFAULT, responseStatus, htmlBoy ) ;
  }

  public static HttpResponder.Resolver alwaysResolveTo( final Command command ) {
    return ( evaluationContext, httpRequest ) -> command ;
  }


  public static final HttpResponder.Resolver NULL_RESOLVER =
      new HttpResponder.Resolver() {
        @Override
        public Command resolve( final EvaluationContext Ø, final RichHttpRequest ØØ ) {
          return null ;
        }

        @Override
        public String toString() {
          return ToStringTools.nameAndCompactHash( this ) + "NULL{}" ;
        }
      }
  ;

  public static final HttpResponder.Resolver BOOBYTRAPPED_RESOLVER =
      new HttpResponder.Resolver() {
        @Override
        public Command resolve( final EvaluationContext Ø, final RichHttpRequest ØØ ) {
          throw new BoobyTrapException() ;
        }

        @Override
        public String toString() {
          return ToStringTools.nameAndCompactHash( this ) + "BOOBYTRAPPED{}" ;
        }
      }
      ;

  public static class BoobyTrapException extends RuntimeException { }

// ====
// Duty
// ====

  public interface FirstDuty {
    void stuff1( final Designator designator, final String parameter ) ;
  }

  public static class TransientCommandOne extends TransientCommand< FirstDuty > {

    private final String parameter ;
    public TransientCommandOne( final Designator designator, final String parameter ) {
      super( designator ) ;
      this.parameter = checkNotNull( parameter ) ;
    }

    @Override
    public void callReceiver( final FirstDuty firstDuty ) {
      firstDuty.stuff1( endpointSpecific, parameter ) ;
    }

  }


  public static class FirstDutyCommandCrafter implements FirstDuty {
    private final CommandConsumer< TransientCommand< FirstDuty > > commandConsumer ;

    public FirstDutyCommandCrafter(
        final CommandConsumer< TransientCommand< FirstDuty > > commandConsumer
    ) {
      this.commandConsumer = checkNotNull( commandConsumer ) ;
    }


    @Override
    public void stuff1( final Designator designator, final String parameter ) {
      commandConsumer.accept( new TransientCommandOne( designator, parameter ) ) ;
    }

  }


  public interface SecondDuty {
    void stuff2( final Designator designator, final String parameter ) ;
  }

  public static class SecondDutyCommandCrafter implements SecondDuty {
    private final CommandConsumer< TransientCommand< SecondDuty > > commandConsumer ;

    public SecondDutyCommandCrafter(
        final CommandConsumer< TransientCommand< SecondDuty > > commandConsumer
    ) {
      this.commandConsumer = checkNotNull( commandConsumer ) ;
    }

    @Override
    public void stuff2( final Designator designator, final String parameter ) {
      commandConsumer.accept( new TransientCommandTwo( designator, parameter ) ) ;
    }
  }
  public static class TransientCommandTwo extends TransientCommand< SecondDuty > {

    public final String parameter ;

    public TransientCommandTwo( final Designator designator, final String parameter ) {
      super( designator ) ;
      this.parameter = checkNotNull( parameter ) ;
    }

    @Override
    public void callReceiver( final SecondDuty firstDuty ) {
      firstDuty.stuff2( endpointSpecific, parameter ) ;
    }

  }

}
