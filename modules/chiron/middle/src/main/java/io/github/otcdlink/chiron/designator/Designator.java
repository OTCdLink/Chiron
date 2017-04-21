package io.github.otcdlink.chiron.designator;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.Stamp;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.toolbox.ComparatorTools;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;

import java.util.Comparator;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Identifies a {@link Command} on Upend, and decorates it with additional information which
 * make no sense elsewhere.
 *
 * <h1>Design note: avoiding hyperspecialization</h1>
 * <p>
 * The {@link Designator} covers a wide range of use cases on Upend, so it is tempting to define
 * more specialized versions telling if there is a non-null {@link #sessionIdentifier} or whatever.
 * This has been a failure because it conflicted with genericity of Duty interfaces.
 * For instance there was an {@code Designator} enforcing {@link #sessionIdentifier} and
 * {@link #tag} non-nullity, but we also needed a class with a broader use (so was born
 * {@code Designator}) for all {@link Command}s to be persisted and processed in a single
 * sequential manner. Some of those {@code Designator} {@link Command}s had no
 * {@link #sessionIdentifier}. It was even more complicated with {@link Command}s reinjected by
 * Upend.
 *
 * <h1>How to check {@link Designator} compliance?</h1>
 * <p>
 * The good place for this is in concrete {@link Command} constructors.
 *
 * <h1>When to subclass</h1>
 * <p>
 * It makes sense to subclass a {@link Designator} for stuffing it with things attached to a
 * {@link Command} (or a {@link Command} which is a consequence of the former) that should not be
 * seen by most of code.
 *
 * <h1>How to not lose subclasses</h1>
 * <p>
 * A {@link Designator} causes the instantiation of other {@link Designator}s which should propagate
 * original informatation attached to a {@link Designator} subclasses. Such subclasses should
 * implement {@link Derivable} interface so they can handle instantiation on their own.
 */
public class Designator {


  public enum Kind {
    UPWARD, INTERNAL, DOWNWARD, ;
  }

  public final Kind kind ;

  /**
   * Never {@code null}, uniquely identifies a {@link Designator} and therefore associated
   * {@link Command}. Uniqueness is guaranteed only for {@link Designator}s from the same
   * {@link Factory}.
   */
  public final Stamp stamp ;

  /**
   * May be {@code null} for a {@link Command} created because of some external event, like a
   * User action, or a scheduled action.
   */
  public final Stamp cause ;

  /**
   * May be {@code null}, only {@link Command}s send by Downend should have a non-null
   * {@link Command.Tag}.
   * The need of keeping this field public is arguable because application layer should only
   * <i>chose</i> to propagate it, but the value itself has no interest.
   */
  public final Command.Tag tag ;

  /**
   * May be {@code null}, {@link Command}s send by Downend should have a non-null
   * {@link SessionIdentifier}.
   */
  public final SessionIdentifier sessionIdentifier ;

  protected Designator(
      final Kind kind,
      final Stamp stamp,
      final Stamp cause,
      final Command.Tag tag,
      final SessionIdentifier sessionIdentifier
  ) {
    if( kind == Kind.UPWARD || kind == Kind.DOWNWARD ) {
      checkNotNull( sessionIdentifier ) ;
    }
    this.sessionIdentifier = sessionIdentifier ;
    if( stamp.equals( cause ) ) {
      throw new IllegalArgumentException( "Stamp " + stamp + " same as cause" ) ;
    }
    if( tag != null ) {
      checkNotNull( sessionIdentifier, SessionIdentifier.class.getSimpleName() +
          " can't be null if there is a " + Command.Tag.class.getSimpleName() ) ;
    }
    this.kind = checkNotNull( kind ) ;
    this.stamp = checkNotNull( stamp ) ;
    this.cause = cause ;
    this.tag = tag ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }
    final Designator that = ( Designator ) other ;
    return COMMON_FIELDS_COMPARATOR.compare( this, that ) == 0 ;

  }

  @Override
  public int hashCode() {
    if( sessionIdentifier != null ) {
      // This is useful when gathering Commands per SessionIdentifier, so we can apply throtting.
      // TODO: remove this branch after moving throttling to Netty's ChannelHandler.
      return sessionIdentifier.hashCode() ;
    } else {
      int result = stamp.hashCode() ;
      result = 31 * result + ( cause != null ? cause.hashCode() : 0 ) ;
      result = 31 * result + ( tag != null ? tag.hashCode() : 0 ) ;
      result = 31 * result + ( sessionIdentifier != null ? sessionIdentifier.hashCode() : 0 ) ;
      return result ;
    }
  }

  protected void addToStringBody( final StringBuilder stringBuilder ) {
    stringBuilder
        .append( "kind=" )
        .append( kind.name() )
        .append( ";stamp=" )
        .append( stamp.asStringRoundedToFlooredSecond() )
    ;
    if( cause != null ) {
      stringBuilder.append( ";cause=" ).append( cause.asStringRoundedToFlooredSecond() ) ;
    }
    if( tag != null ) {
      stringBuilder.append( ";tag=" ).append( tag.asString() ) ;
    }
    if( sessionIdentifier != null ) {
      stringBuilder.append( ";session=" ).append( sessionIdentifier.asString() ) ;
    }
  }

  @Override
  public final String toString() {
    final StringBuilder stringBuilder = new StringBuilder( ToStringTools.getNiceClassName( this ) ) ;
    stringBuilder.append( '{' ) ;
    addToStringBody( stringBuilder ) ;
    stringBuilder.append( '}' ) ;
    return stringBuilder.toString() ;
  }

  /**
   * Production code should not use this object.
   * Performs comparison on fields but doesn't care about actual class of compared objects.
   * Use {@link #equals(Object)} to perform class check.
   */
  public static final Comparator< Designator > COMMON_FIELDS_COMPARATOR =
      new ComparatorTools.WithNull< Designator> () {
        @Override
        protected int compareNoNulls( final Designator first, final Designator second ) {
          final int uniqueTimestampComparison =
              first.stamp.compareTo( second.stamp ) ;
          if( uniqueTimestampComparison == 0 ) {
            final int causeComparison =
                Stamp.COMPARATOR.compare( first.cause, second.cause ) ;
            if( causeComparison == 0 ) {
              final int commandTagComparison =
                  Command.Tag.COMPARATOR.compare( first.tag, second.tag ) ;
              if( commandTagComparison == 0 ) {
                final int sessionIdentifierComparison = SessionIdentifier.COMPARATOR.compare(
                    first.sessionIdentifier, second.sessionIdentifier ) ;
                return sessionIdentifierComparison ;
              } else {
                return commandTagComparison ;
              }
            } else {
              return causeComparison ;
            }
          } else {
            return uniqueTimestampComparison ;
          }
        }
      }
  ;


  /**
   * Subclasses of {@link Designator} must implement this interface so can propagate their own
   * field during instantiation.
   */
  public interface Derivable< DESIGNATOR extends  Designator > {
    DESIGNATOR derive(
        Kind newKind,
        Stamp newStamp
    ) ;
    DESIGNATOR derive(
        Kind newKind,
        Stamp newStamp,
        Command.Tag newTag,
        SessionIdentifier newSessionIdentifier
    ) ;
  }


  public interface FactoryForInternal {

    Designator internal() ;

    RenderingAwareDesignator phasing(
        RichHttpRequest richHttpRequest,
        BiFunction<RichHttpRequest, Object, Object > renderer
    ) ;
  }

  public static class Factory implements FactoryForInternal {

    private final Stamp.Generator uniqueTimestampGenerator ;

    public Factory( final Stamp.Generator uniqueTimestampGenerator ) {
      this.uniqueTimestampGenerator = checkNotNull( uniqueTimestampGenerator ) ;
    }

    public Designator upward(
        final Command.Tag tag,
        final SessionIdentifier sessionIdentifier
    ) {
      return new Designator(
          Kind.UPWARD, uniqueTimestampGenerator.generate(), null, tag, sessionIdentifier ) ;
    }

    public Designator downward( final Designator designator ) {
      if( designator instanceof Derivable ) {
        return ( ( Derivable ) designator )
            .derive( Kind.DOWNWARD, uniqueTimestampGenerator.generate() ) ;
      } else {
        return new Designator(
            Kind.DOWNWARD,
            uniqueTimestampGenerator.generate(),
            designator.stamp,
            designator.tag,
            designator.sessionIdentifier
        ) ;
      }
    }

    public Designator downward(
        final SessionIdentifier sessionIdentifier,
        final Stamp cause,
        final Command.Tag commandTag
    ) {
      return new Designator(
          Kind.DOWNWARD,
          uniqueTimestampGenerator.generate(),
          cause,
          commandTag,
          sessionIdentifier
      ) ;
    }

    public Designator downward(
        final SessionIdentifier sessionIdentifier,
        final Stamp cause
    ) {
      return new Designator(
          Kind.DOWNWARD,
          uniqueTimestampGenerator.generate(),
          cause,
          null,
          sessionIdentifier
      ) ;
    }

    @Override
    public Designator internal() {
      return new Designator(
          Kind.INTERNAL, uniqueTimestampGenerator.generate(), null, null, null ) ;
    }

    @Override
    public RenderingAwareDesignator phasing(
        final RichHttpRequest richHttpRequest,
        final BiFunction<RichHttpRequest, Object, Object > renderer
    ) {
      return new RenderingAwareDesignator(
          Kind.INTERNAL,
          uniqueTimestampGenerator.generate(),
          null,
          null,
          null,
          checkNotNull( richHttpRequest ),
          checkNotNull( renderer )
      ) ;
    }

    /**
     * Creates a derived {@link Designator} of {@link Kind#INTERNAL} with
     * no {@link #sessionIdentifier} and no {@link #tag}. TODO: rename to {@code internalAnonymous}.
     */
    public Designator internalZero( final Designator cause ) {
      if( cause instanceof Derivable ) {
        return ( ( Derivable ) cause ).derive(
            Kind.INTERNAL,
            uniqueTimestampGenerator.generate(),
            null,
            null
        ) ;
      } else {
        return new Designator(
            Kind.INTERNAL,
            uniqueTimestampGenerator.generate(),
            cause.stamp,
            null,
            null
        ) ;

      }
    }

    public Designator internal(
        final Stamp cause,
        final SessionIdentifier sessionIdentifier,
        final Command.Tag tag
    ) {
      return new Designator(
          Kind.INTERNAL,
          uniqueTimestampGenerator.generate(),
          cause,
          tag,
          sessionIdentifier
      ) ;
    }

    public Designator internal( final SessionIdentifier sessionIdentifier ) {
      return new Designator(
          Kind.INTERNAL,
          uniqueTimestampGenerator.generate(),
          null,
          null,
          sessionIdentifier
      ) ;
    }

    public Designator internal( final Designator cause ) {
      if( cause instanceof Derivable ) {
        return ( ( Derivable ) cause ).derive(
            Kind.INTERNAL,
            uniqueTimestampGenerator.generate(),
            cause.tag,
            cause.sessionIdentifier
        ) ;
      } else {
        return internal( cause.stamp, cause.sessionIdentifier, cause.tag ) ;
      }
    }

  }

}
