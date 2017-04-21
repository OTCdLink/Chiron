package io.github.otcdlink.chiron.middle.tier;

import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.downend.tier.DownendCommandInterceptorTier;
import io.github.otcdlink.chiron.upend.tier.UpendCommandInterceptorTier;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandInterceptorTierTest {

  @Test
  public void upendUpward() throws Exception {
    final EmbeddedChannel channel = new EmbeddedChannel(
        new UpendCommandInterceptorTier( newUpwardInterceptor() ) ) ;
    writeInboundAndVerify( channel ) ;
  }


  @Test
  public void downendUpward() throws Exception {
    final EmbeddedChannel channel = new EmbeddedChannel(
        new DownendCommandInterceptorTier( newUpwardInterceptor() ) ) ;
    writeOutboundAndVerify( channel ) ;
  }

  @Test
  public void upendDownward() throws Exception {
    final EmbeddedChannel channel = new EmbeddedChannel(
        new UpendCommandInterceptorTier( newDownwardInterceptor() ) ) ;
    writeOutboundAndVerify( channel ) ;
  }


  @Test
  public void downendDownward() throws Exception {
    final EmbeddedChannel channel = new EmbeddedChannel(
        new DownendCommandInterceptorTier( newDownwardInterceptor() ) ) ;
    writeInboundAndVerify( channel ) ;
  }



// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( CommandInterceptorTierTest.class ) ;

  private static final class PrivateCommand extends Command< String, Void > {
    protected PrivateCommand( final String message ) {
      super( message ) ;
    }

    @Override
    public void callReceiver( final Void Ø ) {
      throw new UnsupportedOperationException( "Do not call" ) ;
    }

    @Override
    public void encodeBody( final PositionalFieldWriter Ø ) {
      throw new UnsupportedOperationException( "Do not call" ) ;
    }

    public String message() {
      return endpointSpecific ;
    }
  }

  private static final PrivateCommand COMMAND_TO_FORWARD = new PrivateCommand( "forward-0" ) ;
  private static final PrivateCommand COMMAND_TO_BACKWARD = new PrivateCommand( "backward-0" ) ;
  private static final PrivateCommand COMMAND_TO_SWALLOW = new PrivateCommand( "swallow-0" ) ;

  private static CommandInterceptor newUpwardInterceptor() {
    return new CommandInterceptor() {
      @Override
      public boolean interceptUpward( final Command command, final Sink sink ) {
        return intercept( command, sink ) ;
      }
      @Override
      public boolean interceptDownward( final Command command, final Sink sink ) {
        throw new AssertionError( "Should not be called" ) ;
      }
    } ;
  }

  private static CommandInterceptor newDownwardInterceptor() {
    return new CommandInterceptor() {
      @Override
      public boolean interceptUpward( final Command command, final Sink sink ) {
        throw new AssertionError( "Should not be called" ) ;
      }
      @Override
      public boolean interceptDownward( final Command command, final Sink sink ) {
        return intercept( command, sink ) ;
      }
    } ;
  }

  private static boolean intercept( final Command command, final CommandInterceptor.Sink sink ) {
    if( command instanceof PrivateCommand ) {
      final PrivateCommand privateCommand = ( PrivateCommand ) command ;
      if( privateCommand.message().startsWith( "forward-" ) ) {
        LOGGER.info( "Implicitely forwarding " + command + " (returning false)." ) ;
        return false ;
      } else if( privateCommand.message().startsWith( "backward-" ) ) {
        LOGGER.info( "Backwarding " + command + " (returning true)." ) ;
        sink.sendBackward( command ) ;
        return true ;
      } else if( privateCommand.message().startsWith( "swallow-" ) ) {
        LOGGER.info( "Swallowing " + command + " (returning true)." ) ;
        return true ;
      }
    }
    LOGGER.info( "Doing nothing with " + command + " (returning false)." ) ;
    return false ;
  }

  private static void writeInboundAndVerify( final EmbeddedChannel channel ) {
    channel.writeInbound( COMMAND_TO_FORWARD ) ;
    assertThat( ( Object ) channel.readOutbound() ).isNull() ;
    assertThat( ( Object ) channel.readInbound() ).isSameAs( COMMAND_TO_FORWARD ) ;
    assertThat( ( Object ) channel.readInbound() ).isNull() ;

    channel.writeInbound( COMMAND_TO_BACKWARD ) ;
    assertThat( ( Object ) channel.readInbound() ).isNull() ;
    assertThat( ( Object ) channel.readOutbound() ).isSameAs( COMMAND_TO_BACKWARD ) ;
    assertThat( ( Object ) channel.readOutbound() ).isNull() ;

    channel.writeInbound( COMMAND_TO_SWALLOW ) ;
    assertThat( ( Object ) channel.readInbound() ).isNull() ;
    assertThat( ( Object ) channel.readOutbound() ).isNull() ;
  }

  public static void writeOutboundAndVerify( final EmbeddedChannel channel ) {
    channel.writeOutbound( COMMAND_TO_FORWARD ) ;
    assertThat( ( Object ) channel.readInbound() ).isNull() ;
    assertThat( ( Object ) channel.readOutbound() ).isSameAs( COMMAND_TO_FORWARD ) ;
    assertThat( ( Object ) channel.readOutbound() ).isNull() ;

    channel.writeOutbound( COMMAND_TO_BACKWARD ) ;
    assertThat( ( Object ) channel.readOutbound() ).isNull() ;
    assertThat( ( Object ) channel.readInbound() ).isSameAs( COMMAND_TO_BACKWARD ) ;
    assertThat( ( Object ) channel.readInbound() ).isNull() ;

    channel.writeOutbound( COMMAND_TO_SWALLOW ) ;
    assertThat( ( Object ) channel.readInbound() ).isNull() ;
    assertThat( ( Object ) channel.readOutbound() ).isNull() ;
  }


}