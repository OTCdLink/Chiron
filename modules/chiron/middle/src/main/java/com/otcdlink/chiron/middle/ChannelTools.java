package com.otcdlink.chiron.middle;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public final class ChannelTools {

  private ChannelTools() { }

  private static final Logger LOGGER = LoggerFactory.getLogger( ChannelTools.class ) ;

  @SuppressWarnings( "unchecked" )
  public static CompletableFuture< ? > concluderFrom( final Future< ? > future ) {
    final CompletableFuture< ? > concluder = new CompletableFuture<>() ;
    future.addListener( ( GenericFutureListener ) f -> {
      if( f.isSuccess() ) {
        concluder.complete( null ) ;
      } else {
        final Throwable cause = f.cause() ;
        LOGGER.error( "Could not start.", cause ) ;
        concluder.completeExceptionally( cause ) ;
      }
    } ) ;
    return concluder ;
  }

  public static final AttributeKey< SessionIdentifier > SESSION_KEY =
      AttributeKey.newInstance( "SESSION_IDENTIFIER" ) ;

  public static boolean hasSession( final ChannelHandlerContext channelHandlerContext ) {
    return sessionIdentifier( channelHandlerContext ) != null ;
  }

  public static SessionIdentifier sessionIdentifier( final ChannelHandlerContext channelHandlerContext ) {
    return channelHandlerContext.attr( SESSION_KEY ).get() ;
  }

  public static void dumpPipeline( final ChannelPipeline pipeline ) {
    dumpPipeline( pipeline, "" ) ;
  }

  public static void dumpPipelineAsynchronously( final ChannelPipeline pipeline ) {
    dumpPipelineAsynchronously( pipeline, "" ) ;
  }

  public static void dumpPipelineAsynchronously(
      final ChannelPipeline pipeline,
      final String message
  ) {
    pipeline.channel().eventLoop().execute( () -> dumpPipeline( pipeline, message ) ) ;
  }

  public static void dumpPipeline( final ChannelPipeline pipeline, final String message ) {
    final StringBuilder stringBuilder = new StringBuilder(
        "Dumping " + ToStringTools.nameAndCompactHash( pipeline ) ) ;
    if( ! Strings.isNullOrEmpty( message ) ) {
      stringBuilder.append( ' ' ) ;
      stringBuilder.append( message ) ;
    }
    stringBuilder.append( ':' ) ;

    pipeline.forEach( entry ->
        stringBuilder.append( "\n  " )
            .append( entry.getKey() ).append( " -> " ).append( entry.getValue() )
    ) ;
    LOGGER.debug( stringBuilder.toString() ) ;
  }

  public static void decorateWithLogging(
      final ChannelPipeline channelPipeline,
      final String handlerName,
      final boolean sideout,
      final boolean sidein
  ) {
    if( sideout ) {
      channelPipeline.addBefore(
          handlerName,
          "logging-sideout-" + handlerName,
          new LoggingHandler( "logging-sideout-" + handlerName )
      ) ;
    }
    if( sidein ) {
      channelPipeline.addAfter(
          handlerName,
          "logging-sidein-" + handlerName,
          new LoggingHandler( "logging-sidein-" + handlerName )
      ) ;
    }
  }

  public static Long extractLongOrNull(
      final Logger logger,
      final WebSocketFrame pingWebSocketFrame
  ) {
    Long decoded = null ;
    try {
      decoded = pingWebSocketFrame.content().readLong() ;
    } catch( final Exception e ) {
      // Logging the stack trace would make to easy to blow the Daemon with some rotten ping.
      logger.error( "Could not parse ping counter. " +
          e.getClass().getName() + ": " + e.getMessage() + "." ) ;
    }
    return decoded ;
  }

  public static class ByteSequenceMatcher {
    private final byte[] bytesToMatch ;

    public ByteSequenceMatcher( final String asciiString ) {
      this.bytesToMatch = asciiString.getBytes( Charsets.US_ASCII ) ;
    }

    public ByteSequenceMatcher( final byte[] bytesToMatch ) {
      this.bytesToMatch = Arrays.copyOf( bytesToMatch, bytesToMatch.length ) ;
    }


    public boolean matchThenSkip( final PositionalFieldReader positionalFieldReader ) {
      try {
        final boolean matches = matches( positionalFieldReader ) ;
        if( matches ) {
          positionalFieldReader.skipBytes( bytesToMatch.length ) ;
        }
        return matches ;
      } catch( final DecodeException e ) {
        throw new RuntimeException( "Should not happen because of range checks", e ) ;
      }
    }

    public boolean matches( final PositionalFieldReader positionalFieldReader )
        throws DecodeException
    {
      if( positionalFieldReader.readableBytes() >= bytesToMatch.length ) {
        for( int index = 0 ; index < bytesToMatch.length ; index ++ ) {
          if( bytesToMatch[ index ] != positionalFieldReader.getByte( index ) ) {
            return false ;
          }
        }
        return true ;
      } else {
        return false ;
      }
    }
  }


}
