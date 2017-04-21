package io.github.otcdlink.chiron.upend.http.dispatch;

import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.designator.RenderingAwareDesignator;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.upend.http.dispatch.UsualConditions.ALWAYS;
import static io.netty.channel.ChannelFutureListener.CLOSE;

/**
 * Defines a sequence of rules applied to a {@link RichHttpRequest} to determine
 * how to operate on a {@link io.netty.channel.ChannelHandlerContext}.
 *
 * There are different methods for that.
 * <ul>
 *   <li>
 *     Use {@link HttpResponder.Resolver}-based methods to create a {@link Command} object
 *     that goes directly into the {@link ChannelPipeline}.
 *   </li><li>
 *     Use {@link HttpResponder.DutyCaller} to create a {@link Command} object from a
 *     Duty interface, associating a rendering function once the callback happens.
 *   </li>
 * </ul>
 *
 * <h1>Usage</h1>
 * <pre> W O R K   I N   P R O G R E S S
    HttpDispatcher.newDispatcher()
        .beginCondition( LOCALHOST )
            .action( absolutePath( "" ), pathAware.path() + "/" )
        .relativeMatch( "/", supervisionPage( "" ) )
     .beginCondition( GET_METHOD )
     .relativeMatch( ConsoleFormConstants.ForDiagnostic.SHOW, diagnostic() )
     .end()
     .beginCondition( POST_METHOD )
     .relativeMatch(
     ConsoleFormConstants.ForLogicConfiguration.SET_RETENTION_DURATION,
     setRetentionDuration()
     )
     .end()
     .notFound()
     .end()
     .forbidden()
     ;
 * </pre>
 *
 * <h1>Typing</h1>
 * Type parameters are only here to propagate the concrete type of
 * {@link HttpResponder.DutyCaller} (the Duty interface that makes creation of
 * {@link Command} objects transparent).
 * Usage of {@link Command} and {@link RichHttpRequest} is hardcoded. Making
 * those parameterizable is ridiculously too complex.
 *
 * @param <COMMAND> helps to narrow down actual type of {@link CommandConsumer}.
 * @param <COMMAND_CONSUMER> makes Java compiler happy.
 * @param <DUTY> the type returned by {@link EvaluationContext#duty} within the scope
 *     of {@link #beginBareActionContext(Function)}.
 *     Fresh instance has this parameter type set to {@code Void}.
 * @param <ANCESTOR> the type of parent scope of {@link #beginBareActionContext(Function)}
 *     so we can pop context properly.
 *     Fresh instance has this parameter type set to {@code Void}.
 * @param <THIS> propages the type of a subclass if there is one, so subclass methods'
 *     remain available.
 */
public class BareHttpDispatcher<
    COMMAND extends Command< Designator, DUTY >,
    COMMAND_CONSUMER extends CommandConsumer< COMMAND >,
    DUTY,
    ANCESTOR,
    THIS extends BareHttpDispatcher< COMMAND, COMMAND_CONSUMER, DUTY, ANCESTOR, THIS >
> {

  private static final Logger LOGGER = LoggerFactory.getLogger( BareHttpDispatcher.class ) ;

  private final Designator.FactoryForInternal designatorFactory ;

  public BareHttpDispatcher( final Designator.FactoryForInternal designatorFactory ) {
    this.designatorFactory = checkNotNull( designatorFactory ) ;
  }

  public static BareHttpDispatcher< ?, ?, Void, Void, ? extends BareHttpDispatcher>
  newCrudeHttpDispatcher(
      final Designator.FactoryForInternal designatorSupplier
  ) {
    return new BareHttpDispatcher<>( designatorSupplier ) ;
  }

  protected final THIS genericThis() {
    return ( THIS ) this ;
  }

  public HttpRequestRelayer build() {
    if( currentBuildContext.parent != null ) {
      throw new ContextException( "Unclosed context, current is " + currentBuildContext ) ;
    }
    final BuildContext frozen = currentBuildContext.freeze() ;
    return frozen ;
  }

// =======
// Context
// =======

  /**
   * A mutable structure containing a list of
   */
  private abstract class BuildContext< CONTEXT_DUTY >
      implements HttpRequestRelayer, EvaluationContext< CONTEXT_DUTY >
  {

    protected final BuildContext parent ;
    private final CONTEXT_DUTY duty ;
    private final UriPath uriPath ;
    protected final List< HttpRequestRelayer > relayers ;

    protected BuildContext(
        final BuildContext parent,
        final UriPath uriPath,
        final CONTEXT_DUTY duty,
        final List< HttpRequestRelayer > relayers
    ) {
      this.parent = parent ;
      this.uriPath = checkNotNull( uriPath ) ;
      this.duty = duty ;
      if( relayers == null ) {
        this.relayers = new ArrayList<>() ;
      } else {
        this.relayers = relayers ;
      }
    }

    public abstract BuildContext freeze() ;

    protected final List< HttpRequestRelayer > freezeRelayers() {
      final ImmutableList.Builder< HttpRequestRelayer > builder = ImmutableList.builder() ;
      for( final HttpRequestRelayer relayer : relayers ) {
        if( relayer instanceof BuildContext ) {
          builder.add( ( ( BuildContext ) relayer ).freeze() ) ;
        } else {
          builder.add( relayer ) ;
        }
      }
      return builder.build() ;
    }

    @Override
    public Designator designator() {
      return designatorFactory.internal() ;
    }

    protected final BuildContext add( final HttpRequestRelayer httpRequestRelayer ) {
      relayers.add( checkNotNull( httpRequestRelayer ) ) ;
      return this ;
    }

    @Override
    public final boolean relay(
        final RichHttpRequest httpRequest,
        final ChannelHandlerContext channelHandlerContext
    ) {
      try {
        if( contextAppliesTo( httpRequest ) ) {
          for( final HttpRequestRelayer httpRequestRelayer : relayers ) {
            if( httpRequestRelayer.relay( httpRequest, channelHandlerContext ) ) {
              return true ;
            }
          }
        }
        return false ;
      } catch( final Exception e ) {
        LOGGER.error( "Error while processing " + httpRequest, e ) ;
        new UsualHttpCommands.ServerError(
            "<p>Error while processing request.</p><pre>" +
            httpRequest.toString() + "</pre>"
        ).feed( channelHandlerContext ) ;
        return true ;
      }
    }



    @Override
    public final CONTEXT_DUTY duty() {
      if( duty == null ) {
        throw new ContextException(
            "No duty set (use " + BEGIN_CALL_CONTEXT_METHOD.getName() + " first)" ) ;
      } else {
        return duty ;
      }
    }

    public final CONTEXT_DUTY dutyOrNull() {
      return duty ;
    }

    @Override
    public final UriPath contextPath() {
      return uriPath ;
    }

    private  < RESULT > EvaluationContext< DUTY > capturePhasing(
        final RichHttpRequest richHttpRequest,
        final HttpResponder.Renderer< RESULT > renderer
    ) {
      checkNotNull( richHttpRequest ) ;
      checkNotNull( renderer ) ;
      return new EvaluationContext< DUTY >() {
        @Override
        public UriPath contextPath() {
          return uriPath ;
        }

        @Override
        public DUTY duty() {
          return ( DUTY ) duty ;
        }

        @Override
        public Designator designator() {
          final RenderingAwareDesignator renderingAwareDesignator = designatorFactory.phasing(
              richHttpRequest,
              ( BiFunction<RichHttpRequest, Object, Object> ) renderer
          ) ;
          return renderingAwareDesignator;
        }
      } ;
    }

    public boolean contextAppliesTo( final RichHttpRequest httpRequest ) {
      return true ;
    }

    private ActionContext currentActionContext() {
      BuildContext context = this ;
      while( context != null ) {
        if( context instanceof ActionContext ) {
          return ( ActionContext ) context ;
        } else {
          context = context.parent ;
        }
      }
      throw new ContextException( "No " + ActionContext.class.getSimpleName() +
          " reachable from " + this ) ;
    }

    public int depth() {
      BuildContext context = this ;
      int total = 0 ;
      while( context != null ) {
        total ++ ;
        context = context.parent ;
      }
      return total ;
    }

  }

  private class RootContext extends BuildContext {

    public RootContext() {
      this( null ) ;
    }

    private RootContext( final List< HttpRequestRelayer > relayers ) {
      super( null, UriPath.ROOT, null, relayers ) ;
    }

    @Override
    public RootContext freeze() {
      return new RootContext( freezeRelayers() ) ;
    }
  }

  private class ActionContext<
      CHILD_COMMAND extends Command< Designator, CHILD_DUTY >,
      CHILD_DUTY
  > extends BuildContext {
    private final ShallowConsumer< CHILD_COMMAND > shallowConsumer ;

    public ActionContext(
        final BuildContext parent,
        final UriPath uriPath,
        final CHILD_DUTY duty,
        final List< HttpRequestRelayer > relayers,
        final ShallowConsumer< CHILD_COMMAND > shallowConsumer
    ) {
      super( parent, uriPath, duty, relayers ) ;
      this.shallowConsumer = checkNotNull( shallowConsumer ) ;
    }

    public ActionContext(
        final Function< CommandConsumer< CHILD_COMMAND >, CHILD_DUTY > commandCrafterCreator
    ) {
      this(
          currentBuildContext,
          currentBuildContext.contextPath(),
          captureShallowConsumer( commandCrafterCreator ),
          null,
          retrieveShallowConsumer()
      ) ;
    }

    @Override
    public ActionContext freeze() {
      return new ActionContext( parent, contextPath(), duty(), freezeRelayers(), shallowConsumer ) ;
    }
  }

  /**
   * Some magic for {@link BareHttpDispatcher.ActionContext#ActionContext(Function)}
   */
  private static final ThreadLocal SHALLOW_CONSUMER_CAPTURE = new ThreadLocal< ShallowConsumer >() ;

  /**
   * Some magic for {@link BareHttpDispatcher.ActionContext#ActionContext(Function)}
   */
  private static< CHILD_COMMAND extends Command< Designator, ? >, CHILD_DUTY >
  CHILD_DUTY captureShallowConsumer(
      final Function< CommandConsumer< CHILD_COMMAND >, CHILD_DUTY > commandCrafterCreator
  ) {
    final ShallowConsumer< CHILD_COMMAND > shallowConsumer = new ShallowConsumer<>() ;
    final CHILD_DUTY duty = commandCrafterCreator.apply( shallowConsumer ) ;
    SHALLOW_CONSUMER_CAPTURE.set( shallowConsumer ) ;
    return duty ;
  }

  /**
   * Some magic for {@link BareHttpDispatcher.ActionContext#ActionContext(Function)}
   */
  private static < CHILD_COMMAND extends Command< Designator, ? > >

  /**
   * Some magic for {@link BareHttpDispatcher.ActionContext#ActionContext(Function)}
   */
  ShallowConsumer< CHILD_COMMAND > retrieveShallowConsumer() {
    final ThreadLocal< ShallowConsumer< CHILD_COMMAND > > shallowConsumerCapture =
        SHALLOW_CONSUMER_CAPTURE ;
    final ShallowConsumer< CHILD_COMMAND > shallowConsumer = shallowConsumerCapture.get() ;
    shallowConsumerCapture.set( null ) ;
    return shallowConsumer ;
  }


  private class PathContext extends BuildContext {

    private PathContext(
        final BuildContext parent,
        final UriPath uriPath,
        final DUTY duty,
        final List< HttpRequestRelayer > relayers
    ) {
      super( parent, uriPath, duty, relayers ) ;
    }

    public PathContext( final String nextPathSegment ) {
      this(
          currentBuildContext,
          UriPath.append( currentBuildContext.contextPath(), nextPathSegment ),
          ( DUTY ) currentBuildContext.dutyOrNull(),
          null
      ) ;
    }

    @Override
    public PathContext freeze() {
      return new PathContext( parent, contextPath(), ( DUTY ) dutyOrNull(), freezeRelayers() ) ;
    }

    @Override
    public boolean contextAppliesTo( final RichHttpRequest httpRequest ) {
      final UriPath.MatchKind matchKind = contextPath().pathMatch( httpRequest.uriPath ) ;
      return matchKind.segmentsMatched ;
    }
  }

  private class ConditionContext extends BuildContext {
    private final HttpResponder.Condition condition ;


    private ConditionContext(
        final BuildContext parent,
        final UriPath uriPath,
        final DUTY duty,
        final List< HttpRequestRelayer > relayers,
        final HttpResponder.Condition condition
    ) {
      super( parent, uriPath, duty, relayers ) ;
      this.condition = checkNotNull( condition ) ;
    }

    public ConditionContext( final HttpResponder.Condition condition ) {
      this(
          currentBuildContext,
          currentBuildContext.contextPath(),
          ( DUTY ) currentBuildContext.dutyOrNull(),
          null,
          condition
      ) ;
    }

    @Override
    public BuildContext freeze() {
      return new ConditionContext(
          parent, contextPath(), ( DUTY ) dutyOrNull(), freezeRelayers(), condition ) ;
    }

    @Override
    public boolean contextAppliesTo( final RichHttpRequest httpRequest ) {
      return condition.shouldAccept( this, httpRequest ) ;
    }
  }

  private BuildContext currentBuildContext = new RootContext() ;

  private void pushContext( final BuildContext buildContext ) {
    currentBuildContext.add( buildContext ) ;
    currentBuildContext = buildContext ;
  }

  private < CONTEXT extends BuildContext > void popContext(
      final Class< CONTEXT > expectedCurrentContextClass
  ) {
    checkNotNull( expectedCurrentContextClass ) ;
    final BuildContext parentContext = currentBuildContext.parent ;
    if( parentContext == null ) {
      throw new ContextException( "No parent for " + currentBuildContext ) ;
    }
    if( currentBuildContext.getClass() == expectedCurrentContextClass ) {
      currentBuildContext = parentContext ;
    } else {
      throw new ContextException( "Trying to pop " +
          expectedCurrentContextClass.getSimpleName() + " when in " + currentBuildContext ) ;
    }
  }

  /**
   * Exposes current {@link UriPath} at the time we build a {@link BareHttpDispatcher},
   * reflecting last change from {@link #beginPathSegment(String)} and {@link #endPathSegment()}.
   */
  public final UriPath buildContextPath() {
    return currentBuildContext.uriPath ;
  }

  public static class ContextException extends RuntimeException {
    private ContextException( final String message ) {
      super( message ) ;
    }
  }


// ======
// Action
// ======

  public final THIS action(
      final HttpResponder.Resolver resolver
  ) {
    return action( ALWAYS, resolver ) ;
  }

  public final THIS action(
      final HttpResponder.Condition condition,
      final HttpResponder.Resolver resolver
  ) {
    /** Capture the{@link BuildContext} before it mutates. */
    final BuildContext currentContext = this.currentBuildContext ;

    currentContext.add( ( httpRequest, channelHandlerContext ) -> {
      if( condition.shouldAccept( currentContext, httpRequest ) ) {
        final Command resolved = resolver.resolve( currentContext, httpRequest ) ;
        if( resolved != null ) {
          channelHandlerContext.fireChannelRead( resolved ) ;
          return true ;
        }
      }
      return false ;
    } ) ;
    return genericThis() ;
  }

  public final THIS response(
      final HttpResponder.Outbound responder
  ) {
    return responseIf( ALWAYS, responder ) ;
  }

  public final THIS responseIf(
      final HttpResponder.Condition condition,
      final HttpResponder.Outbound responder
  ) {
    /** Capture the{@link BuildContext} before it mutates. */
    final BuildContext currentContext = this.currentBuildContext ;

    currentContext.add( new HttpRequestRelayer() {
      @Override
      public boolean relay(
          final RichHttpRequest httpRequest,
          final ChannelHandlerContext channelHandlerContext
      ) {
        if( condition.shouldAccept( currentContext, httpRequest ) ) {
          final PipelineFeeder pipelineFeeder = responder.outbound( currentContext, httpRequest ) ;
          if( pipelineFeeder != null ) {
            pipelineFeeder.feed( channelHandlerContext ) ;
            return true ;
          }
        }
        return false ;
      }

      @Override
      public String toString() {
        return BareHttpDispatcher.class.getSimpleName() + "#outbound{" +
            ( condition == ALWAYS ? "" : condition + ";" ) +
            responder +
            "}"
        ;
      }
    } ) ;
    return genericThis() ;
  }

  public final < RESULT > THIS command(
      final HttpResponder.DutyCaller< DUTY > explicitDutyCaller,
      final HttpResponder.Renderer< RESULT > renderer
  ) {
    return commandIf( ALWAYS, explicitDutyCaller, renderer ) ;
  }

  /**
   * Call methods on a object defined by {@link #beginBareActionContext(Function)}.
   * Calling this method out of the scope of {@link #beginBareActionContext(Function)}
   * makes no sense, and this will spit an exception at runtime.
   */
  public final < RESULT > THIS commandIf(
      final HttpResponder.Condition condition,
      final HttpResponder.DutyCaller< DUTY > explicitDutyCaller,
      final HttpResponder.Renderer< RESULT > renderer
  ) {
    /** Capture the{@link BuildContext} before it mutates. */
    final BuildContext currentContext = this.currentBuildContext ;
    currentContext.duty() ;  // Throws an exception if yet undefined.

    currentContext.add( ( httpRequest, channelHandlerContext ) -> {
      if( condition.shouldAccept( currentContext, httpRequest ) ) {
        final EvaluationContext evaluationContext ;
        if( renderer == null ) {
          evaluationContext = currentContext ;  /** Create vanilla {@link Designator}. */
        } else {
          evaluationContext = currentContext.capturePhasing( httpRequest, renderer ) ;
          httpRequest.retain() ; // We'll pass it again to the renderer.
        }
        final HttpResponse immediateResponse = explicitDutyCaller.call(
            evaluationContext, httpRequest ) ;
        final Command command = currentContext.currentActionContext().shallowConsumer.extract() ;
        if( command != null ) {
          channelHandlerContext.fireChannelRead( command ) ;
        }
        if( immediateResponse != null
            && immediateResponse != HttpResponder.DutyCaller.SILENT_RESPONSE
        ) {
          if( immediateResponse instanceof ReferenceCounted ) {
            ( ( ReferenceCounted ) immediateResponse ).retain() ;
          }
          final ChannelFuture channelFuture =
              channelHandlerContext.writeAndFlush( immediateResponse ) ;
          if( immediateResponse instanceof FullHttpResponse ) {
            channelFuture.addListener( CLOSE ) ;
          }
        }
        if( command != null || immediateResponse != null ) {
          return true ;
        }
      }
      return false ;
    } ) ;
    return genericThis() ;
  }


// =====================
// Other leaf statements
// =====================

  /**
   * <i>Experimental hacking feature:</i>
   * just add some bare {@link ChannelHandlerContext} that can inject arbitrary object
   * into given {@link ChannelHandlerContext}.
   * We probably don't need that because most of time we need more context that
   * the {@link RichHttpRequest} itself, so we have {@link HttpResponder.Resolver}
   * and {@link HttpResponder.DutyCaller} that get called with an {@link EvaluationContext}.
   */
  public THIS just(
      final HttpRequestRelayer httpRequestRelayer
  ) {
    currentBuildContext.add( httpRequestRelayer ) ;
    return genericThis() ;
  }

  public THIS include( final Consumer< ? extends BareHttpDispatcher > httpDispatcherConsumer ) {
    return includeIf( true, httpDispatcherConsumer ) ;
  }


  /**
   * Convenience to delegate some configuration to another code block.
   *
   * @param constructionTimeCondition {@code true} to perform the include.
   */
  public THIS includeIf(
      final boolean constructionTimeCondition,
      final Consumer< ? extends BareHttpDispatcher > httpDispatcherConsumer
  ) {
    checkNotNull( httpDispatcherConsumer ) ;
    if( constructionTimeCondition ) {
      final int contextDepthBeforeIncluding = currentBuildContext.depth() ;
      ( ( Consumer ) httpDispatcherConsumer ).accept( this ) ;
      final int contextDepthAfterIncluding = currentBuildContext.depth() ;
      if( contextDepthBeforeIncluding != contextDepthAfterIncluding ) {
        throw new ContextException( "Depth mistmatch: was " + contextDepthBeforeIncluding +
                " and now " + contextDepthAfterIncluding + " after including" ) ;
      }
    }
    return genericThis() ;
  }



// ===============
// Context nesting
// ===============

  public THIS beginPathSegment(
      final String segment
  ) {
    pushContext( new PathContext( segment ) ) ;
    return genericThis() ;
  }

  public THIS beginCondition(
      final HttpResponder.Condition condition
  ) {
    pushContext( new ConditionContext( condition ) ) ;
    return genericThis() ;
  }

  private static final Method BEGIN_CALL_CONTEXT_METHOD ;

  static {
    try {
      BEGIN_CALL_CONTEXT_METHOD = BareHttpDispatcher.class.getMethod(
          "beginBareActionContext", Function.class) ;
    } catch( final NoSuchMethodException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  /**
   * Subclasses need to redefine this method in order to propagate {@link DUTY} type.
   */
  public final <
      NEW_COMMAND extends Command< Designator, CHILD_DUTY >,
      NEW_COMMAND_CONSUMER extends CommandConsumer< NEW_COMMAND >,
      CHILD_DUTY,
      THAT extends BareHttpDispatcher<
          NEW_COMMAND,
          NEW_COMMAND_CONSUMER,
          CHILD_DUTY,
          THIS,
          THAT
      >
  > THAT beginBareActionContext(
      final Function< NEW_COMMAND_CONSUMER, CHILD_DUTY > commandCrafterCreator
  ) {
    final ActionContext< NEW_COMMAND, CHILD_DUTY > actionContext =
        new ActionContext( commandCrafterCreator ) ;
    pushContext( actionContext ) ;
    return ( THAT ) this ;
  }

  @SuppressWarnings( "unchecked" )
  public final ANCESTOR endActionContext() {
    popContext( ActionContext.class ) ;
    return ( ANCESTOR ) this ;
  }

  public final THIS endCondition() {
    popContext( ConditionContext.class ) ;
    return genericThis() ;
  }

  public final THIS endPathSegment() {
    popContext( PathContext.class ) ;
    return genericThis() ;
  }


}
