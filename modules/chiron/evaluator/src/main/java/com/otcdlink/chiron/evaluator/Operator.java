package com.otcdlink.chiron.evaluator;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.ToStringTools;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Applies an evaluation bi-function to a {@link PARAMETER} captured by
 * {@link Evaluator#field(QueryField, Operator, Object)}, and a {@link VALUE} obtained from one
 * queried object with an {@link Extractor}.
 *
 * <h1>Alternate names</h1>
 * <ul>
 *   <li>
 *     Matcher, already used a lot of times.
 *   </li><li>
 *     Likener, not bad but introduces a mistaking notion of fuzziness.
 *   </li>
 * </ul>
 *
 * @param <PARAMETER> type in the query.
 * @param <VALUE> type in queried data.
 */
public interface Operator< PARAMETER, VALUE > {

  String name() ;

  /**
   * Nicer representation used in {@link Evaluator#print(Appendable)}.
   */
  default String symbol() {
    return name() ;
  }

  /**
   * Evaluate {@code value} against query's {@code parameter}.
   * <p>
   * Implementors should take in consideration that "instictive" notation for a query is something
   * like:
   * <pre>
   *   value [operator] parameter
   * </pre>
   * Just like this:
   * <pre>
   *   creation-date >= 2012-01-10
   * </pre>
   * This can lead to some inverted notation for non-reflexive operations.
   */
  boolean apply( PARAMETER parameter, VALUE value ) ;


  /**
   * A hook inside an {@link Evaluator} to transform a context-free {@link Operator}
   * (usually declared as an enum member) into a context-aware one.
   * For instance the context could be the wall clock time during evaluation for a time-related
   * field (supporting an expression like {@code somefield == now}).
   */
  interface Contextualizer {

    /**
     * May replace an {@link Operator} with a context-aware one.
     *
     * @return a non-{@code null} value which can be the {@code operator} given in input.
     */
    Operator contextualize( Operator operator ) ;

    Contextualizer NULL = new Contextualizer() {
      @Override
      public Operator contextualize( final Operator operator ) {
        return operator ;
      }
      @Override
      public String toString() {
        return ToStringTools.getNiceName( Contextualizer.class ) + "{NULL}" ;
      }
    } ;


    final class Composite implements Contextualizer {

      private final ImmutableList< Contextualizer > contextualizers ;

      public static Composite newComposite( final Contextualizer... contextualizers ) {
        return new Composite( contextualizers ) ;
      }

      public Composite( final Contextualizer... contextualizers ) {
        this( ImmutableList.copyOf( contextualizers ) ) ;
      }

      public Composite( final ImmutableList< Contextualizer > contextualizers ) {
        this.contextualizers = contextualizers ;
      }

      @Override
      public Operator contextualize( final Operator operator ) {
        for( final Contextualizer contextualizer : contextualizers ) {
          final Operator contextualizedMaybe = contextualizer.contextualize( operator ) ;
          if( contextualizedMaybe != operator ) {
            return contextualizedMaybe ;
          }
        }
        return operator ;
      }
    }
  }

  abstract class Delegator< PARAMETER, VALUE > implements Operator< PARAMETER, VALUE > {

    protected final Operator< PARAMETER, VALUE > delegate ;

    public Delegator( final Operator< PARAMETER, VALUE > delegate ) {
      this.delegate = checkNotNull( delegate ) ;
    }

    @Override
    public String name() {
      return delegate.name() ;
    }

    @Override
    public String symbol() {
      return delegate.symbol() ;
    }

  }

  /**
   * Thrown when calling {@link Operator#apply(Object, Object)} on an {@link Operator} that
   * can not be used without context given by {@link Contextualizer}.
   */
  final class MissingContextException extends RuntimeException {
    public MissingContextException( final String message ) {
      super( message );
    }
  }

}
