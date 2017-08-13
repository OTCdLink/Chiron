package com.otcdlink.chiron.toolbox.latency;

import org.joda.time.DateTime;
import org.joda.time.Duration;

public final class LatencyTools {

  private LatencyTools() { }

  /**
   * Yes we really need additional typing here, type fails to propagate if defined only at
   * method level, while {@link ConcreteBuilder2} implements the interfaces with no prior
   * knowledge about wished type.
   */
  public static < CATEGORY extends Enum< CATEGORY > > Builder2.BeginTimeStep< CATEGORY >
  builder()
  {
    return new ConcreteBuilder2<>() ;
  }

  interface Builder2 {
    interface BeginTimeStep< CATEGORY extends Enum< CATEGORY > > {
      default EndTimeStep< CATEGORY > beginTime( DateTime dateTime ) {
        return beginTime( dateTime.getMillis() ) ;
      }
      EndTimeStep< CATEGORY > beginTime( long millisecondsSince1970 ) ;
    }

    interface EndTimeStep< CATEGORY extends Enum< CATEGORY > > {
      default CategoryStep< CATEGORY > duration( final Duration duration ) {
        return duration( duration.getMillis() ) ;
      }
      CategoryStep< CATEGORY > duration( long milliseconds ) ;
      CategoryStep< CATEGORY > end( long millisecondsSince1970 ) ;
    }

    interface CategoryStep< CATEGORY extends Enum< CATEGORY > > {
      OccurenceCountStep< CATEGORY > category( CATEGORY category ) ;
    }

    interface OccurenceCountStep< CATEGORY extends Enum< CATEGORY > > {
      PeakDurationStep< CATEGORY > occurenceCount( long milliseconds ) ;
    }

    interface PeakDurationStep< CATEGORY extends Enum< CATEGORY > > {
      PeakDelayStep< CATEGORY > peakDelay( long milliseconds ) ;
    }

    interface PeakDelayStep< CATEGORY extends Enum< CATEGORY > > {
      FinalCategoryStep< CATEGORY > cumulatedDelay( long milliseconds ) ;
    }

    interface FinalCategoryStep< CATEGORY extends Enum< CATEGORY > >
        extends CategoryStep< CATEGORY >
    {
      LatencyAverage< CATEGORY > build() ;
    }

  }

  private static class ConcreteBuilder2< CATEGORY extends Enum< CATEGORY > >
    implements
      Builder2.BeginTimeStep< CATEGORY >,
      Builder2.EndTimeStep< CATEGORY >,
      Builder2.CategoryStep< CATEGORY >,
      Builder2.OccurenceCountStep< CATEGORY >,
      Builder2.PeakDurationStep< CATEGORY >,
      Builder2.PeakDelayStep< CATEGORY >,
      Builder2.FinalCategoryStep< CATEGORY >
  {
    private Class< CATEGORY > categoryClass ;
    private long beginTime ;
    private long endTime ;
    private long[] array ;
    private CATEGORY currentCategory ;

    @Override
    public Builder2.EndTimeStep< CATEGORY > beginTime( final long milliseconds ) {
      beginTime = milliseconds ;
      return this ;
    }

    @Override
    public Builder2.CategoryStep< CATEGORY > duration( long milliseconds ) {
      endTime = beginTime + milliseconds ;
      return this ;
    }

    @Override
    public Builder2.CategoryStep< CATEGORY > end( long end ) {
      endTime = end ;
      return this ;
    }


    @Override
    public Builder2.OccurenceCountStep< CATEGORY > category( CATEGORY category )
    {
      categoryClass = ( Class<CATEGORY> ) category.getClass() ;
      array = LatencyAverage.newArray( categoryClass ) ;
      LatencyAverage.common( array, LatencyAverage.Common.BEGIN_TIME, beginTime ) ;
      LatencyAverage.common( array, LatencyAverage.Common.END_TIME, endTime ) ;
      currentCategory = category ;
      return this ;
    }

//    @Override
//    public Builder2.PeakDurationStep occurenceCount( long milliseconds ) {
//      LatencyAverage.counter(
//          array, LatencyAverage.Counter.OCCURENCE, ( CATEGORY ) currentCategory, milliseconds ) ;
//      return this ;
//    }

    @Override
    public Builder2.PeakDurationStep< CATEGORY >
    occurenceCount( long milliseconds ) {
      LatencyAverage.counter(
          array, LatencyAverage.Counter.OCCURENCE, currentCategory, milliseconds ) ;
      return this ;
    }

    @Override
    public Builder2.PeakDelayStep< CATEGORY > peakDelay( long milliseconds ) {
      LatencyAverage.counter(
          array, LatencyAverage.Counter.PEAK_DELAY, currentCategory, milliseconds ) ;
      return this ;
    }

    @Override
    public Builder2.FinalCategoryStep< CATEGORY > cumulatedDelay( long milliseconds ) {
      LatencyAverage.counter(
          array, LatencyAverage.Counter.CUMULATED_DELAY, currentCategory, milliseconds ) ;
      return this ;
    }

    @Override
    public LatencyAverage< CATEGORY > build() {
      return new LatencyAverage<>( categoryClass, array ) ;
    }
  }

}
