package com.otcdlink.chiron.toolbox.collection;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.collection.ImmutableLongKeyHolderMapFixture.Entity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImmutableLongKeyHolderMapTest {

  @Test
  void addToEmpty() {
    final ImmutableLongKeyHolderMap< Entity.Key, Entity > mapEmpty =
        new ImmutableLongKeyHolderMap<>( ImmutableList.of() ) ;
    final ImmutableLongKeyHolderMap< Entity.Key, Entity > map1 = mapEmpty.copyAdd( E1 ) ;
    assertThat( map1.values() ).containsExactly( E1 ) ;
  }

  @Test
  void addInTheMiddle() {
    final ImmutableLongKeyHolderMap< Entity.Key, Entity > map02 =
        new ImmutableLongKeyHolderMap<>( ImmutableList.of( E0, E2 ) ) ;
    final ImmutableLongKeyHolderMap< Entity.Key, Entity > map012 = map02.copyAdd( E1 ) ;
    assertThat( map012.values() ).containsExactly( E0, E1, E2 ) ;
  }

  @Test
  void addAtStart() {
    final ImmutableLongKeyHolderMap< Entity.Key, Entity > map12 =
        new ImmutableLongKeyHolderMap<>( ImmutableList.of( E1, E2 ) ) ;
    final ImmutableLongKeyHolderMap< Entity.Key, Entity > map012 = map12.copyAdd( E0 ) ;
    assertThat( map012.values() ).containsExactly( E0, E1, E2 ) ;
  }

  @Test
  void addAtEnd() {
    final ImmutableLongKeyHolderMap< Entity.Key, Entity > map01 =
        new ImmutableLongKeyHolderMap<>( ImmutableList.of( E0, E1 ) ) ;
    final ImmutableLongKeyHolderMap< Entity.Key, Entity > map012 = map01.copyAdd( E2 ) ;
    assertThat( map012.values() ).containsExactly( E0, E1, E2 ) ;
  }

  @Test
  void addExisting() {
    final ImmutableLongKeyHolderMap< Entity.Key, Entity > map02 =
        new ImmutableLongKeyHolderMap<>( ImmutableList.of( E0, E2 ) ) ;
    assertThatThrownBy( () -> map02.copyAdd( E2 ) ).isInstanceOf( IllegalArgumentException.class ) ;
  }

  @Test
  void containsKeyWithIndex() {
    final ImmutableLongKeyHolderMap< Entity.Key, Entity > map02 =
        new ImmutableLongKeyHolderMap<>( ImmutableList.of( E0, E2 ) ) ;
    assertThat( map02.containsKeyWithIndex( E0.key().index() ) ).isTrue() ;
    assertThat( map02.containsKeyWithIndex( E1.key().index() ) ).isFalse() ;
    assertThat( map02.containsKeyWithIndex( E2.key().index() ) ).isTrue() ;
  }

// =======
// Fixture
// =======

  private static final Entity E0 = new Entity( 0 ) ;
  private static final Entity E1 = new Entity( 1 ) ;
  private static final Entity E2 = new Entity( 2 ) ;

}