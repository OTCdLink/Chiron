package com.otcdlink.chiron.mockster;

import org.assertj.core.api.Assertions;

/**
 * Kept in a {@link VerifyingInvocation} when calling a mock with values to match.
 */
final class ArgumentEquality extends ArgumentTrap implements ArgumentEvaluator {

  private final Object expectedValue ;

  public ArgumentEquality(
      final int methodCallIndex,
      final int parameterIndex,
      final Object expectedValue
  ) {
    super( methodCallIndex, parameterIndex ) ;
    this.expectedValue = expectedValue ;
  }

  @Override
  protected void toStringExtended( final StringBuilder stringBuilder ) {
    stringBuilder.append( ";expectedValue=" ).append( expectedValue ) ;
  }

  @Override
  public void evaluate( final Object actualValue ) {
    Assertions
        .assertThat( actualValue )
        .describedAs( location().asString() )
        .isEqualTo( expectedValue )
    ;
  }
}
