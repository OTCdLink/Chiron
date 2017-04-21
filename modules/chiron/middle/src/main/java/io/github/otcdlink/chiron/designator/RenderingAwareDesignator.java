package io.github.otcdlink.chiron.designator;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.Stamp;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.netty.channel.ChannelPipeline;

import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link Designator} meant to propagate a computation waiting for some parameter.
 *
 * <h1>Other candidate names</h1>
 * ResponderDesignator
 * RespondingDesignator
 * RefluxDesignator
 * ReplyCommandDesignator
 * ResponsiveDesignator
 * DeferredCommandDesignator
 * LateCommandDesignator
 * RelapseDesignator
 * KickbackDesignator
 * CallbackDesignator
 * BackrunDesignator
 * CommandStuffedDesignator
 * RetroactiveDesignator
 * AfterfactDesignator
 * AftermathDesignator
 * UniqueComputationDesignator
 * WaterfallDesignator
 */
public final class RenderingAwareDesignator
    extends Designator
    implements Designator.Derivable< RenderingAwareDesignator >
{

  /**
   * Useful to find which {@link ChannelPipeline} to send to when there is no
   * {@link #sessionIdentifier}.
   */
  public final RichHttpRequest richHttpRequest;

  private final BiFunction<RichHttpRequest, Object, Object > renderer;

  public RenderingAwareDesignator(
      final Kind kind,
      final Stamp stamp,
      final Stamp cause,
      final Command.Tag tag,
      final SessionIdentifier sessionIdentifier,
      final RichHttpRequest richHttpRequest,
      final BiFunction<RichHttpRequest, Object, Object > renderer
  ) {
    this(
        checkNotNull( renderer ),
        kind,
        stamp,
        cause,
        tag,
        richHttpRequest,
        sessionIdentifier
    ) ;
  }

  protected RenderingAwareDesignator(
      final BiFunction<RichHttpRequest, Object, Object > renderer,
      final Kind kind,
      final Stamp stamp,
      final Stamp cause,
      final Command.Tag tag,
      final RichHttpRequest richHttpRequest,
      final SessionIdentifier sessionIdentifier
      ) {
    super( kind, stamp, cause, tag, sessionIdentifier ) ;
    checkKind( kind ) ;
    this.richHttpRequest = checkNotNull( richHttpRequest ) ;
    this.renderer = renderer ;
  }

  private BiFunction<RichHttpRequest, Object, Object > extractRenderer() {
    checkState( renderer != null, "Computation not set" ) ;
    return renderer ;
  }

  public Object renderFrom(
      final RichHttpRequest initialRequest,
      final Object dutyResult
  ) {
    final BiFunction<RichHttpRequest, Object, Object > function =
        extractRenderer() ;
    return function.apply( initialRequest, dutyResult ) ;
  }

  private static Kind checkKind( final Kind newKind ) {
    checkArgument( newKind == Kind.INTERNAL, "Probably makes no sense: " + newKind ) ;
    return newKind ;
  }

  @Override
  public RenderingAwareDesignator derive(
      final Kind newKind,
      final Stamp newStamp
  ) {
    return new RenderingAwareDesignator(
        extractRenderer(),
        checkKind( newKind ) ,
        newStamp,
        this.stamp,
        tag,
        richHttpRequest,
        sessionIdentifier
    ) ;
  }

  @Override
  public RenderingAwareDesignator derive(
      final Kind newKind,
      final Stamp newStamp,
      final Command.Tag newTag,
      final SessionIdentifier newSessionIdentifier
  ) {
    return new RenderingAwareDesignator(
        extractRenderer(),
        checkKind( newKind ) ,
        newStamp,
        this.stamp,
        newTag,
        richHttpRequest,
        newSessionIdentifier
    ) ;
  }
}
