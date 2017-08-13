package com.otcdlink.chiron.conductor;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Uninterruptibles;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The base class for a record-replay-verify behavior, with role separation.
 *
 * The transformation of {@link GUIDED_OUT} into {@link OUTBOUND} is convenient for requests
 * and responses of mixed types (namely {@code Command} and {@link WebSocketFrame}).
 * This class is too complicated for recording plain HTTP requests/responses.

 * <h2>Big picture</h2>
 * This is how it works with a full record-replay-verify. There can be simplifications.
 *
 * <pre>

          {@link INBOUND}            {@link GUIDED_OUT}
             ^                    |
             |                    |
           Query           Prepare response
             |                    |
             |                    v
  +------------------------------------------+
  |      | {@link Extractor}         |        |      |
  |      |___________________|        |      |
  |      |                            |      |
  |      |   {@link RecordingGuide} |      |
  |      |____________________________|      |
  |          ^                    |          |
  |          |                    |          |
  |       Record          Transform, replay  |
  |          |                    |          |
  |       ___|____________________v___       |
  |      |                            |      |
  |      |      {@link Responder}             |      |
  |      |                            |      |
  +------------------------------------------+
             ^                    |
             |                    |
             |                    v
          Request              Response
          {@link INBOUND}              {@link OUTBOUND}

 * </pre>
 * <p>
 * The {@link Responder} records requests and gives responses with one single method
 * that allow automatic, synchronous responses.
 * <p>
 * The {@link Extractor} subcontract is for checking {@link INBOUND} objects received by
 * {@link Responder#respond(Object)}.
 * <p>
 * The {@link RecordingGuide} takes {@link GUIDED_OUT} objects and transforms them into
 * {@link OUTBOUND} objects to be served by {@link Responder}.
 */
public abstract class Conductor< INBOUND, OUTBOUND, GUIDED_OUT > {

  protected final BlockingQueue< INBOUND > inboundQueue = new LinkedBlockingQueue<>() ;
  private final BlockingQueue< GUIDED_OUT > guidedOutQueue = new LinkedBlockingQueue<>() ;

  protected Conductor( final Function< GUIDED_OUT, OUTBOUND > guidedToOutbound ) {
    this.guidedToOutbound = checkNotNull( guidedToOutbound ) ;
  }

  protected abstract Responder< INBOUND, OUTBOUND > responder() ;
  protected abstract Extractor< INBOUND > guide() ;

  protected final Function< GUIDED_OUT, OUTBOUND > guidedToOutbound ;

  protected final GUIDED_OUT nextGuidedOut() {
    return guidedOutQueue.remove() ;
  }


  public static class AutomaticResponder< INBOUND, OUTBOUND >
      implements Responder< INBOUND, OUTBOUND >
  {

    private final Function< INBOUND, OUTBOUND > transformation ;

    public AutomaticResponder( final Function< INBOUND, OUTBOUND > transformation ) {
      this.transformation = checkNotNull( transformation ) ;
    }

    @Override
    public OUTBOUND respond( final INBOUND inbound ) {
      return transformation.apply( inbound ) ;
    }
  }

  public static class CompositeResponder< INBOUND, OUTBOUND >
      implements Responder< INBOUND, OUTBOUND >
  {
    private final ImmutableList< Responder< INBOUND, OUTBOUND > > responders ;

    @SafeVarargs
    public CompositeResponder( final Responder< INBOUND, OUTBOUND >... responders ) {
      this( ImmutableList.copyOf( responders ) ) ;
    }

    public CompositeResponder( final ImmutableList< Responder< INBOUND, OUTBOUND > > responders ) {
      this.responders = checkNotNull( responders ) ;
    }

    @Override
    public OUTBOUND respond( final INBOUND inbound ) {
      for( final Responder< INBOUND, OUTBOUND > responder : responders ) {
        final OUTBOUND response = responder.respond( inbound ) ;
        if( response != null ) {
          return response ;
        }
      }
      return null ;
    }

  }

  protected abstract class AbstractRecordingResponder implements Responder< INBOUND, OUTBOUND > {
    @Override
    public OUTBOUND respond( final INBOUND inbound ) {
      final INBOUND recordable = asRecordable( inbound ) ;
      if( recordable != null ) {
        inboundQueue.add( inbound ) ;
      }
      return doRespond( inbound ) ;
    }

    protected INBOUND asRecordable( final INBOUND inbound ) {
      return inbound ;
    }

    protected abstract OUTBOUND doRespond( INBOUND inbound ) ;

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{}" ;
    }
  }

  protected final class SilentResponder extends AbstractRecordingResponder {
    @Override
    protected OUTBOUND doRespond( final INBOUND inbound ) {
      return null ;
    }
  }

  protected class ReplayingResponder extends AbstractRecordingResponder {
    @Override
    protected OUTBOUND doRespond( final INBOUND inbound ) {
      final GUIDED_OUT guidedOut = guidedOutQueue.remove() ;
      if( guidedOut == NO_RESPONSE ) {
        return null ;
      } else {
        return guidedToOutbound.apply( guidedOut ) ;
      }
    }
  }

  public class RecordingGuide implements Extractor< INBOUND > {

    @Override
    public final ImmutableList< INBOUND > drainInbound() {
      final Collection< INBOUND > collection = new ArrayList<>() ;
      inboundQueue.drainTo( collection ) ;
      return ImmutableList.copyOf( collection ) ;
    }

    @Override
    public final INBOUND waitForNextInbound() {
      return Uninterruptibles.takeUninterruptibly( inboundQueue ) ;
    }

    @Override
    public final INBOUND waitForInboundMatching( final Predicate<INBOUND> matcher ) {
      checkNotNull( matcher ) ;
      while( true ) {
        final INBOUND inbound = waitForNextInbound() ;
        if( matcher.test( inbound ) ) {
          return inbound ;
        }
      }
    }

    public final boolean outboundQueueEmpty() {
      return guidedOutQueue.isEmpty() ;
    }

    public final RecordingGuide record(
        final GUIDED_OUT guidedOut
    ) {
      guidedOutQueue.add( guidedOut == null ? ( GUIDED_OUT ) NO_RESPONSE : guidedOut ) ;
      return this ;
    }

    public final RecordingGuide recordNoResponse() {
      guidedOutQueue.add( ( GUIDED_OUT ) NO_RESPONSE ) ;
      return this ;
    }


  }

  /**
   * Magic object telling there should be a {@code null} or no response.
   */
  private static final Object NO_RESPONSE = new Object() {
    @Override
    public String toString() {
      return Responder.class.getSimpleName() + "{NO_RESPONSE}" ;
    }
  } ;


  /**
   * {@link INBOUND} and {@link OUTBOUND} objects are manipulated directly.
   */
  public static class RawConductor< INBOUND, OUTBOUND >
      extends Conductor< INBOUND, OUTBOUND, OUTBOUND >
  {
    private final Responder< INBOUND, OUTBOUND > responder = inbound -> nextGuidedOut() ;
    private final RecordingGuide recordingGuide = new RecordingGuide() ;

    public RawConductor() {
      super( Function.identity() );
    }

    @Override
    public Responder< INBOUND, OUTBOUND > responder() {
      return responder ;
    }

    @Override
    public RecordingGuide guide() {
      return recordingGuide;
    }
  }

  /**
   * Does not respond anything.
   * Yes this wastes {@link #guidedOutQueue}.
   */
  public static class RecordingOnly< INBOUND > extends Conductor< INBOUND, Void, Void > {

    private final SilentResponder responder = new SilentResponder() ;
    private final RecordingGuide guide = new RecordingGuide() ;

    public RecordingOnly() {
      super( any -> null ) ;
    }

    @Override
    protected Responder< INBOUND, Void > responder() {
      return responder ;
    }

    public void justRecord( final INBOUND inbound ) {
      responder.respond( inbound ) ;
    }

    @Override
    public Extractor< INBOUND > guide() {
      return guide ;
    }
  }
}
