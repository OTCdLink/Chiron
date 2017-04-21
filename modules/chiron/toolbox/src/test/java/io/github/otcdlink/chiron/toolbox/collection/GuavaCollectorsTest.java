package io.github.otcdlink.chiron.toolbox.collection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class GuavaCollectorsTest {

  @Test
  public void immutableList() throws Exception {
    final ImmutableList< Integer > list = IntStream.range( 0, 3 )
        .boxed()
        .collect( ImmutableList.toImmutableList() )
    ;
    assertThat( list ).containsExactly( 0, 1, 2 ) ;
  }

  @Test
  public void immutableSet() throws Exception {
    final ImmutableSet< Integer > list = IntStream.range( 0, 3 )
        .boxed()
        .collect( ImmutableSet.toImmutableSet() )
    ;
    assertThat( list ).containsExactly( 0, 1, 2 ) ;
  }
}