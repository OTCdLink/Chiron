package com.otcdlink.chiron.mockster;

import com.google.common.collect.ImmutableList;

import java.lang.reflect.Method;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

class VerifyingInvocation extends Invocation {

  public VerifyingInvocation(
      final int verificationIndex,
      final Object target,
      final Method method,
      final List< Object > arguments,
      final Object result,
      final Throwable throwable
  ) {
    super( verificationIndex, target, method, arguments, result, throwable ) ;
    checkArgument(
        method.getParameterCount() == arguments.size(),
        "Expecting parameters of type " + ImmutableList.copyOf( method.getParameterTypes() ) +
        " but got " + ImmutableList.copyOf( arguments )
    ) ;
    for( int i = 0, argumentsLength = arguments.size() ; i < argumentsLength ; i++ ) {
      final Object argument = arguments.get( i ) ;
      checkArgument( argument instanceof ArgumentTrap, "Bad argument at index " + i ) ;
    }
  }

  public ArgumentTrap argumentTrapAt( final int index ) {
    return ( ArgumentTrap ) arguments.get( index ) ;
  }
}
