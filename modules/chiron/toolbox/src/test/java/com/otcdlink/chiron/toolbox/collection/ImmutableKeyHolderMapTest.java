package com.otcdlink.chiron.toolbox.collection;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ImmutableKeyHolderMap}.
 */
public class ImmutableKeyHolderMapTest extends AbstractKeyHolderMapTest<
    Map< AbstractKeyHolderMapTest.Thing.Key, AbstractKeyHolderMapTest.Thing>
> {


  @Test
  public void build() {
    final ImmutableKeyHolderMap.Builder< Thing.Key, Thing > builder
        = ImmutableKeyHolderMap.builder() ;
    builder.putAll( Arrays.asList( THING_0, THING_1 ) ) ;
    final ImmutableKeyHolderMap< Thing.Key, Thing > thingMap = builder.build() ;
    assertThat( thingMap.values() ).containsOnly( THING_0, THING_1 ) ;
  }

  @Test
  public void collector() {
    final ImmutableKeyHolderMap< Thing.Key, Thing > collected =
        ImmutableList.of( THING_0, THING_1 )
        .stream()
        .collect( ImmutableKeyHolderMap.toImmutableKeyHolderMap() )
    ;
    assertThat( collected.values() ).containsOnly( THING_0, THING_1 ) ;
  }

// =======
// Fixture
// =======


  @Override
  protected ImmutableKeyHolderMap<
      AbstractKeyHolderMapTest.Thing.Key, AbstractKeyHolderMapTest.Thing
  > create( final Thing... values ) {
    return ImmutableKeyHolderMap.
        < AbstractKeyHolderMapTest.Thing.Key, AbstractKeyHolderMapTest.Thing >of( values ) ;
  }
}
