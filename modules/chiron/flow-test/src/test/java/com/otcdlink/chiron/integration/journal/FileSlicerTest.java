package com.otcdlink.chiron.integration.journal;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.flow.journal.slicer.FileSlicer;
import com.otcdlink.chiron.flow.journal.slicer.FileSlicerFixture;
import com.otcdlink.chiron.testing.junit5.DirectoryExtension;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class FileSlicerTest {


  @Test
  void simpleSlice() throws Exception {
    check( 1024, 4, LineBreak.CR_UNIX, true, "AB" ) ;
  }

  @Test
  void simpleSliceNoTrailingBreak() throws Exception {
    check( 1024, 4, LineBreak.CR_UNIX, false, "AB" ) ;
  }

  @Test
  void emptySlices() throws Exception {
    check( 1024, 4, LineBreak.CR_UNIX, true, "", "" ) ;
  }

  @Test
  void noSlice() throws Exception {
    check( 1024, 1, LineBreak.CR_UNIX, true, ImmutableList.of() ) ;
  }

  @Test
  void twoSlices() throws Exception {
    check( 1024, 4, LineBreak.CR_UNIX, true, "AB", "C" ) ;
  }

  @Test
  void threeSlicesOnTwoChunks() throws Exception {
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
  void contentSplitOverChunks() throws Exception {
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
  void delimiterSplitOverChunks() throws Exception {
    check( 8, 2, LineBreak.CRLF_WINDOWS, true, "A", "a", "B", "C" ) ;
  }

  @Test
  void simpleSliceWithMultibyteDelimiter() throws Exception {
    check( 1024, 4, LineBreak.CRLF_WINDOWS, true, "AB" ) ;
  }

  @Test
  void twoSlicesWithMultibyteDelimiter() throws Exception {
    check( 1024, 4, LineBreak.CRLF_WINDOWS, true, "AB", "CD" ) ;
  }

  @Test
  void manySlices() throws Exception {
    final int sliceCount = 1_000_000 ;
    FileSlicerFixture.check(
        methodSupport.testDirectory(),
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

  @SuppressWarnings( "WeakerAccess" )
  @RegisterExtension
  final DirectoryExtension methodSupport = new DirectoryExtension() ;

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
        methodSupport.testDirectory(),
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