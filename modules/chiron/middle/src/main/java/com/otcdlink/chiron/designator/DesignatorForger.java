package com.otcdlink.chiron.designator;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.toolbox.clock.Clock;
import org.joda.time.DateTime;

/**
 * Only for tests: creates arbitrary {@link Designator}s.
 */
public final class DesignatorForger {

  private DesignatorForger() { }

  public static StartStep newForger() {
    return new Progress( null, null, null, null, 0 ) ;
  }

  public interface StartStep extends CauseStep {
    CauseStep session( final SessionIdentifier sessionIdentifier ) ;
  }

  public interface CauseStep extends CommandTagStep {
    CommandTagStep cause( final Stamp cause ) ;
    Designator downwardFrom( final Designator upward, int counter ) ;
    Designator internalFrom( final Designator upward, int counter ) ;
  }

  public interface CommandTagStep extends InstantStep {
    InstantStep tag( final Command.Tag commandTag ) ;
  }

  public interface InstantStep {
    CounterStep instant( final DateTime instant ) ;

    /**
     * With a {@link DateTime} equal to {@link Stamp#FLOOR_MILLISECONDS} plus given amount of milliseconds.
     */
    CounterStep flooredInstant( final int plusMilliseconds ) ;

    CreationStep nextStamp( final Stamp stamp ) ;
  }

  public interface CounterStep extends CreationStep {
    CreationStep counter( final long counter ) ;
  }

  public interface CreationStep {
    Designator downward() ;
    Designator upward() ;
    Designator internal() ;
  }


  private static class Progress implements StartStep, CounterStep {

    private final SessionIdentifier sessionIdentifier ;
    private final Command.Tag commandTag ;
    private final Stamp cause ;
    private final DateTime instant ;
    private final long counter ;

    public Progress(
        final SessionIdentifier sessionIdentifier,
        final Command.Tag commandTag,
        final Stamp cause,
        final DateTime instant,
        final long counter
    ) {
      this.sessionIdentifier = sessionIdentifier ;
      this.commandTag = commandTag ;
      this.cause = cause ;
      this.instant = instant ;
      this.counter = counter ;
    }

    @Override
    public CommandTagStep cause( final Stamp cause ) {
      return new Progress(
          this.sessionIdentifier,
          this.commandTag,
          cause,
          this.instant,
          this.counter
      ) ;
    }

    @Override
    public Designator downwardFrom( final Designator upward, final int counter ) {
      return cause( upward.stamp )
          .tag( upward.tag )
          .instant( upward.stamp.timestampUtc() )
          .counter( counter )
          .downward()
      ;
    }

    @Override
    public Designator internalFrom( final Designator upward, final int counter ) {
      return cause( upward.stamp )
          .tag( upward.tag )
          .instant( upward.stamp.timestampUtc() )
          .counter( counter )
          .internal()
      ;
    }

    @Override
    public CauseStep tag( final Command.Tag commandTag ) {
      return new Progress(
          this.sessionIdentifier,
          commandTag,
          this.cause,
          this.instant,
          this.counter
      ) ;
    }

    @Override
    public CreationStep counter( final long counter ) {
      return new Progress(
          this.sessionIdentifier,
          this.commandTag,
          this.cause,
          this.instant,
          counter
      ) ;
    }

    @Override
    public CauseStep session( final SessionIdentifier sessionIdentifier ) {
      return new Progress(
          sessionIdentifier,
          this.commandTag,
          this.cause,
          this.instant,
          this.counter
      ) ;
    }

    @Override
    public CounterStep instant( final DateTime instant ) {
      return new Progress(
          this.sessionIdentifier,
          this.commandTag,
          this.cause,
          instant,
          this.counter
      ) ;
    }

    @Override
    public CounterStep flooredInstant( final int plusMilliseconds ) {
      return new Progress(
          this.sessionIdentifier,
          this.commandTag,
          this.cause,
          new DateTime( Stamp.FLOOR_MILLISECONDS + plusMilliseconds ),
          this.counter
      ) ;
    }

    @Override
    public CreationStep nextStamp( final Stamp stamp ) {
      final DateTime stampInstant = stamp.timestampUtc() ;
      long counterOfNext = -1 ;
      {
        Stamp next ;
        do {
          next = Stamp.raw( stampInstant.getMillis(), ++ counterOfNext ) ;
        } while( stamp.compareTo( next ) >= 0 ) ;
      }
      return new Progress(
          this.sessionIdentifier,
          this.commandTag,
          this.cause,
          stampInstant,
          counterOfNext
      ) ;
    }

    private Designator.Factory designatorFactory() {
      return new Designator.Factory(
          new InstrumentedStampGenerator( instant.getMillis(), counter ) ) ;
    }

    @Override
    public Designator downward() {
      return designatorFactory().downward( sessionIdentifier, cause, commandTag ) ;
    }

    @Override
    public Designator upward() {
      return designatorFactory().upward( commandTag, sessionIdentifier ) ;
    }

    @Override
    public Designator internal() {
      return designatorFactory().internal( cause, sessionIdentifier, commandTag ) ;
    }

  }

  private static class InstrumentedStampGenerator extends Stamp.Generator {

    private final long counter ;
    private final Clock clock ;

    public InstrumentedStampGenerator( final long instant, final long counter ) {
      super( () -> instant ) ;
      this.clock = () -> instant ;
      this.counter = counter ;
    }

    @Override
    public Stamp generate() {
      return Stamp.raw( clock.currentTimeMillis(), counter ) ;
    }
  }

}
