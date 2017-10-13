package com.otcdlink.chiron.integration.journal;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.flow.journal.slicer.FileSlicer;
import com.otcdlink.chiron.flow.journal.slicer.FileSlicerFixture;
import com.otcdlink.chiron.testing.MethodSupport;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class FileSlicerTest {


  @Test
  public void simpleSlice() throws Exception {
    check( 1024, 4, LineBreak.CR_UNIX, true, "AB" ) ;
  }

  @Test
  public void simpleSliceNoTrailingBreak() throws Exception {
    check( 1024, 4, LineBreak.CR_UNIX, false, "AB" ) ;
  }

  @Test
  public void emptySlices() throws Exception {
    check( 1024, 4, LineBreak.CR_UNIX, true, "", "" ) ;
  }

  @Test
  public void noSlice() throws Exception {
    check( 1024, 1, LineBreak.CR_UNIX, true, ImmutableList.of() ) ;
  }

  @Test
  public void twoSlices() throws Exception {
    check( 1024, 4, LineBreak.CR_UNIX, true, "AB", "C" ) ;
  }

  @Test
  public void threeSlicesOnTwoChunks() throws Exception {
    check( 6, 2, LineBreak.CR_UNIX, true, "Aa", "Bb", "C" ) ;
  }

  /**
   * <pre>
   chunk: |-----0---------x|
                         |------1------|
   slice: |0|   |-2--|   |-3--|   |2|
   byte:   A  ↵  a  @  ↵  B  C  ↵  D  ↵
   offset: 0  1  2  3  4  5  6  7  8  9
   </pre>
   */
  @Test
  public void contentSplitOverChunks() throws Exception {
    check( 6, 2, LineBreak.CR_UNIX, true, "A", "a@", "BC", "D" ) ;
  }

  /**
   * <pre>
   chunk: |----------0----------x|
                            |-------1--------|
   slice: |0|      |1|      |2|      |3|
   byte:   A  ␍  ␊  a  ␍  ␊  B  ␍  ␊  C  ␍  ␊
   offset: 0  1  2  3  4  5  6  7  8  9  0  1
   </pre>
   */
  @Test
  public void delimiterSplitOverChunks() throws Exception {
    check( 8, 2, LineBreak.CRLF_WINDOWS, true, "A", "a", "B", "C" ) ;
  }

  @Test
  public void simpleSliceWithMultibyteDelimiter() throws Exception {
    check( 1024, 4, LineBreak.CRLF_WINDOWS, true, "AB" ) ;
  }

  @Test
  public void twoSlicesWithMultibyteDelimiter() throws Exception {
    check( 1024, 4, LineBreak.CRLF_WINDOWS, true, "AB", "CD" ) ;
  }

  @Test
  public void manySlices() throws Exception {
    final int sliceCount = 1_000_000 ;
    FileSlicerFixture.check(
        methodSupport.getDirectory(),
        FileSlicer.DEFAULT_CHUNK_MAXIMUM_LENGTH,
        FileSlicer.DEFAULT_SLICE_MAXIMUM_LENGTH,
        LineBreak.CR_UNIX,
        true,
        sliceCount
    ) ;
  }



// =======
// Fixture
// =======


  private static final Logger LOGGER = LoggerFactory.getLogger( FileSlicerTest.class ) ;

  @Rule
  public MethodSupport methodSupport = new MethodSupport() ;

  private void check(
      final int chunkMaximumLength,
      final int sliceMaximumLength,
      final LineBreak lineBreak,
      final boolean lineBreakAtEnd,
      final String... content
  ) throws IOException, InterruptedException {
    check(
        chunkMaximumLength,
        sliceMaximumLength,
        lineBreak,
        lineBreakAtEnd,
        ImmutableList.copyOf( content )
    ) ;
  }

  private void check(
      final int chunkMaximumLength,
      final int sliceMaximumLength,
      final LineBreak lineBreak,
      boolean lineBreakAtEnd,
      final ImmutableList< String > content
  ) throws IOException, InterruptedException {
    FileSlicerFixture.check(
        methodSupport.getDirectory(),
        chunkMaximumLength,
        sliceMaximumLength,
        lineBreak,
        lineBreakAtEnd,
        content.size(),
        content,
        true
    ) ;
  }


  static {
    NettyTools.forceNettyClassesToLoad() ;
  }
}