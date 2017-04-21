package io.github.otcdlink.chiron.middle.shaft;

import org.junit.ComparisonFailure;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ShaftTest {

  @Test
  public void shallowShaftOk() throws Exception {
    new MinimalShaft().submit( STRAIGHT_METHOD_CALLER, new MethodCallVerifier.Default() ) ;
  }

  @Test
  public void shallowShaftReportsParameterDifference() throws Exception {
    assertThatThrownBy( () ->
        new MinimalShaft().submit( new TwistedMethodCaller(), new MethodCallVerifier.Default() )
    ).isInstanceOf( ComparisonFailure.class ) ;
  }

// =======
// Fixture
// =======

  private interface Whatever {
    void foo( final String first, final int second ) ;
    void bar( final String first, final int second ) ;
  }

  private static final MethodCaller< Whatever > STRAIGHT_METHOD_CALLER =
      new MethodCaller.Default< Whatever >() {
        @Override
        public void callMethods( final Whatever whatever ) {
          whatever.foo( "A", 1 ) ;
          whatever.bar( "B", 2 ) ;
        }
      }
  ;

  private static final class TwistedMethodCaller implements MethodCaller< Whatever > {
    private int callCounter = 0 ;
    @Override
    public void callMethods( final Whatever whatever ) {
      whatever.foo( "A", callCounter ++ ) ;
    }

    @Override
    public Class< Whatever > targetInterface() {
      return Whatever.class ;
    }
  }

}