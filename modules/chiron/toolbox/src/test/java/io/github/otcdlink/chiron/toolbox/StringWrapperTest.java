package io.github.otcdlink.chiron.toolbox;

import com.google.common.base.Equivalence;
import com.google.common.base.Supplier;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.EquivalenceTester;
import junit.framework.AssertionFailedError;
import org.junit.Test;

import java.util.Comparator;

public class StringWrapperTest {

  @Test
  public void equality() throws Exception {
    new EqualsTester()
        .addEqualityGroup( A0, A1 )
        .addEqualityGroup( B0, B1 )
        .testEquals()
    ;
  }

  @Test
  public void comparator() throws Exception {
    EquivalenceTester.of( comparatorEquivalence( Thing.COMPARATOR ) )
        .addEquivalenceGroup( A0, A1 )
        .addEquivalenceGroup( B0, B1 )
        .test()
    ;
  }


// =======
// Fixture
// =======

  private static final class Thing extends StringWrapper< Thing > {
    public Thing( final String wrapped ) {
      super( wrapped ) ;
    }

    public static final Comparator< Thing > COMPARATOR = new StringWrapper.WrapperComparator<>() ;
  }

  private static final Thing A0 = new Thing( "A" ) ;
  private static final Thing A1 = new Thing( "A" ) ;
  private static final Thing B0 = new Thing( "B" ) ;
  private static final Thing B1 = new Thing( "B" ) ;

  /**
   * Kind of hack to test {@code Comparable}'s symmetry using Guava's
   * {@link com.google.common.base.Equivalence}.
   * In a better world Guava would use this excellent pattern to check reflexivity and
   * transitivity as well.
   * But they favor usage of {@link com.google.common.base.Equivalence} instead of
   * {@code java.util.Comparator}.
   *
   * TODO: move this into some 'Chiron-testing' project (or whatever that sounds good).
   */
  private static < OBJECT > Equivalence< OBJECT > comparatorEquivalence(
      final Comparator< OBJECT > comparator
  ) {
    return new Equivalence< OBJECT >() {
      @Override
      protected boolean doEquivalent( final OBJECT first, final OBJECT second ) {

        final int straight = comparator.compare( first, second ) ;
        final int inverse = comparator.compare( second, first ) ;

        final Supplier< AssertionFailedError > failOnNonReflexiveOperation = () ->
            new AssertionFailedError(
                "#compare( " + first + ", " + second + " ) -> " + straight +
                    " but " +
                    "#compare( " + second + ", " + first + " ) -> " + inverse
            )
        ;

        if( straight == 0 ) {
          if( inverse != 0 ) {
            throw failOnNonReflexiveOperation.get() ;
          }
        } else {
          if( straight > 0 ) {
            if( inverse > 0 ) {
              throw failOnNonReflexiveOperation.get() ;
            }
          } else {
            if( inverse < 0 ) {
              throw failOnNonReflexiveOperation.get() ;
            }
          }
        }
        return straight == 0 ;
      }

      @Override
      protected int doHash( final OBJECT object ) {
        return object.hashCode() ;
      }
    } ;
  }

}