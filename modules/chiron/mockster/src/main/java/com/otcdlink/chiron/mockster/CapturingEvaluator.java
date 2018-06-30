package com.otcdlink.chiron.mockster;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Extends the {@link ArgumentCapture} with a verification; the capture is useful to return
 * an instance of the correct type with no prior knowledge of the method called (a {@code null}
 * wouldn't fit in a primitive return type).
 */
class CapturingEvaluator extends ArgumentCapture implements ArgumentEvaluator {

  /**
   * Throws an {@code AssertionError} if something goes bad.
   * {@code Object} parameter is actual value.
   * {@code String} is location as returned by {@link #location()}.
   */
  private final ArgumentVerifier argumentVerifier ;

  public CapturingEvaluator(
      final CoreVerifier coreVerifier,
      final int methodCallIndex,
      final int parameterIndex,
      final long invocationTimeoutMs,
      final ArgumentVerifier< ? > argumentVerifier
  ) {
    super( coreVerifier, methodCallIndex, parameterIndex, invocationTimeoutMs ) ;
    this.argumentVerifier = checkNotNull( argumentVerifier ) ;
  }

  @Override
  protected void toStringExtended( StringBuilder stringBuilder ) {
    super.toStringExtended( stringBuilder ) ;
    stringBuilder.append( ';' ).append( "argumentVerifier" ).append( argumentVerifier ) ;
  }

  @Override
  public void evaluate( final Object actualValue ) {
    argumentVerifier.verify( location(), actualValue ) ;
  }
}
