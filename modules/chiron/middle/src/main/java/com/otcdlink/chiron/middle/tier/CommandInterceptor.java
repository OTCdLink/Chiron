package com.otcdlink.chiron.middle.tier;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.command.Command;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Generic, {@link ChannelHandler}-agnostic behavior for intercepting {@link Command}s and
 * operating filtering or tranformations on them.
 * <p>
 * Interception may happen on the two directions (upward and downward) because the
 * direction usually define a specific logic path. The same {@link CommandInterceptor} may run
 * in Upend or Downend.
 * This is useful when we want to run some processings on Downend (like throttling or
 * {@link Command} deduplication), while not relying blindly on Upend on what the Downend sent.
 * <p>
 * An intercepted {@link Command} can be sent {@link Sink#sendForward(Object)} forward}, which
 * means the natural direction of message emission (inbound or outbound), or
 * {@link Sink#sendBackward(Object)}, which means reverse direction (back in the direction of the
 * emitting {@link ChannelHandler}). The latter is useful for returning quickly a {@link Command}
 * describing some kind of problem that was detectable before sending it on the wire.
 * The "forward/backward" semantic helps sharing {@link CommandInterceptor}s between Upend
 * and Downend, while {@link CommandInterceptorTier} hides differences when sending Inbound
 * or Outbound.
 */
public interface CommandInterceptor {

  /**
   * @param sink where to send intercepted {@link Command}s to.
   *     Don't reference it past the scope of this method.
   * @return {@code true} if this method handled given {@link Command} and no subsequent
   *     {@link CommandInterceptor} should be called
   *
   */
  default boolean interceptUpward( final Command command, final Sink sink ) {
    return false ;
  }

  default boolean interceptDownward( final Command command, final Sink sink ) {
    return false ;
  }

  interface Sink extends ScheduledSender {
    void sendForward( Object message ) ;
    void sendBackward( Object message ) ;
  }

  /**
   * Creates a new instance for one specific {@link CommandInterceptorTier}.
   */
  interface Factory< INTERCEPTOR extends CommandInterceptor > {
    INTERCEPTOR createNew() ;

    /**
     * Good for tests for which there is only one {@link Channel}, or for stateless
     * {@link CommandInterceptor}.
     */
    static < INTERCEPTOR extends CommandInterceptor > Factory< INTERCEPTOR > always(
        final INTERCEPTOR interceptor
    ) {
      return interceptor == null ? null : () -> interceptor ;
    }
  }

  final class Chain implements CommandInterceptor {
    private final ImmutableList< CommandInterceptor > interceptors ;

    public Chain( final ImmutableList< CommandInterceptor > interceptors ) {
      this.interceptors = checkNotNull( interceptors ) ;
    }

    @Override
    public boolean interceptUpward( final Command command, final Sink sink ) {
      for( final CommandInterceptor commandInterceptor : interceptors ) {
        final boolean handled = commandInterceptor.interceptUpward( command, sink ) ;
        if( handled ) {
          return true ;
        }
      }
      return false ;
    }

    @Override
    public boolean interceptDownward( final Command command, final Sink sink ) {
      for( final CommandInterceptor commandInterceptor : interceptors ) {
        final boolean handled = commandInterceptor.interceptDownward( command, sink ) ;
        if( handled ) {
          return true ;
        }
      }
      return false ;
    }

    public static class Factory implements CommandInterceptor.Factory {

      private final ImmutableList< CommandInterceptor.Factory > factories ;

      public Factory( final CommandInterceptor.Factory... factories ) {
        this( ImmutableList.copyOf( factories ) ) ;
      }

      public Factory( final ImmutableList< CommandInterceptor.Factory > factories ) {
        this.factories = checkNotNull( factories ) ;
      }

      @Override
      public CommandInterceptor createNew() {
        final ImmutableList< CommandInterceptor > commandInterceptors = factories.stream()
            .map( CommandInterceptor.Factory::createNew )
            .collect( ImmutableList.toImmutableList() )
        ;
        return new Chain( commandInterceptors ) ;
      }
    }
  }

}

