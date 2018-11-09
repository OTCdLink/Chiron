package com.otcdlink.chiron.evaluator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

public abstract class Evaluator<
    ENTITY,
    ENTITY_CONTEXT,
    QUERYFIELD extends QueryField< ENTITY, ENTITY_CONTEXT, ?, ?, ?, ? >
> {

  public final Kind kind ;
  final ENTITY_CONTEXT entityContext ;
  public final ImmutableSet< QUERYFIELD > queryFields ;

  public Evaluator(
      final Kind kind,
      final ENTITY_CONTEXT entityContext,
      final ImmutableSet< QUERYFIELD > queryFields ) {
    this.kind = checkNotNull( kind ) ;
    this.entityContext = entityContext ;
    this.queryFields = checkNotNull( queryFields ) ;

    final int distinctFieldNameCount = queryFields.stream()
        .distinct()
        .collect( Collectors.toList() )
        .size()
    ;
    checkArgument( distinctFieldNameCount == queryFields.size(),
        "There are colliding names in " + queryFields ) ;
  }

  public abstract boolean evaluate( final ENTITY entity ) ;


// =======
// Priming
// =======

  public static <
      ENTITY,
      ENTITY_CONTEXT,
      QUERYFIELD extends QueryField< ENTITY, ENTITY_CONTEXT, ?, ?, ?, ? >
  > Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > empty(
      final ENTITY_CONTEXT entityContext,
      final ImmutableSet< QUERYFIELD > fields
  ) {
    return new Empty<>( entityContext, fields ) ;
  }

  private static class Empty<
      ENTITY,
      ENTITY_CONTEXT,
      QUERYFIELD extends QueryField< ENTITY, ENTITY_CONTEXT, ?, ?, ?, ? >
  > extends Evaluator< ENTITY, ENTITY_CONTEXT,QUERYFIELD > {
    private Empty(
        final ENTITY_CONTEXT entityContext,
        final ImmutableSet< QUERYFIELD > fields
    ) {
      super( Kind.EMPTY, entityContext, fields ) ;
    }

    @Override
    public boolean evaluate( ENTITY entity ) {
      return true ;
    }

  }



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



// ====
// Just
// ====

  private void checkKnownQueryField( final QueryField queryfield ) {
    checkArgument( queryFields.contains( queryfield ),
        "Does not contain " + queryfield + ": " + queryFields ) ;
  }

  public <
      SOME_QUERYFIELD extends QueryField<
                ENTITY,
                ENTITY_CONTEXT,
                OPERATOR,
                OPERATOR_CONTEXT,
                PARAMETER,
                VALUE
            >,
      OPERATOR_CONTEXT,
      OPERATOR extends Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
      PARAMETER,
      VALUE
  >
  Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > field(
      final SOME_QUERYFIELD queryfield,
      final OPERATOR operator,
      final PARAMETER parameter
  ) {
    checkKnownQueryField( queryfield ) ;
    return new ForField( entityContext, queryFields, queryfield, operator, parameter ) ;
  }

  public static class ForField<
      ENTITY,
      ENTITY_CONTEXT,
      OPERATOR extends Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
      OPERATOR_CONTEXT,
      QUERYFIELD extends QueryField<
          ENTITY,
          ENTITY_CONTEXT,
          OPERATOR,
          OPERATOR_CONTEXT,
          PARAMETER,
          VALUE
      >,
      PARAMETER,
      VALUE
  > extends Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > {
    public final QUERYFIELD queryfield ;
    public final PARAMETER parameter ;
    public final OPERATOR operator ;

    private ForField(
        final ENTITY_CONTEXT entityContext,
        final ImmutableSet< QUERYFIELD > fields,
        final QUERYFIELD queryfield,
        final OPERATOR operator,
        final PARAMETER parameter
    ) {
      super( Kind.FIELD, entityContext, fields ) ;
      this.queryfield = checkNotNull( queryfield ) ;
      this.operator = checkNotNull( operator ) ;
      this.parameter = parameter ;
    }

    @Override
    public boolean evaluate( final ENTITY entity ) {
      final OPERATOR_CONTEXT operatorContext =
          queryfield.operatorContextExtractor().apply( entityContext ) ;
      final VALUE value = queryfield.valueExtractor().apply( entity ) ;
      return operator.apply( operatorContext, parameter, value ) ;
    }

  }


// =========
// Composite
// =========


  final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > combine(
      final Kind combination,
      final ImmutableList<Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD >> children
  ) {
    checkArgument( combination.combinating,
        "Not a combinating " + Kind.class.getSimpleName() + ": " + kind ) ;
    return new Combinator<>(
        combination,
        entityContext,
        queryFields,
        children
    ) ;
  }

  public Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > negate() {
    return new Combinator<>( Kind.NOT, entityContext, queryFields, ImmutableList.of( this ) ) ;
  }


  public final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > and(
      final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > other
  ) {
    return new Combinator<>(
        Kind.AND,
        entityContext,
        queryFields,
        ImmutableList.of( this, other )
    ) ;
  }

  public final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > or(
      final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > other
  ) {
    return new Combinator<>(
        Kind.OR,
        entityContext,
        queryFields,
        ImmutableList.of( this, other )
    ) ;
  }


  /**
   * Factors AND and OR operations.
   */
  static class Combinator<
      ENTITY,
      ENTITY_CONTEXT,
      QUERYFIELD extends QueryField< ENTITY, ENTITY_CONTEXT, ?, ?, ?, ? >
  > extends Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > {
    final ImmutableList< Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > > children ;

    private Combinator(
        final Kind kind,
        final ENTITY_CONTEXT entityContext,
        final ImmutableSet< QUERYFIELD > fields,
        final ImmutableList< Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > > children
    ) {
      super( kind, entityContext, fields ) ;
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
        for( final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > child : children ) {
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
        Stream.of( Evaluator.Kind.values() ).filter( k -> k.combinating ).collect( toImmutableSet() ) ;
  }

// =========
// Utilities
// =========

  public Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > newEmpty() {
    return new Empty<>( entityContext, queryFields ) ;
  }

  public Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > replaceFields(
      final FieldReplacer< ENTITY, ENTITY_CONTEXT, QUERYFIELD > fieldReplacer
  ) {
    switch( kind ) {
      case EMPTY :
        return this ;
      case FIELD :
        final ForField current = ( ForField ) this ;
        final Evaluator replacement = fieldReplacer.replace( this,
            ( QUERYFIELD ) current.queryfield, current.operator, current.parameter ) ;
        checkNotNull( replacement ) ;
        return replacement ;
      case NOT :
      case AND :
      case OR :
        final Combinator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > combinator = ( Combinator ) this ;
        boolean modified = false ;
        final ImmutableList.Builder< Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > > builder =
            ImmutableList.builder() ;
        for( final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > child : combinator.children ) {
          final Evaluator withReplacementMaybe = child.replaceFields( fieldReplacer ) ;
          builder.add( withReplacementMaybe ) ;
          modified |= withReplacementMaybe != child ;
        }
        if( modified ) {
          return new Combinator( kind, entityContext, queryFields, builder.build() ) ;
        } else {
          return this ;
        }
      default :
        throw new IllegalArgumentException( "Unsupported: " + kind ) ;
    }

  }

  /**
   * Used by {@link Evaluator#replaceFields(FieldReplacer)}.
   * We could use a {@code Function} with a {@link ForField} as input but the typing
   * would get messy.
   */
  public interface FieldReplacer<
      ENTITY,
      ENTITY_CONTEXT,
      QUERYFIELD extends QueryField< ENTITY, ENTITY_CONTEXT, ?, ?, ?, ? >
  > {
    /**
     * @param evaluator the one containing given {@link QUERYFIELD}, useful to call
     *     {@link Evaluator#newEmpty()} then {@link Evaluator#field(QueryField, Operator, Object)}.
     * @return a non-{@code null} value, which may be the same {@link Evaluator} as passed in.
     */
    Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > replace(
        Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > evaluator,
        QUERYFIELD queryfield,
        Operator operator,
        Object parameter
    ) ;
  }

  public void visitAll(
      final Consumer< Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > > evaluatorConsumer
  ) {
    switch( kind ) {
      case EMPTY:
        break ;
      case FIELD :
        evaluatorConsumer.accept( this ) ;
        break;
      case NOT :
      case AND :
      case OR :
        final Combinator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > combinator =
            ( Combinator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > ) this ;
        for( final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > child : combinator.children ) {
          child.visitAll( evaluatorConsumer ) ;
        }
        break;
      default :
        throw new IllegalArgumentException( "Unsupported: " + kind ) ;
    }
  }

}
