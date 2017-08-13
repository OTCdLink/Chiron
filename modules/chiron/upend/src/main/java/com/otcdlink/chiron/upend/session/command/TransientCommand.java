package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;

public abstract class TransientCommand< CALLABLE_RECEIVER > extends Command< Designator, CALLABLE_RECEIVER >
{
  protected TransientCommand( final Designator designatorInternal ) {
    super( designatorInternal, false ) ;
  }

  @Override
  public final void encodeBody( final PositionalFieldWriter Ø ) {
    throw new UnsupportedOperationException( "Do not call, " + this + " derives from " +
        TransientCommand.class.getSimpleName() + " which meanst there is no encoding/decoding" ) ;
  }
}
