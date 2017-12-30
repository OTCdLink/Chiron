package com.otcdlink.chiron.toolbox;

import com.google.common.collect.ImmutableList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An {@code Exception} that may contain several {@code Exception}s.
 *
 * Other candidate names:
 * Manifold, Multi (used by Jetty), Tupling, Tupled, Tapling, Combo, Gathering
 * Formerly: Multiplexing
 */
public class CollectingException extends Exception {

  public final ImmutableList< Throwable > exceptions ;

  public CollectingException( final String message, final ImmutableList< Throwable > exceptions ) {
    super( join( message, exceptions ) ) ;
    this.exceptions = checkNotNull( exceptions ) ;
  }

  private static String join( final String message, final ImmutableList< Throwable > exceptions ) {
    return message
        + " (" + exceptions.size() + " exception" + ( exceptions.size() > 1 ? "s" : "" ) + ")"
        + join( exceptions )
    ;
  }
  private static String join( final ImmutableList< Throwable > exceptions ) {
    final StringBuilder stringBuilder = new StringBuilder() ;
    for( final Throwable throwable : exceptions ) {
      stringBuilder
          .append( "\n    " )
          .append( throwable.getClass().getName() )
          .append( " - " )
          .append( throwable.getMessage() )
      ;
    }
    return stringBuilder.toString() ;
  }

  public static Collector< CollectingException > newCollector() {
    return new Collector<>( CollectingException::new ) ;
  }


  @Override
  public void printStackTrace( final PrintWriter printWriter ) {
    super.printStackTrace( printWriter ) ;

    int i = 0 ;
    for( final Throwable throwable : exceptions ) {
      printWriter.println( "--- Collected Throwable " + ( i ++ ) + " ---" ) ;
      throwable.printStackTrace( printWriter ) ;
    }
  }

  public static final class Collector< E extends CollectingException> {

    private final List< Throwable > collected = Collections.synchronizedList( new ArrayList<>() ) ;
    private final BiFunction< String, ImmutableList< Throwable >, E > constructor ;

    public Collector(
        final BiFunction< String, ImmutableList< Throwable >, E > constructor
    ) {
      this.constructor = checkNotNull( constructor ) ;
    }

    public void collect( final Throwable throwable ) {
      collected.add( checkNotNull( throwable ) ) ;
    }

    public void collect( final ImmutableList< Throwable > throwables ) {
      collected.addAll( throwables ) ;
    }

    public void throwIfAny( final String message ) throws E {
      if( ! empty() ) {
        // Using the same lock as {@code java.util.Collections.SynchronizedList} internally,
        // so it's correct.
        synchronized( collected ) {
          throw constructor.apply( message, ImmutableList.copyOf( collected ) ) ;
        }
      }
    }

    public< RETURNED > RETURNED returnOrThrow(
        final RETURNED returned,
        final String message
    ) throws E {
      if( ! empty() ) {
        throw constructor.apply( message, ImmutableList.copyOf( collected ) ) ;
      }
      return returned ;
    }

    public boolean empty() {
      return collected.isEmpty() ;
    }

    public Collector execute( final Task... tasks ) {
      for( final Task task : tasks ) {
        try {
          task.execute() ;
        } catch( final Exception e ) {
          collect( e ) ;
        }
      }
      return this ;
    }

    public interface Task {
      void execute() throws Exception ;
    }


  }

}
