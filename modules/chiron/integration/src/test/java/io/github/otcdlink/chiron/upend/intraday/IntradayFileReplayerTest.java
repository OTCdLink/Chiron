package io.github.otcdlink.chiron.upend.intraday;

import com.google.common.base.Charsets;
import com.google.common.collect.AbstractIterator;
import com.google.common.io.CharSink;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.Stamp;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.designator.DesignatorForger;
import io.github.otcdlink.chiron.integration.echo.EchoCodecFixture;
import io.github.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import io.github.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import io.github.otcdlink.chiron.middle.CommandAssert;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.testing.MethodSupport;
import io.github.otcdlink.chiron.toolbox.clock.UpdateableClock;
import io.github.otcdlink.chiron.toolbox.text.LineBreak;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class IntradayFileReplayerTest {

  @Test
  public void justTwoCommands() throws Exception {
    final ReplayerKit replayerKit = new ReplayerKit( LineBreak.CRLF_WINDOWS ) ;
    replayerKit.createFile( 2 ) ;
    replayerKit.fileReplayer.searchForRecoveryFile() ;
    replayerKit.fileReplayer.replayLogFile() ;

    Assertions.assertThat( replayerKit.commandRecorder ).hasSize( 2 ) ;

    CommandAssert.assertThat( replayerKit.command( 0 ) ).specificFieldsEquivalent( COMMAND_1 ) ;
    CommandAssert.assertThat( replayerKit.command( 1 ) ).specificFieldsEquivalent( COMMAND_2 ) ;

    Assertions.assertThat( Designator.COMMON_FIELDS_COMPARATOR.compare(
        replayerKit.command( 1 ).endpointSpecific, COMMAND_2.endpointSpecific ) )
        .describedAs( "Expected: " + COMMAND_2 + " actual: " + replayerKit.command( 1 ) )
        .isEqualTo( 0 )
    ;

    Assertions.assertThat( replayerKit.command( 1 ).endpointSpecific )
        .isInstanceOf( FileDesignatorCodecTools.DesignatorFromFile.class ) ;
    Assertions.assertThat( replayerKit.command( 1 ).endpointSpecific.kind )
        .isEqualTo( Designator.Kind.UPWARD ) ;
    Assertions.assertThat( replayerKit.command( 1 ).endpointSpecific.lineNumber ).isEqualTo( 3 ) ;


  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( IntradayFileReplayerTest.class ) ;

  @Rule
  public MethodSupport methodSupport = new MethodSupport() ;

  private final UpdateableClock updateableClock =
      new UpdateableClock.Default( Stamp.FLOOR_MILLISECONDS ) ;

  private static final UpwardEchoCommand<Designator> COMMAND_1 = command( 1 ) ;
  private static final UpwardEchoCommand<Designator> COMMAND_2 = command( 2 ) ;


  private static UpwardEchoCommand<Designator> command( final int counter ) {
    return new UpwardEchoCommand<>(
        DesignatorForger.newForger()
            .session( new SessionIdentifier( "7he5e5510n" ) )
            .flooredInstant( 0 )
            .counter( counter )
            .upward()
        ,
        "hello-" + counter
    ) ;
  }

  private class ReplayerKit {
    public final File intradayFile ;
    private final LineBreak lineBreak ;

    public final IntradayFileReplayer<Designator, EchoUpwardDuty<Designator> >
        fileReplayer ;

    public final List< Command<Designator, ? > > commandRecorder = new ArrayList<>() ;

    private ReplayerKit( final LineBreak lineBreak ) {
      this.intradayFile = new File( methodSupport.getDirectory(), "my.intraday" ) ;
      this.lineBreak = checkNotNull( lineBreak ) ;

      fileReplayer = new IntradayFileReplayer<>(
          updateableClock,
          intradayFile,
          lineBreak,
          new FileDesignatorCodecTools.InwardDesignatorDecoder(),
          new EchoCodecFixture.PartialUpendDecoder(),
          commandRecorder::add,
          0
      ) ;

    }

    public UpwardEchoCommand<FileDesignatorCodecTools.DesignatorFromFile> command(
        final int index
    ) {
      return ( UpwardEchoCommand<FileDesignatorCodecTools.DesignatorFromFile> ) ( Object )
          commandRecorder.get( index ) ;
    }

    public void createFile( final int commandCount ) throws IOException {

      final Iterable< String > lines = () -> new AbstractIterator<String>() {
        int counter = 0 ;
        @Override
        protected String computeNext() {
          if( counter == 0 ) {
            counter ++ ;
            return "SchemaVersion 0 ApplicationVersion dev-SNAPSHOT " ;
          } else if( counter <= commandCount ){
            final String line = "0:" + counter + " 7he5e5510n echo hello-" + counter + " " ;
            counter ++ ;
            return line ;
          } else {
            return endOfData() ;
          }
        }
      } ;

      final CharSink charSink = Files.asCharSink(
          intradayFile, Charsets.US_ASCII, FileWriteMode.APPEND ) ;
      charSink.writeLines( lines, lineBreak.asString ) ;

      LOGGER.info( "Wrote " + intradayFile.length() + " bytes into '" +
          intradayFile.getAbsolutePath() + "'." ) ;

    }
  }


}