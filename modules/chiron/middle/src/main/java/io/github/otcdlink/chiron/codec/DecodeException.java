package io.github.otcdlink.chiron.codec;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

public class DecodeException extends IOException {
  public DecodeException( final String message ) {
    super( message );
  }

  public DecodeException( final String message, final Throwable cause ) {
    super( message, cause ) ;
  }

  /**
   * @param byteBuf the thing to dump, using Netty's hex dump.
   */
  public DecodeException( final String message, final Throwable cause, final ByteBuf byteBuf ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  public DecodeException( final String message, final ByteBuf coated ) {
    super( message ) ;
  }
}
