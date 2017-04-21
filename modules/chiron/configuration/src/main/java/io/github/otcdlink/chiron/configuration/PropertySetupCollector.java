package io.github.otcdlink.chiron.configuration;

import com.google.common.reflect.AbstractInvocationHandler;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class PropertySetupCollector< C extends Configuration> {
  public final C template ;
  private Method lastAccessed = null ;
  private final Configuration.PropertySetup.SetupAcceptor setupAcceptor ;

  public PropertySetupCollector(
      final Class< C > configurationClass,
      final Configuration.PropertySetup.SetupAcceptor setupAcceptor
  ) {
    this.setupAcceptor = setupAcceptor ;
    //noinspection unchecked,NullableProblems
    template = ( C ) Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class< ? >[]{ configurationClass },
        new AbstractInvocationHandler() {
          @Override
          protected Object handleInvocation(
              final Object proxy,
              final Method method,
              final Object[] args
          ) throws Throwable {
            lastAccessed = method ;
            return ValuedProperty.safeNull( method.getReturnType() ) ;
          }
        }
    ) ;
  }

  public final < T > Configuration.PropertySetup< C, T > on(
      @SuppressWarnings( "UnusedParameters" ) final T methodCallResult
  ) {
    return new Configuration.PropertySetup<>( lastAccessed, setupAcceptor ) ;
  }

}
