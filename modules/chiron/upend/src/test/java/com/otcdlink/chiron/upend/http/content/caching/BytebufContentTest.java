package com.otcdlink.chiron.upend.http.content.caching;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BytebufContentTest {

  @Test
  public void reuse() throws Exception {
    final ByteBuf initialBytebuf = Unpooled.buffer() ;
    final String happyContent = "HappyContent" ;
    ByteBufUtil.writeAscii( initialBytebuf, happyContent ) ;
    final ByteBuf readOnlyBytebuf = Unpooled.unmodifiableBuffer( initialBytebuf ) ;
    final BytebufContent bytebufContent = new BytebufContent( readOnlyBytebuf, "mimetype/some" ) ;

    final ByteBuf first = bytebufContent.bytebuf() ;
    first.readByte() ;
    final int readableBytes1 = first.readableBytes() ;

    final ByteBuf second = bytebufContent.bytebuf() ;
    second.readByte() ;
    final int readableBytes2 = second.readableBytes() ;

    assertThat( readableBytes1 )
        .describedAs( "We should read each buffer with no side-effect" )
        .isEqualTo( readableBytes2 ) ;

  }
}