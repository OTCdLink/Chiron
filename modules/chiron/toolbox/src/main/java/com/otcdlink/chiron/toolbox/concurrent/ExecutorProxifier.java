package com.otcdlink.chiron.toolbox.concurrent;

import com.google.common.reflect.AbstractInvocationHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Wraps method calls into an {@code Executor} call.
 * Wrapped calls are from interfaces, method must have a {@code void} return type and throw
 * no exception.
 */
public class ExecutorProxifier {

  private final Object delegate ;
  private final Object proxy ;
  private final String tostringValue ;

  private ExecutorProxifier(
      final Executor executor,
      final Object delegate,
      final Class... supportedInterfaces
  ) {
    checkNotNull( executor ) ;
    this.delegate = checkNotNull( delegate ) ;
    proxy = Proxy.newProxyInstance(
        getClass().getClassLoader(),
        supportedInterfaces,
        new AbstractInvocationHandler() {
          @Override
          protected Object handleInvocation(
              final Object proxy,
              final Method method,
              final Object[] arguments
          ) throws Throwable {
                executor.execute( () -> invokeQuiet( method, arguments ) ) ;
            return null ;
          }
        }
    ) ;
    tostringValue = getClass().getSimpleName() + '{' +
        executor + ';' +
        delegate +
        '}'
    ;
  }

  public < PROXY > PROXY proxy() {
    return ( PROXY ) proxy ;
  }

  public static ExecutorProxifier proxify(
      final Executor executor,
      final Object delegate
  ) {
    return new ExecutorProxifier( executor, delegate, extractInterfaces( delegate ) ) ;
  }

  private Object invokeQuiet( final Method method, final Object[] arguments ) {
    try {
      return method.invoke( delegate, arguments ) ;
    } catch( final IllegalAccessException | InvocationTargetException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  private static String isInterfaceValid( final Class candidateInterface ) {
    if( ! candidateInterface.isInterface() ) {
      return "Not an interface: " + candidateInterface.getName() ;
    }
    for( final Method method : candidateInterface.getDeclaredMethods() ) {
      if( ! Void.TYPE.equals( method.getReturnType() ) ) {
        return "Return type is not void: " + method ;
      }
      for( final Class exceptionType : method.getExceptionTypes() ) {
        if( Exception.class.isAssignableFrom( exceptionType ) ) {
          return "Checked exception thrown: " + exceptionType.getName() + " in " + method ;
        }
      }
    }
    return null ;
  }

  private static Class[] extractInterfaces( final Object delegate ) {
    final Set< Class > interfaces = new HashSet<>( 10 ) ;
    Class currentClass = delegate.getClass() ;
    while( ! Object.class.equals( currentClass ) ) {
      for( final Class implementedInterface : currentClass.getInterfaces() ) {
        final String failure = isInterfaceValid( implementedInterface );
        if( failure != null ) {
          throw new IllegalArgumentException(
              "Incorrect interface (" + failure + ") for " + delegate ) ;
        } else {
          interfaces.add( implementedInterface ) ;
        }
      }
      currentClass = currentClass.getSuperclass() ;
    }
    return interfaces.toArray( new Class[ interfaces.size() ] ) ;
  }

  @Override
  public String toString() {
    return tostringValue ;
  }
}
