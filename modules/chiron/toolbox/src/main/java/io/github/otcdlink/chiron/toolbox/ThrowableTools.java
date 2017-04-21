package io.github.otcdlink.chiron.toolbox;

import com.google.common.base.Throwables;

import java.lang.reflect.Field;
import java.util.List;

public final class ThrowableTools {

  private ThrowableTools() { }

  public static void appendMessage( final Throwable throwable, final String message ) {
    final String newMessage = throwable.getMessage() + message ;
    try {
      // TODO: some caching.
      final Field field = Throwable.class.getDeclaredField( "detailMessage" ) ;
      field.setAccessible( true ) ;
      field.set( throwable, newMessage ) ;
    } catch( IllegalAccessException | NoSuchFieldException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  @SuppressWarnings( "ThrowableResultOfMethodCallIgnored" )
  public static void appendCause( final Throwable throwable, final Exception cause ) {
    if( cause != null ) {
      final List< Throwable > causalChain = Throwables.getCausalChain( throwable ) ;
      causalChain.get( causalChain.size() - 1 ).initCause( cause ) ;
    }
  }

  public static void removeStackTraceTopElement( final Exception exception ) {
    final StackTraceElement[] fullStackTrace = exception.getStackTrace() ;
    final StackTraceElement[] abbreviatedStackTrace =
        new StackTraceElement[ fullStackTrace.length - 1 ] ;
    System.arraycopy(
        fullStackTrace, 1, abbreviatedStackTrace, 0, abbreviatedStackTrace.length ) ;
    exception.setStackTrace( abbreviatedStackTrace ) ;
  }
}
