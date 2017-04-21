package io.github.otcdlink.chiron.reactor;

import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.toolbox.catcher.Catcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class FakeStage extends Stage {

  public final Setup setup ;
  protected final Logger logger = LoggerFactory.getLogger( getClass() ) ;

  private final AtomicInteger safeCallCount = new AtomicInteger( 0 ) ;
  private int unsafeCallCount = 0 ;

  private final BlockingQueue< FakeCommand > queue ;
  private volatile Thread callingThread = null ;

  public volatile boolean fail = false ;

  protected FakeStage( final Setup setup ) {
    this.setup = checkNotNull( setup ) ;
    queue = setup.recording ? new LinkedBlockingQueue<>() : null ;
  }
  
  public static final class Setup {
    public final boolean recording ;
    public final boolean verbose ;
    public final boolean alwaysSameCallingThread ;
    public final boolean plannedFailure ;

    public Setup(
        final boolean recording,
        final boolean verbose,
        final boolean alwaysSameCallingThread,
        final boolean plannedFailure
    ) {
      this.recording = recording ;
      this.verbose = verbose ;
      this.alwaysSameCallingThread = alwaysSameCallingThread ;
      this.plannedFailure = plannedFailure ;
    }

    public static final Setup DEFAULT = new Setup( true, true, false, false ) ;
    public static final Setup FAILABLE = new Setup( true, true, false, true ) ;
    public static final Setup FASTEST = new Setup( false, false, true, false ) ;
  }

  protected final void record( final FakeCommand fakeCommand ) {
    if( setup.alwaysSameCallingThread ) {
      if( callingThread == null ) {
        callingThread = Thread.currentThread() ;
      } else {
        checkState( callingThread == Thread.currentThread() ) ;
      }
      unsafeCallCount ++ ;
    } else {
      safeCallCount.incrementAndGet() ;
    }
    if( setup.recording ) {
      queue.add( fakeCommand ) ;
    }

    if( setup.verbose && logger.isDebugEnabled() ) {
      logger.debug( "Received " + fakeCommand + "." ) ;
    }

    if( setup.plannedFailure && fail ) {
      throw new PlannedException( this, fakeCommand ) ;
    }
  }

  public final FakeCommand nextRecordedCommand() throws InterruptedException {
    return queue.take() ;
  }

  public final boolean hasNextRecordedCommand() throws InterruptedException {
    return ! queue.isEmpty() ;
  }

  public int callCount() {
    if( setup.alwaysSameCallingThread ) {
      return unsafeCallCount ;
    } else {
      return safeCallCount.get() ;
    }
  }

  public static class HttpUpward extends FakeStage implements Charger< FakeCommand > {
    public HttpUpward( final Setup setup ) {
      super( setup ) ;
    }
  }
  
  public static class HttpDownward extends FakeStage implements Stage.Absorber< FakeCommand > {
    public HttpDownward( final Setup setup ) {
      super( setup ) ;
    }
    @Override
    public void accept( final FakeCommand fakeCommand ) {
      record( fakeCommand ) ;
    }
  }

  public static class Logic extends FakeStage implements Stage.Spreader< FakeCommand > {
    public Logic( final Setup setup ) {
      super( setup ) ;
    }
    @Override
    public ImmutableList< FakeCommand > apply( final FakeCommand fakeCommand ) {
      record( fakeCommand ) ;

      switch( fakeCommand.kind ) {
        case UPWARD_GLOBAL_ACTIVATION :
          return ImmutableList.of( new FakeCommand(
              FakeCommand.Kind.DOWNWARD_START_GLOBAL_ACTIVATION, null, fakeCommand ) ) ;
        case UPWARD_MONITORING :
          return ImmutableList.of(
              new FakeCommand( FakeCommand.Kind.DOWNWARD_MONITORING, null, fakeCommand ) ) ;
        case UPWARD_PROPOSAL :
          final ImmutableList.Builder< FakeCommand > builder = ImmutableList.builder() ;
          for( int i = 0 ; i < fakeCommand.parameter ; i ++ ) {
            builder.add( new FakeCommand( FakeCommand.Kind.DOWNWARD_PROPOSAL, i, fakeCommand ) ) ;
          }
          final ImmutableList< FakeCommand > commands = builder.build() ;
          if( logger.isTraceEnabled() ) {
            logger.trace( "Generated " + commands + "." ) ;
          }
          return commands ;
        case INTERNAL_DAYBREAK :
          return ImmutableList.of() ;
        case UPWARD_PRECONFIRMATION :
          return ImmutableList.of( new FakeCommand(
              FakeCommand.Kind.INTERNAL_SEND_EMAIL, fakeCommand.parameter, fakeCommand ) ) ;
        case UPWARD_CHANGE_PASSWORD :
          return ImmutableList.of( new FakeCommand(
              FakeCommand.Kind.INTERNAL_HASH_PASSWORD, fakeCommand.parameter, fakeCommand ) ) ;
        case INTERNAL_CHANGE_PASSWORD :
          return ImmutableList.of( new FakeCommand(
              FakeCommand.Kind.DOWNWARD_CHANGE_PASSWORD, fakeCommand.parameter, fakeCommand ) ) ;
        case INTERNAL_SET_THROTTLING :
          return ImmutableList.of( new FakeCommand(
              FakeCommand.Kind.INTERNAL_THROTTLER_DELAY, fakeCommand.parameter, fakeCommand ) ) ;
        case INTERNAL_SESSION_CREATE :
          return ImmutableList.of( new FakeCommand(
              FakeCommand.Kind.INTERNAL_SESSION_CREATED,
              fakeCommand.parameter,
              fakeCommand
          ) ) ;
        default :
          throw new IllegalArgumentException( "Unsupported: " + fakeCommand ) ;
      }
    }
  }

  public static class Persister extends FakeStage implements Stage.Spreader< FakeCommand > {
    public Persister( final Setup setup ) {
      super( setup ) ;
    }
    @Override
    public ImmutableList< FakeCommand > apply( final FakeCommand fakeCommand ) {
      record( fakeCommand ) ;
      return SUCCESS ;
    }
    public static final ImmutableList< FakeCommand > SUCCESS =
        ImmutableList.of( new FakeCommand( FakeCommand.Kind.MAGIC_PERSISTENCE_SUCCESS ) ) ;
  }
  
  public static class Replicator extends FakeStage implements Stage.Spreader< FakeCommand > {
    public Replicator( final Setup setup ) {
      super( setup ) ;
    }
    @Override
    public ImmutableList< FakeCommand > apply( final FakeCommand fakeCommand ) {
      record( fakeCommand ) ;
      return SUCCESS ;
    }
    public static final ImmutableList< FakeCommand > SUCCESS =
        ImmutableList.of( new FakeCommand( FakeCommand.Kind.MAGIC_PERSISTENCE_SUCCESS ) ) ;
  }

  public static class PasswordHasher extends FakeStage
      implements Stage.Transformer< FakeCommand >
  {
    public PasswordHasher( final Setup setup ) {
      super( setup ) ;
    }
    @Override
    public FakeCommand apply( final FakeCommand fakeCommand ) {
      record( fakeCommand ) ;
      checkArgument( fakeCommand.kind == FakeCommand.Kind.INTERNAL_HASH_PASSWORD ) ;
      return new FakeCommand(
          FakeCommand.Kind.INTERNAL_CHANGE_PASSWORD,
          fakeCommand.parameter * 1000,
          fakeCommand
      ) ;
    }
  }

  public static class EmailSender extends FakeStage implements Stage.Absorber< FakeCommand > {
    public EmailSender( final Setup setup ) {
      super( setup ) ;
    }
    @Override
    public void accept( final FakeCommand fakeCommand ) {
      record( fakeCommand ) ;
    }
  }

  public static class Twilio extends FakeStage {
    public Twilio( final Setup setup ) {
      super( setup ) ;
    }
  }

  public static class Throttler extends FakeStage
      implements Stage.Transformer< FakeCommand >
  {
    public volatile boolean refuse = false ;

    public Throttler( final Setup setup ) {
      super( setup ) ;
    }
    @Override
    public FakeCommand apply( final FakeCommand fakeCommand ) {
      record( fakeCommand ) ;
      if( refuse ) {
        return FakeCommand.downwardFailure() ;
      } else {
        switch( fakeCommand.kind ) {
          case INTERNAL_THROTTLER_DELAY :
            return null ;
          default :
            return fakeCommand ;
        }
      }
    }
  }
  public static class SessionSupervisor extends FakeStage
      implements Stage.Absorber< FakeCommand >
  {
    public SessionSupervisor( final Setup setup ) {
      super( setup ) ;
    }
    @Override
    public void accept( final FakeCommand fakeCommand ) {
      record( fakeCommand ) ;
      checkArgument(
          fakeCommand.kind == FakeCommand.Kind.INTERNAL_SESSION_CREATED
      ) ;
    }
  }

  public static class PlannedException extends RuntimeException {
    public PlannedException( final FakeStage emitter, final FakeCommand command ) {
      super( "Emitted by " + emitter + " with " + command ) ;
    }
  }


  /**
   * Covariant return types are our friends.
   */
  public static class Pack extends StagePack< FakeCommand > {
    public Pack(
        final Catcher catcher,
        final Charger< FakeCommand > httpUpward,
        final Absorber< FakeCommand > httpDownward,
        final Spreader< FakeCommand > logic,
        final Spreader< FakeCommand > persister,
        final Spreader< FakeCommand > replicator,
        final Transformer< FakeCommand > passwordHasher,
        final Absorber< FakeCommand > emailSender,
        final Transformer< FakeCommand > throttler,
        final Absorber< FakeCommand > sessionSupervisor
    ) {
      super(
          catcher,
          ( e, command ) -> FakeCommand.downwardFailure(),
          httpUpward,
          httpDownward,
          logic,
          persister,
          passwordHasher,
          emailSender,
          throttler,
          sessionSupervisor
      ) ;
    }

    @Override
    public HttpUpward httpUpward() {
      return ( HttpUpward ) super.httpUpward() ;
    }

    @Override
    public HttpDownward httpDownward() {
      return ( HttpDownward ) super.httpDownward() ;
    }

    @Override
    public Logic logic() {
      return ( Logic ) super.logic() ;
    }

    @Override
    public Persister persister() {
      return ( Persister ) super.persister() ;
    }

    @Override
    public PasswordHasher passwordHasher() {
      return ( PasswordHasher ) super.passwordHasher() ;
    }

    @Override
    public EmailSender emailSender() {
      return ( EmailSender ) super.emailSender() ;
    }

    @Override
    public SessionSupervisor sessionSupervisor() {
      return ( SessionSupervisor ) super.sessionSupervisor() ;
    }

    @Override
    public Throttler throttler() {
      return ( Throttler ) super.throttler() ;
    }

    @Override
    public boolean isInternalPasswordStuff( final FakeCommand fakeCommand ) {
      return fakeCommand.kind == FakeCommand.Kind.INTERNAL_HASH_PASSWORD ;
    }

    @Override
    public boolean isInternalPasswordTransformation( final FakeCommand command ) {
      return command.kind == FakeCommand.Kind.INTERNAL_HASH_PASSWORD ||
          command.kind == FakeCommand.Kind.INTERNAL_CHANGE_PASSWORD ;
    }

    @Override
    public boolean isInternalEmailStuff( final FakeCommand fakeCommand ) {
      return fakeCommand.kind == FakeCommand.Kind.INTERNAL_SEND_EMAIL ;
    }

    @Override
    public boolean isInternalThrottlingDuration( final FakeCommand fakeCommand ) {
      return fakeCommand.kind == FakeCommand.Kind.INTERNAL_SET_THROTTLING ;
    }

    @Override
    public boolean isInternalSessionStuff( final FakeCommand command ) {
      return
          command.kind == FakeCommand.Kind.INTERNAL_SESSION_CREATED
      ;
    }

    @Override
    public boolean isInternalThrottlerDelay( final FakeCommand fakeCommand ) {
      return fakeCommand.kind == FakeCommand.Kind.INTERNAL_THROTTLER_DELAY ;
    }

    @Override
    public boolean isFailureCommand( final FakeCommand command ) {
      return command.kind == FakeCommand.Kind.DOWNWARD_FAILURE ;
    }
  }


  public static Pack newCasting( final Catcher catcher, final Setup setup ) {

    return new Pack(
        catcher,
        new HttpUpward( setup ),
        new HttpDownward( setup ),
        new Logic( setup ),
        new Persister( setup ),
        null,
        new PasswordHasher( setup ),
        new EmailSender( setup ),
        new Throttler( setup ),
        new SessionSupervisor( setup )
    ) {
    } ;
  }

  public static Pack newCastingWithReplicator( final Catcher catcher, final Setup setup ) {
    return new Pack(
        catcher,
        new HttpUpward( setup ),
        new HttpDownward( setup ),
        new Logic( setup ),
        new Persister( setup ),
        new Replicator( setup ),
        new PasswordHasher( setup ),
        new EmailSender( setup ),
        new Throttler( setup ),
        new SessionSupervisor( setup )
    ) {
    } ;
  }

}
