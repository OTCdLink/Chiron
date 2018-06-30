package com.otcdlink.chiron.mockster;

import com.otcdlink.chiron.toolbox.ToStringTools;

import static com.google.common.base.Preconditions.checkArgument;

abstract class ArgumentTrap {

  protected final int methodCallIndex ;
  protected final int parameterIndex ;

  protected ArgumentTrap(
      final int methodCallIndex,
      final int parameterIndex
  ) {
    checkArgument( methodCallIndex >= 0 ) ;
    this.methodCallIndex = methodCallIndex ;
    checkArgument( parameterIndex >= 0 ) ;
    this.parameterIndex = parameterIndex ;
  }

  @Override
  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder() ;
    stringBuilder
        .append( ToStringTools.nameAndHash( this ) )
        .append( "{" )
        .append( location().asString() )
    ;
    toStringExtended( stringBuilder ) ;
    return stringBuilder.append( "}" ).toString() ;
  }

  protected void toStringExtended( final StringBuilder stringBuilder ) { }

  protected final ArgumentEvaluator.Location location() {
    return new ArgumentEvaluator.Location( methodCallIndex, parameterIndex ) ;
  }

}
