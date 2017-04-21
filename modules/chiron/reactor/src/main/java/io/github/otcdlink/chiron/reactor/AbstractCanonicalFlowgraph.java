package io.github.otcdlink.chiron.reactor;

import io.github.otcdlink.chiron.toolbox.ToStringTools;
import org.slf4j.Logger;
import reactor.core.Dispatcher;

import java.util.concurrent.Semaphore;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.otcdlink.chiron.reactor.FlowgraphBuilderTools.createDispatcher;

public abstract class AbstractCanonicalFlowgraph< COMMAND > implements CanonicalFlowgraph< COMMAND > {

  protected final Logger logger;
  protected final Semaphore terminationSemaphore = new Semaphore( 0 ) ;
  protected final StagePack< COMMAND > stagePack;
  protected final Dispatcher dispatcher ;

  protected AbstractCanonicalFlowgraph(
      final Logger logger,
      final StagePack< COMMAND > stagePack,
      final int backlogSize
  ) {
    this.logger = checkNotNull( logger ) ;
    this.dispatcher = createDispatcher( "dispatch", backlogSize ) ;
    this.stagePack = checkNotNull( stagePack ) ;
  }


  protected reactor.fn.BiConsumer< Object, Throwable > errorLogger( final String emitter ) {
    return ( Ø, t ) -> logger.error( "Observed " + emitter + " ", t ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + "{}" ;
  }
}
