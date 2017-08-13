package com.otcdlink.chiron.middle.shaft;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.toolbox.ToStringTools;

import static com.google.common.base.Preconditions.checkNotNull;

public class CommandExecutionFailure {

  public final Command command ;
  public final Throwable throwable ;

  public CommandExecutionFailure( final Command command, final Throwable throwable ) {
    this.command = checkNotNull( command ) ;
    this.throwable = checkNotNull( throwable ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + '{' +
        "command=" + command.getClass().getSimpleName() +
        ";throwable=" + throwable.getClass().getSimpleName() +
        '}'
    ;
  }
}
