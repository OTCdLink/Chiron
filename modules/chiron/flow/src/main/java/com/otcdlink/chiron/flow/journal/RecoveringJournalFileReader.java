package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.codec.Decoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class RecoveringJournalFileReader< DESIGNATOR, DUTY >
    extends JournalFileReader< DESIGNATOR, DUTY >
    implements RecoveringJournalReader< Command< DESIGNATOR, DUTY > >
{
  private static final Logger LOGGER =
      LoggerFactory.getLogger( RecoveringJournalFileReader.class ) ;

  private final File recoveryFile ;
  private final File recoveredFile ;
  private Boolean recoveringFileFound = null ;

  public RecoveringJournalFileReader(
      final File journalFile,
      final File recoveryFile,
      final File recoveredFile,
      final Decoder< DESIGNATOR > designatorDecoder,
      final CommandBodyDecoder< DESIGNATOR, DUTY > commandBodyDecoder,
      final int expectedSchemaVersion,
      final boolean recycleSlice
  ) throws FileNotFoundException {
    super(
        journalFile,
        designatorDecoder,
        commandBodyDecoder,
        expectedSchemaVersion
    ) ;
    this.recoveryFile = checkNotNull( recoveryFile ) ;
    this.recoveredFile = checkNotNull( recoveredFile ) ;
  }

  @Override
  public boolean resolveRecoveryFile() throws IOException {
    if( recoveryFile.exists() ) {
      LOGGER.info( "Recovery file found: '" + recoveryFile.getAbsolutePath() + "' -- "
          + "was previous recovery interrupted?" ) ;
      return recoveringFileFound = true ;
    } else if( journalFile.exists() ) {
      LOGGER.info( "Journal file found: '" + journalFile.getAbsolutePath() + "'." ) ;
      if( journalFile.renameTo( recoveryFile ) ) {
        LOGGER.info( "Renamed journal file into: '" + recoveryFile.getAbsolutePath() + "'." ) ;
        return recoveringFileFound = true ;
      } else {
        throw new IOException( "Could not rename '" + journalFile.getAbsolutePath() + "' "
            + "into '" + recoveryFile.getAbsolutePath() + "'" ) ;
      }
    } else {
      LOGGER.info( "Found no recovery file ('" + journalFile.getAbsolutePath() + "' or '" +
          recoveryFile.getAbsolutePath() + "')." ) ;
      return recoveringFileFound = false ;
    }
  }

  @Override
  public void renameRecoveryFileToRecovered() {
    recoveryFile.renameTo( recoveredFile ) ;
    recoveringFileFound = null ;
  }

  private static final Method SEARCH_FOR_RECOVERY_FILE ;
  static {
    try {
      SEARCH_FOR_RECOVERY_FILE = RecoveringJournalReader.class.getDeclaredMethod(
          "resolveRecoveryFile" ) ;
    } catch( NoSuchMethodException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  @Override
  protected File resolveFile() {
    checkState( recoveringFileFound != null, "Must call " +
        SEARCH_FOR_RECOVERY_FILE.getName() + " first" ) ;
    return recoveringFileFound ? recoveryFile : journalFile ;
  }
}
