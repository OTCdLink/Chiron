package io.github.otcdlink.chiron.integration.echo;

import com.google.common.base.Preconditions;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;

public class EchoUpwardCommandCrafter< DESIGNATOR > implements EchoUpwardDuty< DESIGNATOR > {

  private final CommandConsumer< Command< DESIGNATOR, EchoUpwardDuty< DESIGNATOR > > >
  commandConsumer ;

  public EchoUpwardCommandCrafter(
      final CommandConsumer< Command< DESIGNATOR, EchoUpwardDuty< DESIGNATOR > > > commandConsumer
  ) {
    this.commandConsumer = Preconditions.checkNotNull( commandConsumer ) ;
  }

  @Override
  public void requestEcho( final DESIGNATOR designator, final String message ) {
    try {
      commandConsumer.accept( new UpwardEchoCommand<>( designator, message ) ) ;
    } catch( final Exception e ) {
      throw new RuntimeException( "We should get rid of this stupid catch clause", e ) ;
    }
  }
}
