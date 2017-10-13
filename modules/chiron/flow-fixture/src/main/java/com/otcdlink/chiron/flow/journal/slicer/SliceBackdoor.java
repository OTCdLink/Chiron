package com.otcdlink.chiron.flow.journal.slicer;

import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.EmptyByteBuf;

import java.util.function.Consumer;


public final class SliceBackdoor {

  private SliceBackdoor() { }

  public static Slice newSlice( long sliceIndexInFile ) {
    return new Slice(
        0,
        1,
        NULL_SLICE_BUFFER_CONSUMER,
        EMPTY_BYTEBUF,
        0,
        0,
        sliceIndexInFile
    ) ;
  }

  public static ImmutableList< Slice > newSlices( final int size ) {
    final ImmutableList.Builder< Slice > builder = ImmutableList.builder() ;
    for( int i = 1 ; i <= size ; i ++ ) {
      builder.add( newSlice( i ) ) ;
    }
    return builder.build() ;
  }

  private static final Consumer<Slice> NULL_SLICE_BUFFER_CONSUMER = Ø -> { } ;

  /**
   * Technically speaking, not immutable, but this will be OK.
   */
  private static final ByteBuf EMPTY_BYTEBUF = new EmptyByteBuf( ByteBufAllocator.DEFAULT ) ;
}
