package com.otcdlink.chiron.toolbox.collection;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamToolsTest {

  @Test
  public void nullCollector() {
    final Object collected = ImmutableList.of( 1, 2, 3 ).stream()
        .collect( StreamTools.< Integer, Void >nullCollector() ) ;
    assertThat( collected ).isNull() ;
  }

  @Test
  public void consumingCollector() {
    final ImmutableList< Integer > list = ImmutableList.of( 1, 2, 3 ) ;
    final ImmutableList.Builder< Integer > builder = ImmutableList.builder() ;
    final Object collected = list.stream()
        .collect( StreamTools.< Integer, Void >consumingCollector( builder::add ) ) ;
    assertThat( collected ).isNull() ;
    assertThat( builder.build() ).isEqualTo( list ) ;
  }


}