package io.github.otcdlink.chiron.toolbox.collection;

import org.junit.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SortedKeyHolderMap}.
 */
public class SortedKeyHolderMapTest extends AbstractKeyHolderMapTest<
    SortedKeyHolderMap< AbstractKeyHolderMapTest.Thing.Key, AbstractKeyHolderMapTest.Thing >
> {
  @Test
  public void last() throws Exception {
    final SortedKeyHolderMap< Thing.Key, Thing > keyHolderMap = create( THING_1, THING_0 ) ;
    assertThat( keyHolderMap.lastValue() ).isEqualTo( THING_1 ) ;
    assertThat( keyHolderMap.lastKey() ).isEqualTo( THING_1.key() ) ;
  }

  @Test( expected = NoSuchElementException.class )
  public void lastWhenEmpty() throws Exception {
    final SortedKeyHolderMap< Thing.Key, Thing > keyHolderMap = create() ;
    keyHolderMap.lastValue() ;
  }


// =======
// Fixture
// =======


  @Override
  protected SortedKeyHolderMap< Thing.Key, Thing > create( final Thing... values ) {
    final SortedKeyHolderMap< Thing.Key, Thing > keyHolderMap = new SortedKeyHolderMap<>( Thing.Key.COMPARATOR ) ;
    keyHolderMap.putAll( values ) ;
    return keyHolderMap ;
  }
}
