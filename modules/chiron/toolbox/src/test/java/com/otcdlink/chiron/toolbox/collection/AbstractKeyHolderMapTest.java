package com.otcdlink.chiron.toolbox.collection;

import com.otcdlink.chiron.toolbox.ComparatorTools;
import com.otcdlink.chiron.toolbox.StringWrapper;
import org.junit.Test;

import java.util.Comparator;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ImmutableKeyHolderMap}.
 */
public abstract class AbstractKeyHolderMapTest<
    MAP extends Map< AbstractKeyHolderMapTest.Thing.Key, AbstractKeyHolderMapTest.Thing >
> {

  @Test
  public void one() throws Exception {
    final MAP thingMap = create( THING_0 ) ;
    assertThat( thingMap.values() ).containsOnly( THING_0 ) ;
  }

  @Test
  public void two() throws Exception {
    final MAP thingMap = create( THING_0, THING_1 ) ;
    assertThat( thingMap.values() ).containsOnly( THING_0, THING_1 ) ;
  }


// =======
// Fixture
// =======

  protected static final Thing THING_0 = new Thing( new Thing.Key( 0 ), "Zero" ) ;
  protected static final Thing THING_0_BIS = new Thing( new Thing.Key( 0 ), "Zero-bis" ) ;
  protected static final Thing THING_1 = new Thing( new Thing.Key( 1 ), "One" ) ;

  @SuppressWarnings( "unchecked" )
  protected abstract MAP create( Thing... values ) ;

  protected static class Thing extends StringWrapper< Thing > implements KeyHolder<Thing.Key > {

    private final Key key ;

    public Thing( final Key key, final String name ) {
      super( name ) ;
      this.key = checkNotNull( key ) ;
    }

    @Override
    public Key key() {
      return key ;
    }


    protected Comparator< Thing > comparator() {
      return COMPARATOR ;
    }

    public static final class Key extends KeyHolder.LongKey< Key > {
      public Key( final long index ) {
        super( index ) ;
      }

      public static final Comparator< Key > COMPARATOR = new ComparatorTools.WithNull<Key>() {
        @Override
        protected int compareNoNulls( final Key first, final Key second ) {
          return first.compareTo( second ) ;
        }
      } ;
    }

    public static final Comparator< Thing > COMPARATOR = new ComparatorTools.WithNull< Thing >() {
      @Override
      protected int compareNoNulls( final Thing first, final Thing second ) {
        return first.key().compareTo( second.key() ) ;
      }
    } ;


  }

}
