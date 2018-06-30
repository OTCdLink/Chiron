package com.otcdlink.chiron.mockster;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Blocks until it gets a value from the {@link OperativeInvocation}.
 */
class ArgumentCapture extends ArgumentTrap {

  private final CoreVerifier coreVerifier;
  private final long invocationTimeoutMs ;

  public ArgumentCapture(
      final CoreVerifier coreVerifier,
      final int methodCallIndex,
      final int parameterIndex,
      final long invocationTimeoutMs
  ) {
    super( methodCallIndex, parameterIndex ) ;
    this.coreVerifier = checkNotNull( coreVerifier ) ;
    checkArgument( invocationTimeoutMs >= 0 ) ;
    this.invocationTimeoutMs = invocationTimeoutMs ;
  }

  @Override
  protected void toStringExtended( StringBuilder stringBuilder ) {
    stringBuilder.append( ";invocationTimeoutMs=" ).append( invocationTimeoutMs ) ;
  }

  public < T > T waitForOperativeValue() {
    return coreVerifier.waitForOperativeArgumentValue(
        methodCallIndex, parameterIndex, invocationTimeoutMs ) ;
  }
}
