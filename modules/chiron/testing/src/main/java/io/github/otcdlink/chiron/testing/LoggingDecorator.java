package io.github.otcdlink.chiron.testing;

import com.google.common.base.Throwables;
import org.slf4j.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public final class LoggingDecorator {
  private LoggingDecorator() { }

  public static < OBJECT > OBJECT loggingInstance(
      final Logger logger,
      final Class< OBJECT > objectClass
  ) {
    return decorateWithLogging( logger, null, objectClass ) ;
  }

  @SuppressWarnings( "unchecked" )
  public static < OBJECT > OBJECT decorateWithLogging(
      final Logger logger,
      final OBJECT decorated,
      final Class< OBJECT > objectClass
  ) {
    return ( OBJECT ) Proxy.newProxyInstance(
        LoggingDecorator.class.getClassLoader(),
        new Class[]{ objectClass },
        new InvocationHandler() {
          private final String prefix = objectClass.getSimpleName() + ": " ;

          @Override
          public Object invoke(
              final Object proxy,
              final Method method,
              final Object[] args
          ) {
            if( ( "toString".equals( method.getName() ) && method.getParameterCount() == 0 ) ) {
              return LoggingDecorator.class.getSimpleName() +
                  '@' + System.identityHashCode( proxy ) + '{' +
                  ( decorated == null ? objectClass.getSimpleName() : decorated.toString() ) + '}'
              ;
            } else if( "hashCode".equals( method.getName() ) && method.getParameterCount() == 0 ) {
              return decorated == null ? System.identityHashCode( proxy ) : decorated.hashCode() ;
            } else if( "equals".equals( method.getName() ) && method.getParameterCount() == 0 ) {
              return decorated == null ? args[ 0 ] == proxy : decorated.equals( args[ 0 ] ) ;
            }

            logger.info(
                prefix + '#' + method.getName() +
                    ( method.getParameterCount() == 0
                        ? "()"
//                        : '(' + ( args == null ? null : Joiner.on( ',' ).join( args ) ) + ')'
                        : '(' + Arrays.asList( args ).toString() + ')'
                    )
            ) ;
            try {
              if( decorated == null ) {
                return null ;
              } else {
                return method.invoke( decorated, args ) ;
              }
            } catch( IllegalAccessException | InvocationTargetException e ) {
              throw Throwables.propagate( e ) ;
            }
          }
        }
    ) ;
  }
}
