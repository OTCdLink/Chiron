package com.otcdlink.chiron.flow.journal;

import com.google.common.base.Charsets;
import com.otcdlink.chiron.buffer.BytebufCoat;
import com.otcdlink.chiron.buffer.BytebufTools;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.codec.Decoder;
import com.otcdlink.chiron.flow.journal.slicer.FileSlicer;
import com.otcdlink.chiron.flow.journal.slicer.Slice;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.SynchronousSink;

import java.io.File;
import java.io.FileNotFoundException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.otcdlink.chiron.flow.journal.slicer.FileSlicer.DEFAULT_CHUNK_MAXIMUM_LENGTH;
import static com.otcdlink.chiron.flow.journal.slicer.FileSlicer.DEFAULT_SLICE_MAXIMUM_LENGTH;
import static com.otcdlink.chiron.flow.journal.slicer.FileSlicer.defaultProbableSliceCountPerChunk;

public class JournalFileReader< DESIGNATOR, DUTY >
    implements JournalReader< Command< DESIGNATOR, DUTY > >
{
  private static final Logger LOGGER = LoggerFactory.getLogger( JournalFileReader.class ) ;

  protected final File journalFile ;
  private final Decoder< DESIGNATOR > designatorDecoder ;
  private final boolean designatorDecoderFileAware ;
  private final CommandBodyDecoder< DESIGNATOR, DUTY > commandBodyDecoder ;
  private final BytebufTools.Coating coating = BytebufTools.threadLocalRecyclableCoating() ;
  private final int expectedSchemaVersion ;
  private final boolean recycleSlice ;
  private FileSlicer fileSlicer ;
  private final LineBreak lineBreak ;

  public JournalFileReader(
      final File journalFile,
      final Decoder< DESIGNATOR > designatorDecoder,
      final CommandBodyDecoder< DESIGNATOR, DUTY > commandBodyDecoder,
      final int expectedSchemaVersion,
      final boolean recycleSlice

  ) throws FileNotFoundException {
    this(
        journalFile,
        designatorDecoder,
        commandBodyDecoder,
        expectedSchemaVersion,
        recycleSlice,
        LineBreak.DEFAULT
    ) ;
  }

  public JournalFileReader(
      final File journalFile,
      final Decoder< DESIGNATOR > designatorDecoder,
      final CommandBodyDecoder< DESIGNATOR, DUTY > commandBodyDecoder,
      final int expectedSchemaVersion,
      final boolean recycleSlice,
      final LineBreak lineBreak
  ) throws FileNotFoundException {
    this.journalFile = checkNotNull( journalFile ) ;
    this.designatorDecoder = checkNotNull( designatorDecoder ) ;
    this.designatorDecoderFileAware =
        designatorDecoder instanceof FileDesignatorCodecTools.FileAwareDecoder ;
    this.commandBodyDecoder = checkNotNull( commandBodyDecoder ) ;
    this.expectedSchemaVersion = expectedSchemaVersion ;
    this.recycleSlice = recycleSlice ;
    this.lineBreak = checkNotNull( lineBreak ) ;
  }

  protected File resolveFile() {
    return journalFile ;
  }

  @Override
  public Iterable< Slice > sliceIterable() {
    if( fileSlicer == null ) {
      try {
        fileSlicer = new FileSlicer(
            resolveFile(),
            lineBreak.asByteArray(),
            DEFAULT_CHUNK_MAXIMUM_LENGTH + 1000,  // Adjusting to Windows line break.
            DEFAULT_SLICE_MAXIMUM_LENGTH,
            defaultProbableSliceCountPerChunk( DEFAULT_CHUNK_MAXIMUM_LENGTH, 100 )
        ) ;
      } catch( FileNotFoundException e ) {
        throw new RuntimeException( e ) ;
      }
    }
    return fileSlicer;
  }

  /**
   * Uses a {@link SynchronousSink} so we may not propagate every {@link Slice}.
   * This is useful for processing the first line which has special metadata.
   */
  @Override
  public void decodeSlice(
      final Slice slice,
      final SynchronousSink< Command< DESIGNATOR, DUTY > > synchronousSink
  ) {
    try {
      if( slice.lineIndexInFile() == 0 ) {
        firstLine( slice ) ;
      } else {
        synchronousSink.next( decode( slice ) ) ;
      }
      if( recycleSlice ) {
        slice.recycle() ;
      }
    } catch( final Exception e ) {
      synchronousSink.error( e ) ;
    }
  }

// =========
// Internals
// =========


  private void firstLine( final Slice slice ) throws Exception {
    LOGGER.warn( "Expecting schema version " + expectedSchemaVersion + " from '" +
        slice.toString( Charsets.US_ASCII ) + "', to be done." ) ;
  }

  private Command< DESIGNATOR, DUTY > decode( final Slice slice ) throws Exception {
    final BytebufCoat coat = coating.coat( slice ) ;
    final Command< DESIGNATOR, DUTY > command ;
    try {
      final DESIGNATOR designator ;
      designator = designatorDecoder.decodeFrom( coat ) ;
      final String commandName = coat.readDelimitedString() ;
      final long lineNumber = slice.lineIndexInFile() + 2 ;
      if( designatorDecoderFileAware ) {
        ( ( FileDesignatorCodecTools.FileAwareDecoder ) designatorDecoder ).lineNumber(
            lineNumber ) ;
      }
      command = commandBodyDecoder.decodeBody( designator, commandName, coat ) ;
      if( command == null ) {
        throw new DecodeException( "Failed to resolve '" + commandName + "' at line " +
            lineNumber + "." ) ;
      }
    } finally {
      coating.recycle() ;
    }
    return command ;
  }

}
