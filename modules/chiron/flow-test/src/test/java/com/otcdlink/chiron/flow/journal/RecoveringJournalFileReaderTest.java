package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.integration.echo.EchoCodecFixture;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RecoveringJournalFileReaderTest
    extends AbstractJournalFileReaderTest<
        RecoveringJournalFileReader< Designator, EchoUpwardDuty< Designator > >
    >
{

  @Test
  public void resolveExistingJournalFile() throws Exception {
    newJournalReplayFixture().createFile( 2 ) ;
    assertThat( journalFile().length() ).isGreaterThan( 0 ) ;

    final RecoveringJournalFileReader< Designator, EchoUpwardDuty< Designator > > reader =
        newBareReader( journalFile() ) ;
    reader.resolveRecoveryFile() ;
    assertThat( journalFile() ).doesNotExist() ;
    assertThat( recoveryFile().length() ).isGreaterThan( 0 ) ;
    assertThat( recoveredFile() ).doesNotExist() ;

    reader.renameRecoveryFileToRecovered() ;
    assertThat( journalFile() ).doesNotExist() ;
    assertThat( recoveryFile() ).doesNotExist() ;
    assertThat( recoveredFile().length() ).isGreaterThan( 0 ) ;
  }
  @Test
  public void resolveExistingRecoveryFile() throws Exception {
    newJournalReplayFixture( recoveryFile() ).createFile( 2 ) ;
    assertThat( recoveryFile().length() ).isGreaterThan( 0 ) ;

    final RecoveringJournalFileReader< Designator, EchoUpwardDuty< Designator > > reader =
        newBareReader( journalFile() ) ;
    reader.resolveRecoveryFile() ;
    assertThat( journalFile() ).doesNotExist() ;
    assertThat( recoveryFile().length() ).isGreaterThan( 0 ) ;
    assertThat( recoveredFile() ).doesNotExist() ;

    reader.renameRecoveryFileToRecovered() ;
    assertThat( journalFile() ).doesNotExist() ;
    assertThat( recoveryFile() ).doesNotExist() ;
    assertThat( recoveredFile().length() ).isGreaterThan( 0 ) ;
  }

  @Test
  public void needToResolveBeforeIterating() throws Exception {
    assertThatThrownBy( () -> newBareReader( journalFile() ).sliceIterable() )
        .isInstanceOf( IllegalStateException.class ) ;
  }


// =======
// Fixture
// =======

  @Override
  public RecoveringJournalFileReader< Designator, EchoUpwardDuty< Designator > > newJournalReader(
      final File file
  ) throws IOException {

    final RecoveringJournalFileReader< Designator, EchoUpwardDuty< Designator > > reader =
        newBareReader( file ) ;
    reader.resolveRecoveryFile() ;
    return reader ;
  }

  private RecoveringJournalFileReader< Designator, EchoUpwardDuty< Designator > > newBareReader(
      final File file
  ) throws IOException {

    final RecoveringJournalFileReader< Designator, EchoUpwardDuty< Designator > > reader =
        new RecoveringJournalFileReader<>(
            file,
            recoveryFile(),
            recoveredFile(),
            new FileDesignatorCodecTools.InwardDesignatorDecoder(),
            new EchoCodecFixture.PartialUpendDecoder(),
            0,
            false  // TODO: set back to true before committing. Recycling disabled for bug investigation.
        )
    ;
    return reader ;
  }

  private File recoveredFile() {
    return new File( methodSupport.getDirectory(), "my.recovered" ) ;
  }

  private File recoveryFile() {
    return new File( methodSupport.getDirectory(), "my.recovery" ) ;
  }

}