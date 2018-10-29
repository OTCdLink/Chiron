package com.otcdlink.chiron.toolbox.collection;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.collection.ImmutableLongKeyHolderMapFixture.Entity;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@State( Scope.Benchmark )
public class ImmutableLongKeyHolderMapJmhBenchmark {

  @Benchmark
  @Fork( value = 1, warmups = 1 )
  @BenchmarkMode( Mode.Throughput )
  public void immutableLongKeyHolderMap() {
    applyChanges(
        () -> new ImmutableLongKeyHolderMap<>( ImmutableList.of() ),
        ImmutableLongKeyHolderMap::copyAdd,
        ImmutableLongKeyHolderMap::containsKeyWithIndex
    ) ;
  }

  @Benchmark
  @Fork( value = 1, warmups = 1 )
  @BenchmarkMode( Mode.Throughput )
  public void immutableKeyHolderMap() {
    applyChanges(
        ImmutableKeyHolderMap::of,
        ImmutableKeyHolderMap::add,
        ( map, l ) -> map.containsKey( new Entity.Key( l ) )
    ) ;
  }

  @Benchmark
  @Fork( value = 1, warmups = 1 )
  @BenchmarkMode( Mode.Throughput )
  public void mutableKeyHolderMap() {
    applyChanges(
        KeyHolderMap::new,
        ( map, entity ) -> { map.put( entity ) ; return map ; },
        ( map, l ) -> map.containsKey( new Entity.Key( l ) )
    ) ;
  }

  private< MAP extends Map< Entity.Key, Entity > > void applyChanges(
      final Supplier< MAP > mapSupplier,
      final BiFunction< MAP, Entity, MAP > adder,
      final BiFunction< MAP, Long, Boolean > presenceDetector
  ) {
    MAP map = mapSupplier.get() ;
    for( int i = 0 ; i < 100 ; i ++ ) {
      map = adder.apply( map, new Entity( i ) ) ;
      for( int j = 0 ; j < 100 ; j ++ ) {
        presenceDetector.apply( map, ( long ) j ) ;
      }
    }
  }

  public static void main( final String... arguments ) throws Exception {
    org.openjdk.jmh.Main.main( arguments ) ;
  }


}
