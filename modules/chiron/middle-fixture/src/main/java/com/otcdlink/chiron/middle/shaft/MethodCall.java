package com.otcdlink.chiron.middle.shaft;

import com.google.common.collect.ImmutableList;

import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class MethodCall {
  public final Method method ;
  public final ImmutableList< Object > parameters ;

  public MethodCall( final Method method, final Object... parameters ) {
    this( method, ImmutableList.copyOf( parameters ) ) ;
  }

  public MethodCall( final Method method, final ImmutableList< Object > parameters ) {
    checkArgument( method.getReturnType().equals( Void.TYPE ),
        "Expecting void return type for method '" + method + "'" ) ;
    this.method = checkNotNull( method ) ;
    checkArgument( parameters.size() == method.getParameterCount(),
        "Expecting parameter count to be the same as " + method + " but is " + parameters.size() ) ;
    this.parameters = checkNotNull( parameters ) ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' + method.getName() + '(' + parameters + ")}" ;

  }
}
