package com.otcdlink.chiron.mockster;

import java.lang.reflect.Method;
import java.util.List;

class OperativeInvocation extends Invocation {

  public OperativeInvocation(
      final int invocationIndex,
      final Object target,
      final Method method,
      final List<Object> arguments,
      final Object result,
      final Throwable throwable
  ) {
    super( invocationIndex, target, method, arguments, result, throwable ) ;
  }
}
