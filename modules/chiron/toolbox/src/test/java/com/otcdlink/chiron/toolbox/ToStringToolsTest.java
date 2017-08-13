package com.otcdlink.chiron.toolbox;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ToStringToolsTest {

  @Test
  public void niceClassNameOfAnonymous() throws Exception {
    final Object object = new Inner() {} ;
    assertThat( ToStringTools.getNiceClassName( object ) )
        .describedAs( "Default class name: " + object.getClass().getName() )
        .isEqualTo( "ToStringToolsTest$1(Inner)" ) ;
  }

  @Test
  public void niceClassNameOfInner() throws Exception {
    assertThat( ToStringTools.getNiceClassName( new Inner() ) )
        .isEqualTo( "ToStringToolsTest$Inner" ) ;
    assertThat( ToStringTools.getNiceClassName( new Inner.EvenInner() ) )
        .isEqualTo( "ToStringToolsTest$Inner$EvenInner" ) ;
  }


// =======
// Fixture
// =======

  private static class Inner {
    private static class EvenInner { }
  }


}