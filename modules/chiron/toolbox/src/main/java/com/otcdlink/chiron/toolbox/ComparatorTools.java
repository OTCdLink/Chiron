package com.otcdlink.chiron.toolbox;

import org.joda.time.Duration;
import org.joda.time.LocalDate;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public final class ComparatorTools {

  private ComparatorTools() { }

  public static final Comparator< String > STRING_COMPARATOR = new WithNull< String >() {
    @Override
    protected int compareNoNulls( final String first, final String second ) {
      return first.compareTo( second ) ;
    }
  } ;

  public static int compare( final long first, final long second ) {
    if( first == second ) {
      return 0 ;
    } else {
      if( first > second ) {
        return 1 ;
      } else {
        return -1 ;
      }
    }
  }

  /**
   * Compares two {@code Map}s, using a per-value {@code Comparator}.
   * It doesn't compare the keys, but verifies that for a given key, both {@code Map}s
   * do have equal values.
   *
   * @return a non-null object.
   */
  public static< KEY, VALUE > Comparator< Map< KEY, VALUE > > mapComparator(
      final Comparator< VALUE > valueComparator
  ) {
    return new WithNull< Map< KEY, VALUE > >() {
      @Override
      protected int compareNoNulls( final Map< KEY, VALUE > first, final Map< KEY, VALUE > map2 ) {
        final int sizeComparison = first.size() - map2.size() ;
        if( sizeComparison == 0 ) {
          for( final Entry< KEY, VALUE > entry1 : first.entrySet() ) {
            final VALUE value2 = map2.get( entry1.getKey() ) ;
            if( value2 == null ) {
              return -1 ; // Arbitrary.
            } else {
              final int valueComparison = valueComparator.compare( entry1.getValue(), value2 ) ;
              if( valueComparison != 0 ) {
                return valueComparison ;
              }
            }
          }
          return 0 ;
        } else {
          return sizeComparison ;
        }
      }
    } ;
  }

  public static< VALUE > Comparator< Collection < VALUE > > collectionComparator(
      final Comparator< VALUE > valueComparator
  ) {
    return new WithNull< Collection< VALUE > >() {

      @Override
      protected int compareNoNulls(
          final Collection< VALUE > first,
          final Collection< VALUE > collection2
      ) {
        final int sizeComparison = first.size() - collection2.size() ;
        if( sizeComparison == 0 ) {
          final Iterator< VALUE > iterator1 = first.iterator() ;
          final Iterator< VALUE > iterator2 = collection2.iterator() ;
          while( iterator1.hasNext() ) {
            final VALUE value1 = iterator1.next() ;
            final VALUE value2 = iterator2.next() ;
            final int valueComparison = valueComparator.compare( value1, value2 ) ;
            if( valueComparison != 0 ) {
              return valueComparison ;
            }
          }
        } else {
          return sizeComparison ;
        }
        return 0 ;
      }
    } ;
  }

  public static < COMPARABLE extends Comparable< COMPARABLE > > Comparator< COMPARABLE >
  newComparableComparator() {
    return new WithNull< COMPARABLE >() {
      @Override
      protected int compareNoNulls( COMPARABLE first, COMPARABLE second ) {
        return first.compareTo( second ) ;
      }
    } ;
  }

  public static final Comparator< Boolean > BOOLEAN_COMPARATOR = new WithNull< Boolean >() {
    @Override
    protected int compareNoNulls( final Boolean first, final Boolean boolean2 ) {
      return first.compareTo( boolean2 ) ;
    }
  } ;

  public static final Comparator< Integer > INTEGER_COMPARATOR = new WithNull< Integer >() {
    @Override
    protected int compareNoNulls( final Integer first, final Integer integer2 ) {
      return first.compareTo( integer2 ) ;
    }
  } ;

  public static final Comparator< Long > LONG_COMPARATOR = new WithNull< Long >() {
    @Override
    protected int compareNoNulls( final Long first, final Long long2 ) {
      return first.compareTo( long2 ) ;
    }
  } ;

  public static final Comparator< BigDecimal > BIG_DECIMAL_COMPARATOR = new WithNull< BigDecimal>() {

    @Override
    protected int compareNoNulls( final BigDecimal first, final BigDecimal second ) {
      return first.compareTo( second ) ;
    }
  } ;

  public static final Comparator< Float > FLOAT_COMPARATOR = new WithNull< Float>() {
    @Override
    protected int compareNoNulls( final Float first, final Float second ) {
      return first.compareTo( second ) ;
    }
  } ;

  public static < E extends Enum< E > > Comparator< E > enumComparator() {
    return new WithNull< E >() {
      @Override
      protected int compareNoNulls( final E first, final E second ) {
        return first.compareTo( second ) ;
      }
    } ;
  }

  public static final Comparator< Duration > DURATION_COMPARATOR = new WithNull< Duration >() {
    @Override
    protected int compareNoNulls( final Duration first, final Duration second ) {
      return first.compareTo( second ) ;
    }
  } ;

  public static final Comparator< Class > CLASS_COMPARATOR = new WithNull<Class>() {
    @Override
    protected int compareNoNulls( final Class first, final Class second ) {
      if( first == second ) {
        return 0 ;
      } else {
        final int nameComparison = first.getName().compareTo( second.getName() ) ;
        if( nameComparison == 0 ) {
          // We've got some classloader trickery or other unlikely stuff.
          return -1 ;
        } else {
          return nameComparison ;
        }
      }
    }
  } ;

  public static final Comparator<LocalDate > LOCAL_DATE_COMPARATOR = new WithNull< LocalDate >() {
    @Override
    protected int compareNoNulls( final LocalDate first, final LocalDate second ) {
      return first.compareTo( second ) ;
    }
  } ;

  /**
   * Tries to return a consistent value, regardless of parameter order.
   */
  public static final Comparator< Object > LAST_CHANCE_COMPARATOR = new WithNull<Object>() {
    @Override
    protected int compareNoNulls( final Object first, final Object second ) {
      if( first == second ) {
        return 0 ;
      } else if( first.getClass() == second.getClass() && first instanceof Comparable ) {
            return ( ( Comparable ) first ).compareTo( second ) ;
      } else {
        final int toStringComparison = first.toString().compareTo( second.toString() ) ;
        if( toStringComparison == 0 ) {
          final int systemIdentity
              = System.identityHashCode( first ) - System.identityHashCode( second ) ;
          if( systemIdentity == 0 ) {
            return 1 ; // Bad: this makes comparison non-symmetric.
          } else {
            return systemIdentity ;
          }
        } else {
          return toStringComparison ;
        }
      }
    }
  } ;


  /**
   * Considers {@code null} as smaller than non-null.
   */
  public abstract static class WithNull< OBJECT > implements Comparator< OBJECT > {

    @Override
    public final int compare( final OBJECT first, final OBJECT second ) {
      if( first == second ) {
        return 0 ;
      }
      if( first == null ) {
        if( second == null ) {
          return 0 ;
        } else {
          return -1 ;
        }
      } else {
        if( second == null ) {
          return 1 ;
        } else {
          return compareNoNulls( first, second ) ;
        }
      }
    }

    protected abstract int compareNoNulls( OBJECT first, OBJECT second ) ;

  }
  /**
   * Considers {@code null} as bigger than non-null.
   */
  public abstract static class NullAlwaysBigger< OBJECT > implements Comparator< OBJECT > {

    @Override
    public final int compare( final OBJECT first, final OBJECT second ) {
      if( first == null ) {
        if( second == null ) {
          return 0 ;
        } else {
          return 1 ;
        }
      } else {
        if( second == null ) {
          return -1 ;
        } else {
          return compareNoNulls( first, second ) ;
        }
      }
    }

    protected abstract int compareNoNulls( OBJECT first, OBJECT second ) ;

  }

  public static class ForEnum< ENUM extends Enum > extends WithNull< ENUM > {
    @Override
    protected int compareNoNulls( final ENUM first, final ENUM second ) {
      return first.compareTo( second ) ;
    }
  }
}
