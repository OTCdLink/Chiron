package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.integration.echo.EchoCodecFixture;
import com.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import com.otcdlink.chiron.middle.CommandAssert;
import com.otcdlink.chiron.testing.junit5.DirectoryExtension;
import com.otcdlink.chiron.toolbox.clock.UpdateableClock;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

class JournalFileReaderTest2 {

  @Test
  void justTwoCommands() throws Exception {
    final File intradayFile = new File( methodSupport.testDirectory(), "my.intraday" ) ;

    final LineBreak lineBreakWindows = LineBreak.CRLF_WINDOWS ;
    final JournalReplayFixture journalReplayFixture = new JournalReplayFixture(
        LOGGER, intradayFile, lineBreakWindows ) ;

    journalReplayFixture.createFile( 2 ) ;

    JournalReplayFixture.replayInto(
        intradayFile,
        new FileDesignatorCodecTools.InwardDesignatorDecoder(),
        new EchoCodecFixture.PartialUpendDecoder(),
        journalReplayFixture.commandRecorder::add,
        lineBreakWindows
    ) ;

    Assertions.assertThat( journalReplayFixture.commandRecorder ).hasSize( 2 ) ;

    CommandAssert.assertThat(
        journalReplayFixture.command( 0 ) ).specificFieldsEquivalent( COMMAND_1 ) ;

    CommandAssert.assertThat(
        journalReplayFixture.command( 1 ) ).specificFieldsEquivalent( COMMAND_2 ) ;

    Assertions.assertThat( Designator.COMMON_FIELDS_COMPARATOR.compare(
        journalReplayFixture.command( 1 ).endpointSpecific, COMMAND_2.endpointSpecific ) )
        .describedAs( "Expected: " + COMMAND_2 + " actual: " + journalReplayFixture.command( 1 ) )
        .isEqualTo( 0 )
    ;

    Assertions.assertThat( journalReplayFixture.command( 1 ).endpointSpecific )
        .isInstanceOf( FileDesignatorCodecTools.DesignatorFromFile.class ) ;

    Assertions.assertThat( journalReplayFixture.command( 1 ).endpointSpecific.kind )
        .isEqualTo( Designator.Kind.UPWARD ) ;

    Assertions.assertThat( journalReplayFixture.command( 1 ).endpointSpecific.lineNumber )
        .isEqualTo( 3 ) ;


  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( JournalFileReaderTest2.class ) ;

  @SuppressWarnings( "WeakerAccess" )
  @RegisterExtension
  final DirectoryExtension methodSupport = new DirectoryExtension() ;

  private final UpdateableClock updateableClock =
      new UpdateableClock.Default( Stamp.FLOOR_MILLISECONDS ) ;

  private static final UpwardEchoCommand< Designator > COMMAND_1 =
      JournalReplayFixture.newCommand( 1 ) ;
  private static final UpwardEchoCommand< Designator > COMMAND_2 =
      JournalReplayFixture.newCommand( 2 ) ;


}