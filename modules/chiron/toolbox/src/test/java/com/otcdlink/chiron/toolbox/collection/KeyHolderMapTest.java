package com.otcdlink.chiron.toolbox.collection;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link KeyHolderMap}.
 */
public class KeyHolderMapTest extends AbstractKeyHolderMapTest<
    KeyHolderMap< AbstractKeyHolderMapTest.Thing.Key, AbstractKeyHolderMapTest.Thing>
> {
  @Test
  public void remove() throws Exception {
    final KeyHolderMap< Thing.Key, Thing > keyHolderMap = create( THING_0 ) ;
    keyHolderMap.remove( THING_0.key() ) ;
    assertThat( keyHolderMap.isEmpty() ) ;
  }

  @Test
  public void replace() throws Exception {
    final KeyHolderMap< Thing.Key, Thing > keyHolderMap = create( THING_0 ) ;
    keyHolderMap.replace( THING_0_BIS ) ;
    assertThat( keyHolderMap.values() ).containsOnly( THING_0_BIS ) ;
  }


  // =======
// Fixture
// =======


  @Override
  protected KeyHolderMap<Thing.Key, Thing> create( final Thing... values ) {
    final KeyHolderMap< Thing.Key, Thing > keyHolderMap = new KeyHolderMap<>() ;
    keyHolderMap.putAll( values ) ;
    return keyHolderMap;
  }
}
