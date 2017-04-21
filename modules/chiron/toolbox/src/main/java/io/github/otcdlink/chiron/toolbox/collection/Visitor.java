package io.github.otcdlink.chiron.toolbox.collection;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public interface Visitor< VISITED > {
  /**
   *
   * @param iterable a non-null object.
   * @param visitor a non-null object.
   * @return true if {@code visitor} ran for every object, false otherwise.
   */
  static< OBJECT > boolean visitAll(
      final Iterable< OBJECT > iterable,
      final Visitor< OBJECT > visitor
  ) {
    for( final OBJECT visited : iterable ) {
      if( ! visitor.visit( visited  ) ) {
        return false ;
      }
    }
    return true ;
  }

  /**
   *
   * @param visitable a non-null object.
   * @param predicate a non-null object.
   * @return the first visited object for which {@code predicate} applies, or null.
   */
  static< VISITED > VISITED find(
      final Visitable< VISITED > visitable,
      final Predicate< VISITED > predicate
  ) {
    final AtomicReference< VISITED > found = new AtomicReference<>( null ) ;
    visitable.visitAll( visited -> {
      if( predicate.test( visited ) ) {
        found.set( visited ) ;
        return false ;
      } else {
        return true ;
      }
    } ) ;
    return found.get() ;
  }

  /**
   *
   * @param object a possibly null object, depending on visited object.
   * @return true for continuing the visit if there are any object left,
   *         false for stopping it.
   */
  boolean visit( VISITED object ) ;

  interface Visitable< VISITED > {
    boolean visitAll( Visitor< VISITED > visitor ) ;
  }
}
