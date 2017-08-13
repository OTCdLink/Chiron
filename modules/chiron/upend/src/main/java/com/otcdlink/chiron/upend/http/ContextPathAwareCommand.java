package com.otcdlink.chiron.upend.http;

import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.upend.http.dispatch.HttpDispatcher;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link Command} produced by an {@link HttpDispatcher}.
 * Knowledge of {@link #contextPath} is useful for compatibility with old Servlets
 * during transition phase.
 *
 * @param <REQUEST> Likely to be {@link FullHttpRequest}.
 */
public abstract class ContextPathAwareCommand< REQUEST, CALLABLE_RECEIVER >
    extends Command< Designator, CALLABLE_RECEIVER >
{
  private static final Logger LOGGER = LoggerFactory.getLogger( ContextPathAwareCommand.class ) ;

  private static final boolean DEBUG = true ;

  public final String contextPath ;

  public final REQUEST initialHttpRequest ;

  private final Throwable factoryInstanciationPlace ;

  protected ContextPathAwareCommand(
      final Designator endpointSpecific,
      final String contextPath,
      final REQUEST initialRequest,
      Throwable factoryInstanciationPlace
  ) {
    super( endpointSpecific ) ;
    this.contextPath = checkNotNull( contextPath ) ;
    this.initialHttpRequest = checkNotNull( initialRequest ) ;
    if( initialRequest instanceof ReferenceCounted ) {
      ( ( ReferenceCounted ) initialRequest ).retain() ;
    }
    this.factoryInstanciationPlace = factoryInstanciationPlace ;
  }

  @Override
  public final void encodeBody( final PositionalFieldWriter Ø ) throws IOException {
    throw new UnsupportedOperationException( "Do not call" ) ;
  }

  @Override
  public void callReceiver( final CALLABLE_RECEIVER callableReceiver ) {
    if( DEBUG && factoryInstanciationPlace != null ) {
      LOGGER.debug( "Calling from " + this + " the " + callableReceiver + " instantiated at:", factoryInstanciationPlace ) ;
    }
    doCallReceiver( callableReceiver ) ;
  }

  protected abstract void doCallReceiver( CALLABLE_RECEIVER callableReceiver ) ;
}
