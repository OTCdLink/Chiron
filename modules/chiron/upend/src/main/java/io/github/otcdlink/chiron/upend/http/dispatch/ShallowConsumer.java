package io.github.otcdlink.chiron.upend.http.dispatch;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.designator.Designator;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

final class ShallowConsumer< COMMAND extends Command< Designator, ? > >
    implements CommandConsumer< COMMAND >
{

  @SuppressWarnings( "ThreadLocalNotStaticFinal" )
  private final ThreadLocal< COMMAND > commandReference = new ThreadLocal<>() ;

  @Override
  public void accept( final COMMAND command ) {
    checkNotNull( command ) ;
    checkState( commandReference.get() == null, "Already set" ) ;
    commandReference.set( command ) ;
  }

  public COMMAND extract() {
    final COMMAND command = commandReference.get() ;
    commandReference.set( null ) ;
    return command ;
  }
}
