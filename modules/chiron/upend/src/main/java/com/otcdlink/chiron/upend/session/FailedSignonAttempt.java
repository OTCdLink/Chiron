package com.otcdlink.chiron.upend.session;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.toolbox.ComparatorTools;

import java.util.Comparator;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Counter for failed signon attempts, with support for Secondary authentication.
 * It contains several internal counters, one per
 * {@link SignonAttempt} value.
 * Each call to
 * {@link #increment(SignonAttempt)}
 * increments corresponding counter, and zeroes counter of lower rank (corresponding to lower
 * ordinal value of the enum).
 * This means one successful primary signon attempt followed by one secondary signon
 * failure reset the counter of failed primary signon attempts, while incrementing the
 * counter of secondary signon attempts. This prevents from resetting secondary signon
 * attempt counter by a successful primary attempt.
 */
public class FailedSignonAttempt {

  private static final int MAXIMUM_SIGNON_ATTEMPTS = 3 ;

  /**
   * If there weren't tests, we'd use the {@link #MAXIMUM_SIGNON_ATTEMPTS} directly.
   */
  private final int limit ;

  private final ImmutableMap< SignonAttempt, Integer > counters ;


  public static FailedSignonAttempt create() {
    return create( MAXIMUM_SIGNON_ATTEMPTS ) ;
  }

  public static FailedSignonAttempt create( final SignonAttempt signonAttempt ) {
    return create( signonAttempt, MAXIMUM_SIGNON_ATTEMPTS ) ;
  }

  /**
   * For tests only.
   */
  public static FailedSignonAttempt create( final int limit ) {
    return create( SignonAttempt.values()[ 0 ], limit ) ;
  }

  /**
   * For tests only.
   */
  public static FailedSignonAttempt create(
      final SignonAttempt signonAttempt,
      final int limit
  ) {
    return new FailedSignonAttempt(
        resetAndIncrement( MAP_FULL_OF_ZEROES, signonAttempt ),
        limit
    ) ;
  }

  private FailedSignonAttempt(
      final ImmutableMap< SignonAttempt, Integer > counters,
      final int limit
  ) {
    this.counters = checkNotNull( counters ) ;
    checkArgument( limit > 0 ) ;
    this.limit = limit ;
  }

  public FailedSignonAttempt increment( final SignonAttempt signonAttemptToIncrement ) {
    return new FailedSignonAttempt(
        resetAndIncrement( counters, signonAttemptToIncrement ),
        limit
    ) ;
  }

  public boolean hasReachedLimit() {
    for( final Map.Entry< SignonAttempt, Integer > entry : counters.entrySet() ) {
      if( entry.getValue() >= limit ) {
        return true ;
      }
    }
    return false ;
  }

  @Override
  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder() ;
    stringBuilder.append( getClass().getSimpleName() ) ;
    stringBuilder.append( "{" ) ;

    for( final Map.Entry< SignonAttempt, Integer > entry : counters.entrySet() ) {
      if( entry.getKey().ordinal() > 0 ) {
        stringBuilder.append( ";" ) ;
      }
      stringBuilder.append( entry.getKey() ) ;
      stringBuilder.append( "->" ) ;
      stringBuilder.append( entry.getValue() ) ;
    }
    stringBuilder.append( "}" ) ;
    return stringBuilder.toString() ;
  }


  /*package*/ static final ImmutableMap< SignonAttempt, Integer > MAP_FULL_OF_ZEROES
      = createMapFullOfZeroes() ;

  private static ImmutableMap< SignonAttempt, Integer > createMapFullOfZeroes() {
    final ImmutableMap.Builder< SignonAttempt, Integer > builder = ImmutableMap.builder() ;
    for( final SignonAttempt signonAttempt : SignonAttempt.values() ) {
      builder.put( signonAttempt, 0 ) ;
    }
    return builder.build() ;
  }

  private static ImmutableMap< SignonAttempt, Integer > resetAndIncrement(
      final ImmutableMap< SignonAttempt, Integer > map,
      final SignonAttempt signonAttempt
  ) {
    checkNotNull( signonAttempt ) ;
    final ImmutableMap.Builder< SignonAttempt, Integer > builder = ImmutableMap.builder() ;
    for( final Map.Entry< SignonAttempt, Integer > entry : map.entrySet() ) {
      if( signonAttempt == entry.getKey() ) {
        builder.put( signonAttempt, entry.getValue() + 1 ) ;
      } else if( signonAttempt.ordinal() > entry.getKey().ordinal() ) {
        builder.put( entry.getKey(), 0 ) ;
      } else {
        builder.put( entry.getKey(), entry.getValue() ) ;
      }
    }
    return builder.build() ;
  }

  public ImmutableMap< SignonAttempt, Integer > counters() {
    return counters ;
  }

  private static final Comparator< Map< SignonAttempt, Integer > > COUNTERS_COMPARATOR
      = ComparatorTools.mapComparator( ComparatorTools.INTEGER_COMPARATOR ) ;

  public static final Comparator< FailedSignonAttempt > COMPARATOR
      = new ComparatorTools.WithNull< FailedSignonAttempt >() {
        @Override
        protected int compareNoNulls(
            final FailedSignonAttempt first,
            final FailedSignonAttempt second
        ) {
          final int limitComparison = first.limit - second.limit ;
          if( limitComparison == 0 ) {
            final int countersComparison
                = COUNTERS_COMPARATOR.compare( first.counters, second.counters ) ;
            return countersComparison ;
          } else {
            return limitComparison ;
          }
        }
      }
  ;

}
