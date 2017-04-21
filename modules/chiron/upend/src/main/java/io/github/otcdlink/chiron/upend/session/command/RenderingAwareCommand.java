package io.github.otcdlink.chiron.upend.session.command;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.designator.RenderingAwareDesignator;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;
import static io.github.otcdlink.chiron.toolbox.netty.NettyTools.NULL_FULLHTTPREQUEST;

/**
 * A {@link TransientCommand} that captures a value expected by the computation held
 * by given {@link RenderingAwareDesignator}, so the computation can run when needed.
 * This typically happens in a {@link io.netty.channel.ChannelHandler}.
 */
public abstract class RenderingAwareCommand< IN, OUT > extends TransientCommand< Consumer< OUT > > {
  /**
   * Should be immutable.
   */
  private final IN valueIn ;

  protected RenderingAwareCommand(
      final Designator designator,
      final IN valueIn
  ) {
    super( designator ) ;
    this.valueIn = valueIn ;
  }

  public boolean hasComputation() {
    return endpointSpecific instanceof RenderingAwareDesignator;
  }

  /**
   * Should be called only once.
   */
  public final OUT runComputation( final RichHttpRequest fullHttpRequest ) {
    checkState(
        hasComputation(),
        "Should contain an instance of " + RenderingAwareDesignator.class.getSimpleName() +
            ": " + this
    ) ;
    return ( OUT ) ( ( RenderingAwareDesignator ) endpointSpecific )
        .renderFrom( fullHttpRequest, valueIn ) ;
  }

  /**
   * Implemented for the sake of the {@link Command} contract but we probably don't need to call
   * it, calling {@link #runComputation(RichHttpRequest)} will be enough in most cases.
   */
  @Override
  public void callReceiver( final Consumer< OUT > outConsumer ) {
    outConsumer.accept( runComputation( NULL_FULLHTTPREQUEST ) ) ;
  }

  @Override
  protected String toStringBody() {
    return super.toStringBody() + ";" + valueIn ;
  }

}
