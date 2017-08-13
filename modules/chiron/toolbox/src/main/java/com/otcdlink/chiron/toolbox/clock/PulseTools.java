package com.otcdlink.chiron.toolbox.clock;

import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

final class PulseTools {
  private PulseTools() { }

  public static boolean transitioned(
      final DateTime previous,
      final DateTime current,
      final Pulse.Resolution resolution
  ) {
    checkNotNull( current ) ;
    if( previous == null ) {
      return true ;
    }
    boolean changed = false ;
    switch( resolution ) {
      case DECISECOND :
        changed |= previous.getMillis() / 100 != current.getMillis() / 100 ;
      case SECOND :
        changed |= previous.getSecondOfMinute() != current.getSecondOfMinute() ;
      case DECASECOND :
        changed |= previous.getSecondOfMinute() / 10 != current.getSecondOfMinute() / 10 ;
      case MINUTE :
        changed |= previous.getMinuteOfHour() != current.getMinuteOfHour() ;
      case HOUR :
        changed |= previous.getHourOfDay() != current.getHourOfDay() ;
      case DAY :
        changed |= previous.getDayOfMonth() != current.getDayOfMonth() ;
        changed |= previous.getMonthOfYear() != current.getMonthOfYear() ;
        changed |= previous.getYear() != current.getYear() ;
        break ;
      default :
        throw new IllegalArgumentException( "Unsupported: " + resolution ) ;
    }
    return changed ;
  }

  public static DateTime next( final DateTime dateTime, final Pulse.Resolution resolution ) {
    final DateTime next ;
    switch( resolution ) {
      case DECISECOND :
        next = new DateTime(
            dateTime.getYear(),
            dateTime.getMonthOfYear(),
            dateTime.getDayOfMonth(),
            dateTime.getHourOfDay(),
            dateTime.getMinuteOfHour(),
            dateTime.getSecondOfMinute(),
            ( dateTime.getMillisOfSecond() / 100 ) * 100,
            dateTime.getZone()
        ).plusMillis( 100 ) ;
        break ;
      case SECOND :
        next = new DateTime(
            dateTime.getYear(),
            dateTime.getMonthOfYear(),
            dateTime.getDayOfMonth(),
            dateTime.getHourOfDay(),
            dateTime.getMinuteOfHour(),
            dateTime.getSecondOfMinute(),
            dateTime.getZone()
        ).plusSeconds( 1 ) ;
        break ;
      case DECASECOND :
        next = new DateTime(
            dateTime.getYear(),
            dateTime.getMonthOfYear(),
            dateTime.getDayOfMonth(),
            dateTime.getHourOfDay(),
            dateTime.getMinuteOfHour(),
            ( dateTime.getSecondOfMinute() * 10 ) / 10,
            dateTime.getZone()
        ).plusSeconds( 1 ) ;
        break ;
      case MINUTE :
        next = new DateTime(
            dateTime.getYear(),
            dateTime.getMonthOfYear(),
            dateTime.getDayOfMonth(),
            dateTime.getHourOfDay(),
            dateTime.getMinuteOfHour(),
            0,
            dateTime.getZone()
        ).plusMinutes( 1 ) ;
        break ;
      case HOUR :
        next = new DateTime(
            dateTime.getYear(),
            dateTime.getMonthOfYear(),
            dateTime.getDayOfMonth(),
            dateTime.getHourOfDay(),
            0,
            0,
            dateTime.getZone()
        ).plusHours( 1 ) ;
        break ;
      case DAY :
        next = new DateTime(
            dateTime.getYear(),
            dateTime.getMonthOfYear(),
            dateTime.getDayOfMonth(),
            0,
            0,
            0,
            dateTime.getZone()
        ).plusDays( 1 ) ;
        break ;
      default :
        throw new IllegalArgumentException( "Unsupported: " + resolution ) ;
    }
    return next ;
  }

  public static long millisecondsBeforeNextTransition(
      final DateTime now,
      final Pulse.Resolution resolution
  ) {
    final DateTime next = next( now, resolution ) ;
    final long difference = next.getMillis() - now.getMillis() ;
    checkState( difference >= 0, "" ) ;
    return difference ;
  }

  public static DateTime mostRecent( final DateTime first, final DateTime second ) {
    if( first == null ) {
      if( second == null ) {
        return null ;
      } else {
        return second ;
      }
    } else {
      if( second == null ) {
        return first ;
      } else {
        return first.compareTo( second ) < 0 ? second : first ;
      }
    }
  }
}
