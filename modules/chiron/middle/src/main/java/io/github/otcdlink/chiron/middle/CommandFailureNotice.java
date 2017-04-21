package io.github.otcdlink.chiron.middle;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.Designator;

/**
 * Base class for reporting a problem to the Downend, the Upend calling signalling it by
 * calling {@link CommandFailureDuty#failure(Object, CommandFailureNotice)}.
 * Using standard {@link Command} mechanism gives access to the {@link Designator#sessionIdentifier}
 * so Upend knows who to send the {@link CommandFailureNotice} to.
 */
public class CommandFailureNotice< KIND extends Enum< KIND > & EnumeratedMessageKind >
    extends TypedNotice< KIND >
{

  public CommandFailureNotice( final KIND kind ) {
    super( kind ) ;
  }

  public CommandFailureNotice( final KIND kind, final String message ) {
    super( kind, message ) ;
  }

}
