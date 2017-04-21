package io.github.otcdlink.chiron.upend.session.command;

import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.Designator;

public abstract class TransientCommand< CALLABLE_RECEIVER > extends Command< Designator, CALLABLE_RECEIVER >
{
  protected TransientCommand( final Designator designatorInternal ) {
    super( designatorInternal, false ) ;
  }

  @Override
  public final void encodeBody( final PositionalFieldWriter Ø ) {
    throw new UnsupportedOperationException( "Does not persist" ) ;
  }
}
