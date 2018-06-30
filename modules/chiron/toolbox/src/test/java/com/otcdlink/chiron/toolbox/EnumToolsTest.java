package com.otcdlink.chiron.toolbox;

import org.junit.Test;

import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class EnumToolsTest {

  @Test
  public void strictResolverFromOrdinal() {
    assertThat( MyEnum.STRICT_RESOLVER.apply( 0 ) ).isEqualTo( MyEnum.ZERO ) ;
    assertThat( MyEnum.STRICT_RESOLVER.apply( 1 ) ).isEqualTo( MyEnum.ONE ) ;
    assertThatThrownBy( () -> MyEnum.STRICT_RESOLVER.apply( 2 ) )
        .isInstanceOf( BadEnumOrdinalException.class ) ;
    assertThatThrownBy( () -> MyEnum.STRICT_RESOLVER.apply( -1 ) )
        .isInstanceOf( BadEnumOrdinalException.class ) ;
  }

  @Test
  public void lenientResolverFromOrdinal() {
    assertThat( MyEnum.LENIENT_RESOLVER.apply( 0 ) ).isEqualTo( MyEnum.ZERO ) ;
    assertThat( MyEnum.LENIENT_RESOLVER.apply( 1 ) ).isEqualTo( MyEnum.ONE ) ;
    assertThat( MyEnum.LENIENT_RESOLVER.apply( 2 ) ).isNull() ;
    assertThat( MyEnum.LENIENT_RESOLVER.apply( -1 ) ).isNull() ;
  }

// =======
// Fixture
// =======


  private enum MyEnum {
    ZERO, ONE, ;

    public static final IntFunction< MyEnum > STRICT_RESOLVER =
        EnumTools.strictResolverFromOrdinal( MyEnum::values ) ;

    public static final IntFunction< MyEnum > LENIENT_RESOLVER =
        EnumTools.lenientResolverFromOrdinal( MyEnum::values ) ;

  }
}