package com.otcdlink.chiron.testing;

import org.junit.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumBusterTest {

  @Test
  public void testAddingEnum() {
    final EnumBuster< SomeEnum > buster = new EnumBuster<>( SomeEnum.class ) ;
    try {
      final SomeEnum INTRUDER = buster.make( "INTRUDER" ) ;
      buster.addByValue( INTRUDER ) ;
      assertThat( SomeEnum.values() ).isEqualTo(
          new SomeEnum[] { SomeEnum.A, SomeEnum.B, INTRUDER } ) ;
      System.out.println( Arrays.toString( SomeEnum.values() ) ) ;

    } finally {
      buster.restore() ;
    }
    assertThat( SomeEnum.values() ).isEqualTo(
        new SomeEnum[]{ SomeEnum.A, SomeEnum.B } ) ;
    System.out.println( Arrays.toString( SomeEnum.values() ) ) ;

  }

  @Test
  public void testRemovingEnum() {
    final EnumBuster< SomeEnum > buster = new EnumBuster<>( SomeEnum.class ) ;
    try {
      buster.deleteByValue( SomeEnum.A ) ;
      assertThat( SomeEnum.values() ).isEqualTo( new SomeEnum[] { SomeEnum.B } ) ;
      System.out.println( Arrays.toString( SomeEnum.values() ) ) ;

    } finally {
      buster.restore() ;
    }
    assertThat( SomeEnum.values() ).isEqualTo( new SomeEnum[]{ SomeEnum.A, SomeEnum.B } ) ;
    System.out.println( Arrays.toString( SomeEnum.values() ) ) ;

  }


// =======
// Fixture
// =======

  private enum SomeEnum {
    A, B
  }

}