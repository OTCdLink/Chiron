package com.otcdlink.chiron.upend.intraday;

import com.google.common.base.Charsets;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.testing.MethodSupport;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import io.netty.buffer.ByteBuf;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

public class FileSlicerTest {

  @Test
  public void simpleSlice() throws Exception {
    check( 1024, LineBreak.CR_UNIX, "AB" ) ;
  }

  @Test
  public void emptySlices() throws Exception {
    check( 1024, LineBreak.CR_UNIX, "", "" ) ;
  }

  @Test
  public void noSlice() throws Exception {
    check( 1024, LineBreak.CR_UNIX, ImmutableList.of() ) ;
  }

  @Test
  public void twoSlices() throws Exception {
    check( 1024, LineBreak.CR_UNIX, "AB", "C" ) ;
  }

  @Test
  public void threeSlicesOnTwoPortions() throws Exception {
    check( 4, LineBreak.CR_UNIX, "A", "B", "C" ) ;
  }

  @Test
  public void threeSlicesOnTwoPortionsWithContentOverlap() throws Exception {
    check( 4, LineBreak.CR_UNIX, "A", "BC", "D" ) ;
  }

  @Test
  public void threeSlicesOnTwoPortionsWithDelimiterOverlap() throws Exception {
    check( 4, LineBreak.CRLF_WINDOWS, "AB", "C", "D" ) ;
  }

  @Test
  public void simpleSliceWithMultibyteDelimiter() throws Exception {
    check( 1024, LineBreak.CRLF_WINDOWS, "AB" ) ;
  }

  @Test
  public void twoSlicesWithMultibyteDelimiter() throws Exception {
    check( 1024, LineBreak.CRLF_WINDOWS, "AB", "CD" ) ;
  }

  @Test
  @Ignore( "Takes too long, run manually" )
  public void manySlices() throws Exception {
    final int portionMaximumLength = 20 * 1024 * 1024 ;
    final int sliceCount = /*1_000_000*/ 100_000_000 ;
    check( portionMaximumLength, LineBreak.CR_UNIX, sliceCount ) ;
  }



// =======
// Fixture
// =======


  private static final Logger LOGGER = LoggerFactory.getLogger( FileSlicerTest.class ) ;

  @Rule
  public MethodSupport methodSupport = new MethodSupport() ;

  private void check(
      final int portionMaximumLength,
      final LineBreak lineBreak,
      final String... content
  ) throws IOException {
    check( portionMaximumLength, lineBreak, ImmutableList.copyOf( content ) ) ;
  }

  private void check(
      final int portionMaximumLength,
      final LineBreak lineBreak,
      final ImmutableList< String > content
  ) throws IOException {
    check( portionMaximumLength, lineBreak, content.size(), content, true ) ;
  }

  private void check(
      final int portionMaximumLength,
      final LineBreak lineBreak,
      final long sliceCount
  ) throws IOException {
    check( portionMaximumLength, lineBreak, sliceCount,
        () -> new StringGenerator( sliceCount ), false ) ;
  }

  private void check(
      final int portionMaximumLength,
      final LineBreak lineBreak,
      final long sliceCount,
      final Iterable< String > content,
      final boolean deepAssert
  ) throws IOException {
    final File file = new File( methodSupport.getDirectory(), "file.txt" ) ;
    LOGGER.info( "Creating file '" + file + " ..." ) ;
    try( final FileOutputStream fileOutputStream = new FileOutputStream( file ) ;
         final BufferedOutputStream bufferedOutputStream =
             new BufferedOutputStream( fileOutputStream )
     ) {
      final byte[] lineBreakBytes = lineBreak.asByteArray() ;
      for( final String slice : content ) {
        bufferedOutputStream.write( slice.getBytes( Charsets.US_ASCII ) ) ;
        bufferedOutputStream.write( lineBreakBytes ) ;
      }
    }

    final long fileLength = file.length() ;
    LOGGER.info( "Created file with a size of " + fileLength + " bytes." ) ;

    final long start = System.currentTimeMillis() ;
    final AtomicLong lineCounter = new AtomicLong() ;
    final Iterator< String > contentIterator = content.iterator() ;
    final long officialSliceCount = new FileSlicer( lineBreak.asByteArray(), portionMaximumLength ){
      @Override
      protected void onSlice( final ByteBuf sliced, final long sliceIndex ) throws DecodeException {
        final long expectedSliceIndex = lineCounter.getAndIncrement() ;
        if( deepAssert ) {
          assertThat( sliced.toString( Charsets.US_ASCII ) )
              .isEqualTo( contentIterator.next() ) ;
          assertThat( sliceIndex ).isEqualTo( expectedSliceIndex ) ;
        }
      }
    }.toSlices( file ) ;
    final long end = System.currentTimeMillis() ;

    assertThat( lineCounter.get() ).isEqualTo( sliceCount ) ;
    assertThat( officialSliceCount ).isEqualTo( sliceCount ) ;
    final long duration = end - start ;
    if( duration > 0 ) {
      LOGGER.info( "Processed " + lineCounter.get() + " lines (" + fileLength + " bytes) " +
          "in " + duration + " ms (" +
          ( ( 1000 * sliceCount ) / duration ) + " slice/s, " +
          ( fileLength / ( duration * 1000 ) ) + " MB/s" +
          ")." ) ;
    } else {
      LOGGER.info( "Duration too small for calculating stats." ) ;
    }
  }

  private static class StringGenerator extends AbstractIterator< String > {

    private final long total ;

    private StringGenerator( final long total ) {
      this.total = total ;
    }

    private long index = 0 ;

    @Override
    protected String computeNext() {
      if( index >= total ) {
        return endOfData() ;
      } else {
        return "This string is about 100 bytes long, this is a rough approximation of the size " +
            "of a Proposal Command - " + ( index ++ ) ;
      }
    }
  }

}