package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import com.otcdlink.chiron.testing.MethodSupport;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import com.otcdlink.chiron.toolbox.netty.NettyTools;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

public abstract class AbstractJournalFileReaderTest<
    READER extends JournalFileReader< Designator, EchoUpwardDuty< Designator > >
> {

  @Test
  public void reread() throws Exception {
    newJournalReplayFixture().createFile( 2 ) ;
    final READER journalReader = newJournalReader( journalFile() ) ;
    iterateAndVerify( journalReader ) ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger(
      AbstractJournalFileReaderTest.class ) ;

  @Rule
  public final MethodSupport methodSupport = new MethodSupport() ;

  protected abstract READER newJournalReader( File file ) throws IOException;


  private Scheduler scheduler( final String role ) {
    return Schedulers.fromExecutor(
        Executors.newSingleThreadExecutor( ExecutorTools.newThreadFactory( role ) ) );
  }

  protected final File journalFile() {
    return new File( methodSupport.getDirectory(), "my.journal" ) ;
  }


  protected final JournalReplayFixture newJournalReplayFixture() {
    final File journalFile = journalFile() ;
    return newJournalReplayFixture( journalFile ) ;
  }

  protected final JournalReplayFixture newJournalReplayFixture( final File journalFile ) {
    return new JournalReplayFixture(
        LOGGER,
        journalFile,
        LineBreak.CR_UNIX
    ) ;
  }

  protected final void iterateAndVerify( READER journalReader ) {
    final Flux< Command< Designator, EchoUpwardDuty< Designator > > > flux = Flux
        .fromIterable( journalReader.sliceIterable() )
        .publishOn( scheduler( "deserialize" ) )
        .handle( journalReader::decodeSlice )
        .doOnNext( command -> LOGGER.info( "Got " + command + "." ) )
    ;

    StepVerifier
        .create( flux )
        .expectNextMatches( verifyCommand( 1 ) )
        .expectNextMatches( verifyCommand( 2 ) )
        .expectComplete()
        .verify()
    ;
  }



  private static Predicate< Command< Designator, EchoUpwardDuty< Designator > > > verifyCommand(
      final int counter
  ) {
    return command -> {
      if( ! ( command instanceof UpwardEchoCommand ) ) {
        LOGGER.error( "Not an instance of " + UpwardEchoCommand.class + ": " + command ) ;
        return false ;
      }
      final String expectedMessage = "hello-" + counter ;
      if( ! expectedMessage.equals( ( ( UpwardEchoCommand ) command ).message ) ) {
        LOGGER.error( "Does not contain expected message '" + expectedMessage + "': " + command ) ;
        return false ;
      }
      if( ! ( command.endpointSpecific instanceof FileDesignatorCodecTools.DesignatorFromFile ) ) {
        LOGGER.error( "EndpointSpecific not an instance of " +
            FileDesignatorCodecTools.DesignatorFromFile.class.getSimpleName() + ": " + command ) ;
        return false ;
      }
      return true ;
    } ;
  }

  static {
    NettyTools.forceNettyClassesToLoad() ;
  }
}