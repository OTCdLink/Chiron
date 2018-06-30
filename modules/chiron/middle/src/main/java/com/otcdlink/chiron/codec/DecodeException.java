package com.otcdlink.chiron.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.io.IOException;

public class DecodeException extends IOException {
  public DecodeException( final String message ) {
    super( message );
  }

  public DecodeException( final String message, final Throwable cause ) {
    super( message, cause ) ;
  }

  public DecodeException( final String message, final Throwable cause, final ByteBuf coated ) {
    super( message( message, coated ), cause ) ;
  }

  public DecodeException( final String message, final ByteBuf coated ) {
    super( message( message, coated ) ) ;
  }

  private static String message( final String message, final ByteBuf coated ) {
    final int readerIndex = coated.readerIndex() ;
    final int writerIndex = coated.writerIndex() ;
    return
        message + "\n" +
        "Reader index: " + readerIndex +
        " 0x" + Integer.toHexString( readerIndex ) + ", " +
        "writer index: " + writerIndex +
        " 0x" + Integer.toHexString( writerIndex ) + ", " +
        "readable bytes: " + coated.readableBytes() + "\n" +
        ByteBufUtil.prettyHexDump( coated ) ;
  }
}
