package com.otcdlink.chiron.evaluator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

/**
 * Builds an evaluation tree acting like a predicate over instances of {@link ENTITY}.
 * What can be done on an {@link ENTITY} is defined through specific values of {@link QueryField}.
 * An evaluation of a {@link QueryField} is defined by {@link #field(QueryField, Operator, Object)}.
 * Those evaluations can be combined with boolean operators.
 *
 * @see #evaluate(Object)
 */
public abstract class Evaluator< ENTITY, QUERYFIELD extends QueryField< ENTITY, ?, ?, ? > > {

  public final Kind kind ;
  final ImmutableSet< QUERYFIELD > queryFields ;
  private final Operator.Contextualizer contextualizer ;

  private Evaluator(
      final Kind kind,
      final ImmutableSet< QUERYFIELD > queryFields,
      final Operator.Contextualizer contextualizer
  ) {
    this.kind = checkNotNull( kind ) ;
    this.queryFields = checkNotNull( queryFields ) ;
    this.contextualizer = checkNotNull( contextualizer ) ;

    final int distinctFieldNameCount = queryFields.stream()
        .distinct()
        .collect( Collectors.toList() )
        .size()
    ;
    checkArgument( distinctFieldNameCount == queryFields.size(),
        "There are colliding names in " + queryFields ) ;
  }

  public abstract boolean evaluate( final ENTITY entity ) ;

// ========
// Printing
// ========

  @Override
  public final String toString() {
    return Evaluator.class.getSimpleName() + "{" + asString() + "}" ;
  }

  public final String asString() {
    return asString( EvaluatorPrinter.Setup.SINGLE_LINE ) ;
  }

  public final String asString( final EvaluatorPrinter.Setup printSetup ) {
    final StringBuilder stringBuilder = new StringBuilder() ;
    try {
      print( printSetup, stringBuilder ) ;
    } catch( IOException e ) {
      throw new RuntimeException( "Should not happen with " + stringBuilder, e ) ;
    }
    return stringBuilder.toString() ;
  }

  public final void print( final Appendable appendable ) throws IOException {
    new EvaluatorPrinter( EvaluatorPrinter.Setup.MULTILINE ).print( this, appendable ) ;
  }

  public final void print(
      final EvaluatorPrinter.Setup printSetup,
      final Appendable appendable
  ) throws IOException {
    new EvaluatorPrinter( printSetup ).print( this, appendable ) ;
  }



// =======
// Priming
// =======

  public static <
      ENTITY,
      QUERYFIELD extends QueryField< ENTITY, ?, ?, ? >
  > Evaluator< ENTITY, QUERYFIELD > empty(
      final ImmutableSet< QUERYFIELD > fields
  ) {
    return new Empty<>( fields, Operator.Contextualizer.NULL ) ;
  }

  public static <
      ENTITY,
      QUERYFIELD extends QueryField< ENTITY, ?, ?, ? >
  > Evaluator< ENTITY, QUERYFIELD > empty(
      final ImmutableSet< QUERYFIELD > fields,
      final Operator.Contextualizer contextualizer
  ) {
    return new Empty<>( fields, contextualizer ) ;
  }

  private static class Empty< ENTITY, QUERYFIELD extends QueryField< ENTITY, ?, ?, ? > >
      extends Evaluator< ENTITY, QUERYFIELD >
  {
    private Empty(
        final ImmutableSet< QUERYFIELD > fields,
        final Operator.Contextualizer contextualizer
    ) {
      super( Kind.EMPTY, fields, contextualizer ) ;
    }

    @Override
    public boolean evaluate( ENTITY entity ) {
      return true ;
    }

  }

// ====
// Just
// ====

  private void checkKnownQueryField( final QueryField queryfield ) {
    checkArgument( queryFields.contains( queryfield ),
        "Does not contain " + queryfield + ": " + queryFields ) ;
  }

  public <
      SOME_QUERYFIELD extends QueryField< ENTITY, PARAMETER, ?, VALUE >,
      OPERATOR extends Operator< PARAMETER, VALUE >,
      PARAMETER,
      VALUE
  >
  Evaluator< ENTITY, QUERYFIELD > field(
      final SOME_QUERYFIELD queryfield,
      final OPERATOR operator,
      final PARAMETER parameter
  ) {
    checkKnownQueryField( queryfield ) ;
    final OPERATOR contextualized = contextualize( operator ) ;
    return ( Evaluator< ENTITY, QUERYFIELD > )
        new Just( queryFields, contextualizer, queryfield, contextualized, parameter ) ;
  }

  protected <
      OPERATOR extends Operator< PARAMETER, VALUE >,
      PARAMETER,
      VALUE
  > OPERATOR contextualize(
      final OPERATOR operator
  ) {
    return ( OPERATOR ) contextualizer.contextualize( operator ) ;
  }

  static class Just<
      ENTITY,
      QUERYFIELD extends QueryField< ENTITY, PARAMETER, OPERATOR, VALUE >,
      PARAMETER,
      OPERATOR extends Operator< PARAMETER, VALUE >,
      VALUE
  > extends Evaluator< ENTITY, QUERYFIELD > {
    final QUERYFIELD queryfield ;
    final Object parameter ;
    final OPERATOR operator ;

    private Just(
        final ImmutableSet< QUERYFIELD > fields,
        final Operator.Contextualizer contextualizer,
        final QUERYFIELD queryfield,
        final OPERATOR operator,
        final Object parameter
    ) {
      super( Kind.FIELD, fields, contextualizer ) ;
      this.queryfield = checkNotNull( queryfield ) ;
      this.operator = checkNotNull( operator ) ;
      this.parameter = parameter ;
    }

    @Override
    public boolean evaluate( final ENTITY entity ) {
      final Object value = queryfield.extractor.extract( entity ) ;
      return ( ( Operator ) operator ).apply( parameter, value ) ;
    }

  }

// =========
// Composite
// =========


  final Evaluator< ENTITY, QUERYFIELD > combine(
      final Kind combination,
      final ImmutableList< Evaluator< ENTITY, QUERYFIELD > > children
  ) {
    checkArgument( combination.combinating,
        "Not a combinating " + Kind.class.getSimpleName() + ": " + kind ) ;
    return new Combinator<>(
        combination,
        queryFields,
        contextualizer,
        children
    ) ;
  }

  public Evaluator< ENTITY, QUERYFIELD > negate() {
    return new Combinator<>( Kind.NOT, queryFields, contextualizer, ImmutableList.of( this ) ) ;
  }


  public final Evaluator< ENTITY, QUERYFIELD > and(
      final Evaluator< ENTITY, QUERYFIELD > other
  ) {
    return new Combinator<>(
        Kind.AND,
        queryFields,
        contextualizer,
        ImmutableList.of( this, other )
    ) ;
  }

  public final Evaluator< ENTITY, QUERYFIELD > or(
      final Evaluator< ENTITY, QUERYFIELD > other
  ) {
    return new Combinator<>(
        Kind.OR,
        queryFields,
        contextualizer,
        ImmutableList.of( this, other )
    ) ;
  }


  /**
   * Factors AND and OR operations.
   */
  static class Combinator<
      ENTITY,
      QUERYFIELD extends QueryField< ENTITY, ?, ?, ? >
  > extends Evaluator< ENTITY, QUERYFIELD > {
    final ImmutableList< Evaluator< ENTITY, QUERYFIELD > > children ;

    private Combinator(
        final Kind kind,
        final ImmutableSet< QUERYFIELD > fields,
        final Operator.Contextualizer contextualizer,
        final ImmutableList< Evaluator< ENTITY, QUERYFIELD > > children
    ) {
      super( kind, fields, contextualizer ) ;
      checkArgument( kind.combinating, "Unsupported for " + Combinator.class.getSimpleName() +
          ": " + kind ) ;
      if( kind == Kind.NOT ) {
        checkArgument( children.size() == 1 ) ;
      }
      this.children = checkNotNull( children ) ;
    }

    @Override
    public boolean evaluate( final ENTITY entity ) {
      if( kind == Kind.NOT ) {
        return ! children.get( 0 ).evaluate( entity ) ;
      } else {
        for( final Evaluator< ENTITY, QUERYFIELD > child : children ) {
          if( child.evaluate( entity ) ) {
            if( kind == Kind.OR ) {
              return true ;
            }
          } else {
            if( kind == Kind.AND ) {
              return false ;
            }
          }
        }
      }
      return kind == Kind.AND ;
    }
  }


// ====
// Kind
// ====

  public enum Kind {
    EMPTY,
    FIELD,
    NOT( true ),
    AND( true ),
    OR( true ),
    ;
    public final boolean combinating ;

    Kind() {
      this( false ) ;
    }

    Kind( final boolean combinating ) {
      this.combinating = combinating ;
    }

    public static final ImmutableSet< Kind > COMBINATING =
        Stream.of( Kind.values() ).filter( k -> k.combinating ).collect( toImmutableSet() ) ;
  }


}
