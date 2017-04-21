package io.github.otcdlink.chiron.upend.intraday;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;

import java.io.IOException;

/**
 * Defines a synchronous behavior for storing {@link Command} objects.
 */
public interface IntradayPersister< DESIGNATOR, DUTY >
    extends CommandConsumer< Command< DESIGNATOR, DUTY > >
{
  void open() throws IOException ;
  void close() throws IOException ;

  /**
   * Activates automatic flush after writing one {@link Command} (will be effective on the next
   * {@link CommandConsumer#accept(Command)}).
   */
  void autoFlush( boolean flushEachLine ) ;
}
