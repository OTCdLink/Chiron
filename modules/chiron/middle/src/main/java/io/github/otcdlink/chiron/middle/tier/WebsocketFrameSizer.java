package io.github.otcdlink.chiron.middle.tier;

import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Defines how to limit {@link WebSocketFrame} size.
 * Unit is {@code int} because we believe that a maximum value of
 * 2<sup>31</sup> - 1 = {@value Integer#MAX_VALUE} ~= 2 G is enough.
 */
public final class WebsocketFrameSizer {

  /**
   * Frame's payload size as known by
   * {@link WebSocketClientHandshaker#WebSocketClientHandshaker(java.net.URI, io.netty.handler.codec.http.websocketx.WebSocketVersion, java.lang.String, io.netty.handler.codec.http.HttpHeaders, int)}.
   */
  public final int maximumPayloadSize ;

  /**
   * Maximum length of a full reaggregated Frame, as known by
   * {@link io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator}
   */
  public final int maximumAggregatedSize ;

  /**
   * Size of a fragmented Frame as known by {@link WebsocketFragmenterTier}.
   */
  public final int fragmentSize ;

  private WebsocketFrameSizer(
      final int maximumPayloadSize,
      final int maximumAggregatedSize
  ) {
    checkArgument( maximumPayloadSize > 0, "maximumPayloadSize=" + maximumPayloadSize ) ;
    checkArgument( maximumAggregatedSize > 0, "maximumAggregatedSize=" + maximumAggregatedSize ) ;
    final long maximumSingleFrameSize = frameSize( maximumPayloadSize ) ;
    checkArgument(
        maximumAggregatedSize >= maximumSingleFrameSize,
        "maximumPayloadSize=" + maximumPayloadSize + " and " +
        "maximumAggregatedSize=" + maximumAggregatedSize +
        ", the latter is smaller than maximum single frame size which is " + maximumSingleFrameSize
    ) ;
    this.maximumPayloadSize = maximumPayloadSize ;
    this.maximumAggregatedSize = maximumAggregatedSize ;
    checkState( maximumSingleFrameSize <= Integer.MAX_VALUE ) ;
    this.fragmentSize = ( int ) maximumSingleFrameSize ;
  }

  public static WebsocketFrameSizer explicitSizer(
      final int maximumPayloadSize,
      final int maximumAggregatedSize
  ) {
    return new WebsocketFrameSizer( maximumPayloadSize, maximumAggregatedSize ) ;
  }

  public static WebsocketFrameSizer sizerByRepetition(
      final int maximumPayloadSize,
      final int times
  ) {
    checkArgument( times > 0 ) ;
    final long maximumSingleFrameSize = frameSize( maximumPayloadSize ) ;
    final long maximumAggregatedFrameSize = maximumSingleFrameSize * times ;
    checkArgument( maximumAggregatedFrameSize <= Integer.MAX_VALUE ) ;
    return new WebsocketFrameSizer( maximumPayloadSize, ( int ) maximumAggregatedFrameSize ) ;
  }

  public static WebsocketFrameSizer tightSizer( final int maximumPayloadSize ) {
    return sizerByRepetition( maximumPayloadSize, 1 ) ;
  }

  /**
   * Determines the length of a frame, depending on its payload, because the header
   * has an incremental encoding to describe payload length.
   * <p>
   * <a href='https://tools.ietf.org/html/rfc6455#page-28' >RFC 6455</a> says:
   * <quote>Payload length:  7 bits, 7+16 bits, or 7+64 bits</quote>.
   * <ul> <li>
   *   A "Payload length" header on 7      bits means a maximum length of 125.
   *   </li><li>
   *   A "Payload length" header on 7 + 16 bits means a maximum length of 32,768.
   *   </li><li>
   *   A "Payload length" header on 7 + 64 bits means a maximum length of 9,223,372,036,854,775,807.
   * </li></ul>
   * <p>
   * We could pass a parameter to tell if masking is activated.
   */
  public static long frameSize( final long payloadSize ) {
    if( payloadSize < 126 ) {
      return payloadSize + MINIMUM_HEADER_SIZE ;
    } else if( payloadSize <= Short.MAX_VALUE ) {
      return payloadSize + 2 + MINIMUM_HEADER_SIZE ;
    } else {
      return payloadSize + 8 + MINIMUM_HEADER_SIZE ;
    }
  }

  public static long payloadSize( final long frameSize ) {
    checkArgument( frameSize > MINIMUM_HEADER_SIZE,
        "Frame size too small for containing smallest possible header: " + frameSize ) ;

    final int maximumFrameLengthForOneByteSizeField = 126 + MINIMUM_HEADER_SIZE ;
    if( frameSize < maximumFrameLengthForOneByteSizeField ) {
      return frameSize - MINIMUM_HEADER_SIZE ;
    } else if( frameSize <= Short.MAX_VALUE + MINIMUM_HEADER_SIZE ) {
      return frameSize - MINIMUM_HEADER_SIZE - 2 ;
    } else {
      return frameSize - MINIMUM_HEADER_SIZE - 8 ;
    }
  }

  public static int payloadSizeInt( final long frameSize ) {
    final long payloadSizeLong = payloadSize( frameSize ) ;
    checkArgument( payloadSizeLong <= Integer.MAX_VALUE ) ;
    return ( int ) payloadSizeLong ;
  }


  public static int frameSizeInt( final long payloadSize ) {
    final long frameSizeLong = frameSize( payloadSize ) ;
    checkArgument( frameSizeLong <= Integer.MAX_VALUE ) ;
    return ( int ) frameSizeLong;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' +
        "maximumPayloadSize=" + maximumPayloadSize + ';' +
        "maximumAggregatedSize=" + maximumAggregatedSize + ';' +
        "fragmentSize=" + fragmentSize +
        '}'
    ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final WebsocketFrameSizer that = ( WebsocketFrameSizer ) other ;

    if( maximumPayloadSize != that.maximumPayloadSize ) {
      return false ;
    }
    if( maximumAggregatedSize != that.maximumAggregatedSize ) {
      return false ;
    }
    return fragmentSize == that.fragmentSize ;

  }

  @Override
  public int hashCode() {
    int result = maximumPayloadSize ;
    result = 31 * result + maximumAggregatedSize ;
    result = 31 * result + fragmentSize ;
    return result ;
  }

  /**
   * The minimum size of a WebSocket Frame header (for the smallest payload),
   * including a Masking Key.
   */
  static final int MINIMUM_HEADER_SIZE = 6 ;

}
