package com.otcdlink.chiron.mockster;

interface ArgumentEvaluator {

  void evaluate( final Object actualValue ) ;

  final class Location {
    public final int invocationIndex ;
    public final int argumentIndex ;

    public Location( final int invocationIndex, final int argumentIndex ) {
      this.invocationIndex = invocationIndex ;
      this.argumentIndex = argumentIndex ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{" + asString() + "}" ;
    }

    public String asString() {
      return "invocationIndex=" + invocationIndex + "; argumentIndex=" + argumentIndex ;
    }
  }
}
