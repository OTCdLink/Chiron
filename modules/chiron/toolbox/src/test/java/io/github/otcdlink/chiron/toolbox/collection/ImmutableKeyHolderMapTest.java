package io.github.otcdlink.chiron.toolbox.collection;

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
  public void build() throws Exception {
    final ImmutableKeyHolderMap.Builder< Thing.Key, Thing > builder
        = ImmutableKeyHolderMap.builder() ;
    builder.putAll( Arrays.asList( THING_0, THING_1 ) ) ;
    final ImmutableKeyHolderMap< Thing.Key, Thing > thingMap = builder.build() ;
    assertThat( thingMap.values() ).containsOnly( THING_0, THING_1 ) ;
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
