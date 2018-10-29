package com.otcdlink.chiron.flow.journal;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.fixture.CatcherFixture;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.testing.junit5.DirectoryExtension;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJournalPersisterTest {

  @Test
  void simpleWrite() throws Exception {
    final PersisterKit persisterKit = newPersisterKit() ;
    persisterKit.writeSequence(
        command( "Hello", 1 ),
        command( "Hello (longer text)", 2 )
    ) ;

    final String wholeFileAsString = persisterKit.loadActualFile() ;
    assertThat( wholeFileAsString ).isEqualTo(
        "SchemaVersion 1 ApplicationVersion JustTesting" + persisterKit.lineEnd +
        "0:1 7he5e5510N echo 1__Hello " + persisterKit.lineEnd +
        "0:2 7he5e5510N echo 2__Hello+%28longer+text%29 " + persisterKit.lineEnd
    ) ;
  }

  /**
   * Write enough {@link Command}s to saturate {@link JournalFileChannelPersister}'s buffer, if we were
   * doing something stupid like holding the whole file in a single buffer.
   */
  @Test
  void lotsOfWrite() throws Exception {
    final PersisterKit persisterKit = newPersisterKit() ;
    final int commandCount = PersisterKit.BUFFER_SIZE / PersisterKit.BYTES_PER_LINE + 10 ;
    persisterKit.writeSequence( commandCount ) ;

    final String wholeFileAsString = persisterKit.loadActualFile() ;
    assertThat( wholeFileAsString ).contains( " " + ( commandCount - 1 ) + "__" ) ;
  }

  @Test
  @Disabled( "Takes too long and asserts nothing" )
  void evenMoreWrites() throws Exception {
    final PersisterKit persisterKit = newPersisterKit() ;
    persisterKit.persister.autoFlush( true ) ;
    persisterKit.writeSequence( 100_000 ) ;
  }

// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( AbstractJournalPersisterTest.class ) ;

  private static final SessionIdentifier SESSION_IDENTIFIER =
      new SessionIdentifier( "7he5e5510N" ) ;

  private static final DesignatorForger.CounterStep DESIGNATOR_FORGER =
      DesignatorForger.newForger().session( SESSION_IDENTIFIER ).flooredInstant( 1 ) ;

  private static Command< Designator, EchoUpwardDuty< Designator >> command(
      final int index
  ) {
    return command( "Hello", index ) ;
  }

  private static Command< Designator, EchoUpwardDuty< Designator > > command(
      final String message,
      final int index
  ) {
    return new UpwardEchoCommand<>(
        DESIGNATOR_FORGER.counter( index ).upward(), index + "__" + message ) ;
  }

  @RegisterExtension
  final DirectoryExtension directoryExtension = new DirectoryExtension() ;

  protected abstract PersisterKit newPersisterKit() ;

  protected abstract class PersisterKit {

    static final int BUFFER_SIZE = 1000 ;

    /**
     * Include a directory that doesn't exist yet so we we test its creation.
     */
    static final String INTRADAY_RELATIVE_FILENAME = "my/intraday";

    public final CatcherFixture.RecordingCatcher< CatcherFixture.Record > catcher =
        CatcherFixture.newSimpleRecordingCatcher() ;
    final JournalPersister< Command< Designator, EchoUpwardDuty< Designator > > >
        persister ;
    final int bufferSize ;
    final LineBreak lineBreak ;
    final String lineEnd ;

    public PersisterKit() {
      this( BUFFER_SIZE, IntradayPersistenceConstants.LINE_BREAK ) ;
    }

    PersisterKit( final int bufferSize, final LineBreak lineBreak ) {
      this.lineBreak = lineBreak ;
      this.bufferSize = bufferSize ;
      this.lineEnd = lineBreak.asString ;
      persister = createPersister() ;
      LOGGER.info( "Created " + persister + "." ) ;
    }

    protected abstract JournalPersister< Command< Designator, EchoUpwardDuty< Designator > > >
    createPersister() ;

    //  return Joiner.on( lineBreak.asString )
    //      .join( Files.readLines( intradayFile, Charsets.US_ASCII ) ) ;
    public abstract String loadActualFile() throws IOException;

    final void writeSequence( final int commandCount ) throws IOException {
      writeSequence( () -> new AbstractIterator<
          Command< Designator, EchoUpwardDuty< Designator > >
      >() {
        private  int current = 0 ;
        @Override
        protected Command< Designator, EchoUpwardDuty< Designator > > computeNext() {
          if( current < commandCount ) {
            return command( current ++ ) ;
          } else {
            return endOfData() ;
          }
        }
      } ) ;
    }

    @SafeVarargs
    final void writeSequence(
        final Command<Designator, EchoUpwardDuty<Designator>>... commands
    ) throws IOException {
      writeSequence( ImmutableList.copyOf( commands ) ) ;
    }

    final void writeSequence(
        final Iterable<Command<Designator, EchoUpwardDuty<Designator>>> commands
    ) throws IOException {
      persister.open() ;
      LOGGER.info( "Writing " + Command.class.getSimpleName() + "s ..." ) ;
      final long start = System.currentTimeMillis() ;
      final int[] commandCount = { 0 } ;
      commands.forEach( command -> {
        try {
          persister.accept( command ) ;
          commandCount[ 0 ] ++ ;
        } catch( final Exception e ) {
          throw new RuntimeException( e ) ;
        }
      } ) ;
      final long stop = System.currentTimeMillis() ;
      persister.close() ;
      final long durationMs = stop - start ;
      if( durationMs == 0 ) {
        LOGGER.info( "Done. No stats because duration was too short." ) ;
      } else {
        LOGGER.info(
            "Done. Writing " + commandCount[ 0 ] + " " + COMMAND + "s " +
            "took " + durationMs + " ms " +
            "(this is approximatly " + ( 1000 * commandCount[ 0 ] / durationMs ) + " " +
            COMMAND + "/s)."
        ) ;
      }
    }

    /**
     * This is a pessimistic approximation of the number of bytes in a text line representing a
     * {@link Command}.
     */
    static final int BYTES_PER_LINE = 20 ;
  }

  private static final String COMMAND = Command.class.getSimpleName() ;


}