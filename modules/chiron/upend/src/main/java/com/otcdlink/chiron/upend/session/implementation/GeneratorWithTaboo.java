package com.otcdlink.chiron.upend.session.implementation;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Generates random-based {@link VALUE}s and avoids collisions by keeping a "taboo ring"
 * which is a circular list of previously-generated values to not chose again.
 * This is fine as long as taboo ring size is greater than count of values in use.
 * An alternative to this is to include an ever-increasing counter, but when using it for
 * generating session identifiers it would give information about the number of sessions created.
 *
 * <h1>How the taboo works</h1>
 * As soon as generation occurs, {@link #tabooIndex} is incremented to give the index at which
 * generated value is stored into {@link #tabooRing}.
 * Given this value, a scan of the {@link #tabooRing} verifies it does not already appears at
 * another index. If it does, then the value is generated again using a fresh index
 * (collisions chances may be quite low depending on {@link VALUE} generation).
 *
 * <h1>But where is the synchronisation?</h1>
 * Synchronisation only happens on {@link #tabooIndex}. Access to {@link #tabooRing} is not
 * synchronized. If {@link #greatestTabooIndex} is high enough there should be no collision.
 * Reference updates are considered "atomic enough" because data can't be corrupted (but there
 * can be visibility problems across threads). If {@link #greatestTabooIndex} is not high enough
 * there is a <i>risk</i> of collision as high as the risk to create a collision with
 * {@link #valueEvaluator}, which should be quite low with a 128-bit session identifier.
 */
public class GeneratorWithTaboo< VALUE > {

  private final int greatestTabooIndex ;
  private final Object[] tabooRing ;
  private final AtomicInteger tabooIndex = new AtomicInteger( -1 ) ;
  private final Supplier< VALUE > valueGenerator ;
  private final Evaluator< VALUE > valueEvaluator ;


  public interface Evaluator< VALUE > {

    /**
     * @param first a non-{@code null} value.
     * @param second a non-{@code null} value.
     */
    boolean equivalent( VALUE first, VALUE second ) ;
  }

  public GeneratorWithTaboo(
      final int tabooSize,
      final Supplier< VALUE > valueGenerator,
      final Evaluator< VALUE > valueEvaluator
  ) {
    checkArgument( tabooSize > 0 ) ;
    this.greatestTabooIndex = tabooSize - 1 ;
    this.tabooRing = new Object[ tabooSize ] ;
    this.valueGenerator = checkNotNull( valueGenerator ) ;
    this.valueEvaluator = checkNotNull( valueEvaluator ) ;
  }

  public final VALUE generate() {
    VALUE newValue ;
    boolean alreadyExists ;
    do {
      final int newIndex = tabooIndex.updateAndGet(
          operand -> operand == greatestTabooIndex ? 0 : operand + 1 ) ;
      newValue = valueGenerator.get() ;
      tabooRing[ newIndex ] = newValue ;
      alreadyExists = scan( newIndex, newValue ) ;
    } while( alreadyExists  ) ;
    return newValue ;
  }

  private boolean scan( final int startIndex, final VALUE value ) {
    for( int index = startIndex + 1 ; index <= greatestTabooIndex ; index ++ ) {
      final Boolean x = equivalence( value, index ) ;
      if( x == null ) {
        // This happens at the time we didn't wrap around the ring.
        break ;
      } else if( x == Boolean.TRUE ) {
        return x ;
      }
    }
    for( int index = 0 ; index < startIndex ; index ++ ) {
      final Boolean x = equivalence( value, index ) ;
      if( x == Boolean.TRUE ) {
        return x ;
      }
    }
    return false ;
  }

  private Boolean equivalence( final VALUE value, final int index ) {
    final VALUE existing = ( VALUE ) tabooRing[ index ] ;
    if( existing == null ) {
      return null ;
    } else if( valueEvaluator.equivalent( value, existing ) ) {
      return Boolean.TRUE ;
    }
    return Boolean.FALSE ;
  }


}
