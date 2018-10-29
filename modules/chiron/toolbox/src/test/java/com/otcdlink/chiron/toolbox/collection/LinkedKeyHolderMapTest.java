package com.otcdlink.chiron.toolbox.collection;

import com.otcdlink.chiron.toolbox.ToStringTools;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedKeyHolderMapTest {

  @Test
  void preserveOrder() {
    final LinkedKeyHolderMap< Owner.Key, Owner > keyHolderMap = new LinkedKeyHolderMap<>() ;
    keyHolderMap.put( OWNER_2 ) ;
    keyHolderMap.put( OWNER_4 ) ;
    keyHolderMap.put( OWNER_1 ) ;
    keyHolderMap.put( OWNER_3 ) ;

    assertThat( keyHolderMap.values() ).containsExactly( OWNER_2, OWNER_4, OWNER_1, OWNER_3 ) ;
  }

// =======
// Fixture
// =======

  private static final Owner OWNER_1 = new Owner( new Owner.Key( 1 ) ) ;
  private static final Owner OWNER_2 = new Owner( new Owner.Key( 2 ) ) ;
  private static final Owner OWNER_3 = new Owner( new Owner.Key( 3 ) ) ;
  private static final Owner OWNER_4 = new Owner( new Owner.Key( 4 ) ) ;

  private static class Owner implements KeyHolder< Owner.Key > {

    private static final class Key extends KeyHolder.LongKey< Key > {
      protected Key( long index ) {
        super( index );
      }
    }

    public final Key key ;

    private Owner( final Key key ) {
      this.key = key ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) +
          "key=" + key +
          '}'
      ;
    }

    @Override
    public Key key() {
      return key ;
    }
  }
}