package com.otcdlink.chiron.toolbox;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.junit.Test;

import static com.otcdlink.chiron.toolbox.TcpPortBookerByRangeTest.State.AVAILABLE;
import static com.otcdlink.chiron.toolbox.TcpPortBookerByRangeTest.State.BOOKED;
import static com.otcdlink.chiron.toolbox.TcpPortBookerByRangeTest.State.UNTOUCHABLE;
import static org.junit.Assert.assertEquals;

public class TcpPortBookerByRangeTest {

// ======
// Bounds
// ======

  @Test( expected = IllegalArgumentException.class )
  public void rangeTooSmall ( ) {
    new TcpPortBookerByRange( 1, 3, 1 ) ;
  }

  @Test( expected = IllegalArgumentException.class )
  public void rangeTooBig( ) {
    new TcpPortBookerByRange( 1, 3, 4 ) ;
  }

  @Test( expected = IllegalArgumentException.class )
  public void switchedBounds( ) {
    new TcpPortBookerByRange( 3, 1, 2 ) ;
  }


// ==============
// Serious things
// ==============

  @Test
  public void veryFirstAllocationWorks() {
    final InstrumentedBooker booker = new InstrumentedBooker( 1, 3, 2 ) ;
    assertEquals( 2, booker.find() ) ;
    check( booker, BOOKED, AVAILABLE, AVAILABLE ) ;
  }

  @Test
  public void terminationClosesSentinels() {
    final InstrumentedBooker booker = new InstrumentedBooker( 1, 4, 2 ) ;
    booker.find() ;
    booker.find() ;
    booker.terminate() ;
    check( booker, AVAILABLE, AVAILABLE, AVAILABLE, AVAILABLE ) ;
  }

  @Test
  public void allocateInSecondIntervalBecauseFirstHasFirstPortBooked() {
    final InstrumentedBooker booker = new InstrumentedBooker( 1, 4, 2, AVAILABLE, BOOKED, AVAILABLE, AVAILABLE ) ;
    assertEquals( 4, booker.find() ) ;
    check( booker, AVAILABLE, BOOKED, /*sentinel*/ BOOKED, AVAILABLE ) ;
  }

  @Test
  public void severalAllocationsInSequence() {
    final InstrumentedBooker booker = new InstrumentedBooker( 1, 4, 2 ) ;
    assertEquals( 2, booker.find() ) ;
    assertEquals( 4, booker.find() ) ;
    check( booker, /*sentinel*/ BOOKED, AVAILABLE, /*sentinel*/ BOOKED, AVAILABLE ) ;
  }

  @Test( expected = TcpPortBookerByRange.UnexpectedOpenPortException.class )
  public void detectIllegalPortOpeningInsidedAvailableRange( ) {
    final InstrumentedBooker booker = new InstrumentedBooker( 1, 3, 3 ) ;
    assertEquals( 2, booker.find() ) ;
    booker.getBookableList().get( 3 ).open( true ) ;
    booker.find() ;
  }

  @Test( expected = TcpPortBookerByRange.NoMorePortAvailableException.class )
  public void noRangeAvailable() {
    final InstrumentedBooker booker = new InstrumentedBooker( 1, 4, 2, BOOKED, AVAILABLE, BOOKED, AVAILABLE ) ;
    booker.find() ;
  }


// =======
// Fixture
// =======

  private static void check( final InstrumentedBooker booker, final State... bookedStates ) {

    final ImmutableList< State > expectedStates = ImmutableList.< State >builder()
        .add( UNTOUCHABLE )
        .add( bookedStates )
        .build()
    ;

    final ImmutableList< State > actualStates = ImmutableList.copyOf(
        Iterables.transform( booker.getBookableList(), BooleanBookable::state ) ) ;

    assertEquals( expectedStates, actualStates ) ;
  }



  private static class InstrumentedBooker extends TcpPortBookerByRange {
    private final ImmutableList< BooleanBookable > bookableList ;

    public InstrumentedBooker(
        final int firstUsablePort,
        final int lastUsablePort,
        final int rangeSize

    ) {
      this( firstUsablePort, lastUsablePort, rangeSize, createAvailableStates( lastUsablePort ) ) ;
    }

    private static State[] createAvailableStates( final int last ) {
      final State[] states = new State[ last ];
      for( int i = 0 ; i < last ; i++ ) {
        states[ i ] = AVAILABLE ;
      }
      return states ;
    }


    /**
     * @param states initial state of {@link Bookable} instances, starting with the one corresponding to port 1.
     */
    public InstrumentedBooker(
        final int firstUsablePort,
        final int lastUsablePort,
        final int rangeSize,
        final State... states
    ) {
      super( firstUsablePort, lastUsablePort, rangeSize ) ;
      Preconditions.checkArgument( states.length == lastUsablePort ) ;
      final ImmutableList.Builder< BooleanBookable > builder = ImmutableList.builder() ;
      builder.add( new BooleanBookable( 0 ) ) ;
      for( int i = 0 ; i < states.length ; i++ ) {
        final State state = states[ i ];
        builder.add( new BooleanBookable( i + 1, state ) ) ;
      }
      bookableList = builder.build() ;
    }

    public ImmutableList< BooleanBookable > getBookableList() {
      return bookableList ;
    }

    @Override
    protected Bookable createBookable( final int port ) {
      return bookableList.get( port ) ;
    }

  }

  /**
   * Cannot be public for static import.
   */
    /*package*/ enum State {
    AVAILABLE, BOOKED, UNTOUCHABLE
  }


// ========
// Bookable
// ========


  private static class BooleanBookable implements TcpPortBookerByRange.Bookable {
    private final int number ;

    private State state ;


    public BooleanBookable( final int number ) {
      this( number, UNTOUCHABLE ) ;
    }

    public BooleanBookable( final int number, final State state ) {
      Preconditions.checkArgument( number >= 0 ) ;
      this.number = number ;
      this.state = state ;
    }

    public int number() {
      return number ;
    }

    public State state() {
      return state ;
    }

    public void open( final boolean open ) {
      checkTouchable() ;
      this.state = open ? BOOKED : AVAILABLE ;
    }

    @Override
    public boolean isOpen() {
      checkTouchable() ;
      return this.state == BOOKED ;
    }

    @Override
    public boolean open() {
      checkTouchable() ;
      if( state == BOOKED ) {
        return false ;
      } else {
        state = BOOKED ;
        return true ;
      }
    }

    @Override
    public void close() {
      checkTouchable() ;
      state = AVAILABLE ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "[" + number + ", " + state + "]";
    }

    private void checkTouchable() {
      if( state == UNTOUCHABLE ) {
        throw new IllegalStateException( "Can't change state for " + this ) ;
      }
    }

  }


}