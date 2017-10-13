package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.codec.Encoder;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

public interface JournalPersister< COMMAND >
    extends
    Consumer< COMMAND >,
    Closeable
{

  void open() throws IOException ;

  void accept( COMMAND command ) ;

  void autoFlush( boolean autoflush ) ;

  PersisterWhenFrozen whenFrozen() ;


    /**
     * After calling this method it is legal to call {@link #open()} again.
     */
  void close() throws IOException ;

  interface Factory< COMMAND > {
    JournalPersister< COMMAND > createNew( final File file ) ;
  }

  /**
   * Stupid compatibility wrapper.
   */
  static < COMMAND extends Command< DESIGNATOR, DUTY >, DESIGNATOR, DUTY > Factory< COMMAND >
  newFactory(
      final Encoder< DESIGNATOR > designatorEncoder,
      final int schemaVersion,
      final String applicationVersion
  ) {
    checkNotNull( designatorEncoder ) ;  // More convenient to fail now than later.

    final Factory< COMMAND > commandFactory = file -> {

      final JournalFileChannelPersister< DESIGNATOR, DUTY > delegate =
          new JournalFileChannelPersister<>(
              file,
              designatorEncoder,
              schemaVersion,
              applicationVersion
          )
      ;

      return new JournalPersister< COMMAND >() {

        @Override
        public void open() throws IOException {
          delegate.open() ;
        }

        @Override
        public void accept( final COMMAND command ) {
//          if( command instanceof LockPersisterCommand ) {
//            freeze( ( ( LockPersisterCommand ) command ).threadLocker ) ;
//          } else {
//          }
          delegate.accept( command ) ;
        }

        @Override
        public PersisterWhenFrozen whenFrozen() {
          return new InnerLockablePersister() ;
        }

        @Override
        public void autoFlush( final boolean autoflush ) {
          delegate.autoFlush( autoflush ) ;
        }

        @Override
        public void close() throws IOException {
          delegate.close() ;
        }
      } ;
    } ;
    return commandFactory ;
  }

  class InnerLockablePersister implements PersisterWhenFrozen {

  }

  /**
   * Describes what a {@code FlowConductor} can see from a
   * {@code com.otcdlink.server.reactor.StagePack.PersisterStage3}.
   */
  interface PersisterWhenFrozen {

  }
}
