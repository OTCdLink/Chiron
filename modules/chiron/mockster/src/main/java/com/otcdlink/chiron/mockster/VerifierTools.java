package com.otcdlink.chiron.mockster;

import com.otcdlink.chiron.toolbox.ToStringTools;

import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

class VerifierTools {

  public static String proxyAwareToString( final Object object ) {
    if( object == null ) {
      return "<null>" ;
    } else if( Proxy.isProxyClass( object.getClass() ) ) {
      return ToStringTools.nameAndHash( object ) ;
    } else {
      return object.toString() ;
    }
  }

  /**
   * @return {@code true} if running in {@link Mockster}'s constructor.
   */
  public static boolean isRunningInInitializer() {
    final StackTraceElement[] stackTraceElements = new Exception().getStackTrace() ;
    for( int i = 0 ; i < Math.min( stackTraceElements.length, 5 ) ; i ++ ) {
      final StackTraceElement stackTraceElement = stackTraceElements[ i ] ;
      final String className = stackTraceElement.getClassName() ;
      final Class callerClass ;
      try {
        callerClass = Class.forName( className ) ;
      } catch( final ClassNotFoundException e ) {
        throw new RuntimeException( e ) ;
      }
      if( Mockster.class.isAssignableFrom( callerClass ) &&
          stackTraceElement.getMethodName().equals( "<init>" )
      ) {
        return true ;
      }
    }
    return false ;
  }

  public static < OBJECT > OBJECT safeValue( final Type type, final Object value ) {
    final Object safeValue = value == null ? safeNull( type ) : value;
    return ( OBJECT ) safeValue ;
  }

  public static Object safeNull( final Type type ) {
    if( Integer.TYPE.equals( type ) ) {
      return 0 ;
    } else if( Long.TYPE.equals( type ) ) {
      return 0L ;
    } else {
      return null ;
    }
  }

  public static boolean isMock( final Object object ) {
    return Proxy.isProxyClass( object.getClass() ) && object instanceof CoreVerifier.MockLogic ;
  }
}
