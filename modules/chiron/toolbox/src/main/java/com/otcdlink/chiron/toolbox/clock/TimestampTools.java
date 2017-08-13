package com.otcdlink.chiron.toolbox.clock;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public final class TimestampTools {

  private TimestampTools() { }

  public static DateTime nowUtc() {
    return new DateTime( DateTimeZone.UTC ) ;
  }

  public static DateTime newDateTimeUtc( final long instantMilliseconds ) {
    return new DateTime( instantMilliseconds, DateTimeZone.UTC ) ;
  }


}
