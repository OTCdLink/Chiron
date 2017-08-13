package com.otcdlink.chiron.upend.intraday;

import com.otcdlink.chiron.buffer.BytebufCoat;
import com.otcdlink.chiron.buffer.BytebufTools;
import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.command.codec.Decoder;
import com.otcdlink.chiron.toolbox.clock.Clock;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;


/**
 * Replays a file written by {@link IntradayFileChannelPersister}.
 *
 * TODO: support multiple {@link Decoder}s for {@link DESIGNATOR} and {@link Command} depending
 * on application version.
 */
public final class IntradayFileReplayer< DESIGNATOR, DUTY >
    implements IntradayReplayer< DESIGNATOR, DUTY >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( IntradayFileReplayer.class ) ;

  /**
   * Need a timestamp for recovered file.
   */
  private final Clock clock ;

  private final byte[] lineEndAsBytes;
  private final File originalFile ;
  private File recoveryFile ;

  private final Decoder< DESIGNATOR > designatorDecoder ;
  private final boolean designatorDecoderFileAware ;
  private final CommandBodyDecoder< DESIGNATOR, DUTY > commandBodyDecoder ;
  private final CommandConsumer< Command< DESIGNATOR, DUTY > > commandConsumer ;
  private final BytebufTools.Coating coating = BytebufTools.threadLocalRecyclableCoating() ;
  private final int expectedSchemaVersion ;

  public IntradayFileReplayer(
      final Clock clock,
      final File originalFile,
      final LineBreak lineBreak,
      final Decoder< DESIGNATOR > designatorDecoder,
      final CommandBodyDecoder< DESIGNATOR, DUTY > commandBodyDecoder,
      final CommandConsumer< Command< DESIGNATOR, DUTY > > commandConsumer,
      final int expectedSchemaVersion
  ){
    this.clock = checkNotNull( clock ) ;
    this.originalFile = checkNotNull( originalFile ) ;
    this.lineEndAsBytes = lineBreak.asByteArray() ;
    this.designatorDecoder = checkNotNull( designatorDecoder ) ;
    this.designatorDecoderFileAware =
        designatorDecoder instanceof FileDesignatorCodecTools.FileAwareDecoder ;
    this.commandBodyDecoder = checkNotNull( commandBodyDecoder ) ;
    this.commandConsumer = checkNotNull( commandConsumer ) ;
    checkArgument( expectedSchemaVersion >= 0 ) ;
    this.expectedSchemaVersion = expectedSchemaVersion ;
  }


  private boolean recoveryFileResolved = false ;

  public boolean searchForRecoveryFile() throws IOException {
    recoveryFileResolved = true ;
    recoveryFile = resolveRecoveryFile() ;
    return recoveryFile != null ;
  }

  public void replayLogFile() throws Exception {

    checkState( recoveryFileResolved && recoveryFile != null ) ;

    LOGGER.info( "Replaying '" + recoveryFile.getAbsolutePath() + "' ..." ) ;

    final long lineCount = new FileSlicer( lineEndAsBytes ) {
      @Override
      protected void onSlice( final ByteBuf sliced, final long sliceIndex ) throws Exception {
        final BytebufCoat coat = coating.coat( sliced ) ;
        if( sliceIndex == 0 ) {
          readFirstLine( coat ) ;
        } else {
          replayLine( coat, sliceIndex + 1 ) ;
        }
        coating.recycle() ;
      }
    }.toSlices( recoveryFile ) ;

    LOGGER.info( "Replay complete, injected " + ( lineCount - 1 ) + " lines into " +
        commandConsumer + "." ) ;
  }

  public void renameRecoveryFile() {
    checkState( recoveryFile != null ) ;
    final File renamed = FileTools.rename( recoveryFile, clock.getCurrentDateTime(),
        "intraday-recovered" ) ;
    LOGGER.info( "Renamed '" + recoveryFile.getAbsolutePath() + "' into '" +
        renamed.getAbsolutePath() + "'." ) ;
    recoveryFile = null ;
  }

  private File resolveRecoveryFile() throws IOException {
    LOGGER.info( "Attempting to replay from '" + originalFile.getAbsolutePath() + "' ..." ) ;
    final File recoveryFile = FileTools.newName(
        originalFile, null, IntradayPersistenceConstants.RECOVERY_FILE_SUFFIX ) ;

    if( recoveryFile.exists() ) {
      LOGGER.info( "Recovery file found: '" + recoveryFile.getAbsolutePath() + "' "
          + "(previous recovery interrupted?)" ) ;
    } else if( originalFile.exists() ) {
      LOGGER.info( "Original file found: '" + originalFile.getAbsolutePath() + "'." ) ;
      if( originalFile.renameTo( recoveryFile ) ) {
        LOGGER.info( "Renamed original file into: '" + recoveryFile.getAbsolutePath() + "'." ) ;
      } else {
        throw new IOException( "Could not rename '" + originalFile.getAbsolutePath() + "' "
            + "into '" + recoveryFile.getAbsolutePath() + "'" ) ;
      }
    } else {
      LOGGER.info( "Found no file to replay from." ) ;
      return null;
    }
    return recoveryFile ;
  }

  private void readFirstLine( final PositionalFieldReader reader ) throws DecodeException {
    final String magic = reader.readDelimitedString() ;
    if( ! "SchemaVersion".equals( magic ) ) {
      throw new IncorrectHeaderException( "Unexpected magic: '" + magic + "'" ) ;
    }
    final String schemaVersion = reader.readDelimitedString() ;
    if( ! ( "" + expectedSchemaVersion ).equals( schemaVersion ) ) {
      throw new IncorrectHeaderException( "Found schema version " + schemaVersion +
          " but was expecting " + expectedSchemaVersion ) ;
    }
    LOGGER.info( "Schema version is correct: '" + schemaVersion + "'." );
    // We don't care about what follows.
  }


  private void replayLine(
      final PositionalFieldReader reader,
      final long lineNumber
  ) throws Exception {
    if( designatorDecoderFileAware ) {
      ( ( FileDesignatorCodecTools.FileAwareDecoder ) designatorDecoder ).lineNumber( lineNumber ) ;
    }
    final DESIGNATOR designator = designatorDecoder.decodeFrom( reader ) ;
    final String commandName = reader.readDelimitedString() ;
    final Command command = ( ( CommandBodyDecoder ) commandBodyDecoder ).decodeBody(
        designator, commandName, reader ) ;
    if( command == null ) {
      throw new Exception( "Failed to resolve '" + commandName + "' at line " + lineNumber + "." ) ;
    }
    commandConsumer.accept( command ) ;
  }



  public static class IncorrectHeaderException extends DecodeException {

    public IncorrectHeaderException( final String message ) {
      super( message ) ;
    }
  }

}
