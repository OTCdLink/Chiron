package com.otcdlink.chiron.toolbox.clock;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * Mockable access to system time.
 */
public interface Clock {

  default DateTime getCurrentDateTime() {
    return new DateTime( currentTimeMillis(), DateTimeZone.UTC ) ;
  }

  long currentTimeMillis() ;

  Clock SYSTEM_CLOCK = new Clock() {
    @Override
    public long currentTimeMillis() {
      return System.currentTimeMillis() ;
    }

    @Override
    public String toString() {
      return Clock.class.getSimpleName() + ".SYSTEM_CLOCK{}" ;
    }
  } ;

  /**
   * Pre-instantiated object for the case where we just want to avoid a null.
   */
  DateTime ZERO = new DateTime( 0, DateTimeZone.UTC ) ;


}
