package com.otcdlink.chiron.middle.tier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.toolbox.ToStringTools;
import mockit.Injectable;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;

public class CommandInterceptorTest {

  @Test
  public void chain( @Injectable final CommandInterceptor.Sink sink ) throws Exception {
    final Fixture fixture = new Fixture() ;

    final CommandInterceptor.Chain.Factory factory = new CommandInterceptor.Chain.Factory(
        () -> fixture.commandInterceptor1,
        () -> fixture.commandInterceptor2
    ) ;

    final CommandInterceptor interceptorChain = factory.createNew() ;

    interceptorChain.interceptUpward( fixture.command2, sink ) ;

    assertThat( fixture.recorder.traversals() ).containsExactly(
        new Traversal( fixture.commandInterceptor1, fixture.command2, false ),
        new Traversal( fixture.commandInterceptor2, fixture.command2, true )
    ) ;

  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( CommandInterceptorTest.class ) ;

  private static final class Traversal {
    public final CommandInterceptor commandInterceptor ;
    public final Command command ;
    public final boolean handled ;

    private Traversal(
        final CommandInterceptor commandInterceptor,
        final Command command,
        final boolean handled
    ) {
      this.commandInterceptor = checkNotNull( commandInterceptor ) ;
      this.command = checkNotNull( command ) ;
      this.handled = handled ;
    }

    @Override
    public boolean equals( final Object o ) {
      if( this == o ) {
        return true ;
      }
      if( o == null || getClass() != o.getClass() ) {
        return false ;
      }

      final Traversal that = ( Traversal ) o ;

      if( handled != that.handled ) {
        return false ;
      }
      if( !commandInterceptor.equals( that.commandInterceptor ) ) {
        return false ;
      }
      return command.equals( that.command ) ;
    }

    @Override
    public int hashCode() {
      int result = commandInterceptor.hashCode() ;
      result = 31 * result + command.hashCode() ;
      result = 31 * result + ( handled ? 1 : 0 ) ;
      return result ;
    }

    private interface Recorder {
      boolean record(
          final CommandInterceptor commandInterceptor,
          final Command command,
          final boolean handled
      ) ;

      ImmutableList< Traversal > traversals() ;

      static Recorder newRecorder() {
        return asRecorder( ImmutableList.builder() ) ;
      }

      static Recorder asRecorder( final ImmutableList.Builder< Traversal > builder ) {
          return new Recorder() {
            @Override
            public boolean record(
                final CommandInterceptor commandInterceptor,
                final Command command,
                final boolean handled
            ) {
              final Traversal traversal = new Traversal( commandInterceptor, command, handled ) ;
              builder.add( traversal ) ;
              return handled ;
            }

            @Override
            public ImmutableList< Traversal > traversals() {
              return builder.build() ;
            }
          } ;
      }
    }

  }

  private static CommandInterceptor interceptor(
      final Traversal.Recorder recorder,
      final Command... handledCommands
  ) {
    final ImmutableSet< Command > handledCommandSet = ImmutableSet.copyOf( handledCommands ) ;
    return new CommandInterceptor() {
      @Override
      public boolean interceptUpward( final Command command, final Sink sink ) {
        return intercept( command ) ;
      }

      @Override
      public boolean interceptDownward( final Command command, final Sink sink ) {
        return intercept( command ) ;
      }

      private boolean intercept( final Command command ) {
        final boolean handled = handledCommandSet.contains( command ) ;
        recorder.record( this, command, handled );
        LOGGER.info( "Traversing " + this + " with " + command + ", " +
            ( handled ? "handled" : "not handled" ) + "." ) ;
        return handled ;
      }

      @Override
      public String toString() {
        return ToStringTools.nameAndCompactHash( this ) + "{" + handledCommandSet + "}" ;
      }
    } ;
  }

  private static class PrivateCommand extends Command< String, Void > {

    private PrivateCommand( final int number ) {
      super( Integer.toString( number ) ) ;
    }

    @Override
    public void callReceiver( final Void Ø ) {
      throw new UnsupportedOperationException( "Do not call" ) ;
    }

    @Override
    public void encodeBody( final PositionalFieldWriter positionalFieldWriter ) {
      throw new UnsupportedOperationException( "Do not call" ) ;
    }
  }

  /**
   * Pre-instantiate everything that a single test could use.
   */
  private static class Fixture {
    public final Traversal.Recorder recorder = Traversal.Recorder.newRecorder() ;
    public final Command command1 = new PrivateCommand( 1 ) ;
    public final Command command2 = new PrivateCommand( 2 ) ;
    public final CommandInterceptor commandInterceptor1 = interceptor( recorder, command1 ) ;
    public final CommandInterceptor commandInterceptor2 = interceptor( recorder, command2 ) ;
  }
}