package com.otcdlink.chiron.toolbox.clock;

import org.joda.time.DateTime;

import java.util.concurrent.atomic.AtomicLong;

public interface UpdateableClock extends Clock {

  DateTime set( final long milliseconds ) ;

  DateTime set( final DateTime dateTime ) ;

  DateTime increment() ;

  DateTime increment( final long milliseconds ) ;


  static UpdateableClock newClock( final long milliseconds ) {
    return new Default( milliseconds ) ;
  }

  static UpdateableClock newClock( final DateTime dateTime ) {
    return newClock( dateTime.getMillis() ) ;
  }

  /**
   * Need to make it visible from outside because Redlight uses an {@link UpdateableClock}
   * but can't call {@link #newClock(DateTime)} because Java 7 can't call static methods
   * in interfaces.
   */
  class Default implements UpdateableClock {

    private final AtomicLong current ;

    public Default( final long milliseconds ) {
      current = new AtomicLong( milliseconds ) ;
    }

    @Override
    public DateTime set( final long milliseconds ) {
      current.set( milliseconds ) ;
      return getCurrentDateTime() ;
    }

    @Override
    public DateTime set( final DateTime dateTime ) {
      current.set( dateTime.getMillis() ) ;
      return dateTime ;
    }

    @Override
    public long currentTimeMillis() {
      return current.get() ;
    }

    public DateTime increment() {
      current.incrementAndGet() ;
      return getCurrentDateTime() ;
    }

    public DateTime increment( final long milliseconds ) {
      current.addAndGet( milliseconds ) ;
      return getCurrentDateTime() ;
    }

  }

}
