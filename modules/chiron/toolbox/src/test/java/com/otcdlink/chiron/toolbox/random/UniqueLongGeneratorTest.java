package com.otcdlink.chiron.toolbox.random;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.NumberTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.stream.LongStream;

import static com.otcdlink.chiron.toolbox.random.UniqueLongGenerator.newPseudorandom;
import static org.assertj.core.api.Assertions.assertThat;

class UniqueLongGeneratorTest {

  @Test
  void bareXoroshiroDemo() {
    final Xoroshiro xoroshiro3 = new Xoroshiro( 0 ) ;
    print( 100, LongStream.generate( xoroshiro3 ) ) ;
  }

  @Test
  void monotonicAlone() {
    final int count = 100 ;
    assertThat( generate( new Monotonic( 0 ), count ) ).containsExactly( rangeAsArray( count ) ) ;
  }

  @Test
  void monotonicAloneWithStartvalue() {
    final int count = 10 ;
    assertThat( generate( new Monotonic( 3 ), count ) ).containsExactly( rangeFrom( 3, count ) ) ;
  }

  @Test
  void xoroshiroAlone() {
    assertThat( generate( new Xoroshiro( 0 ), 10 ) ).containsExactly(
        5807750865143411619L,
        38375600193489914L,
        1180499099402622421L,
        4579004446585139580L,
        6979005179107534507L,
        -2975419943234953760L,
        2740647768329926480L,
        -5045498572009775062L,
        -4497921319187413182L,
        -4412624960269183820L
    ) ;
  }

  @Test
  void xoroshiroThenMonotonic() {
    assertThat(
        generate( newPseudorandom( 2, 7, 1, 999L ).asLongStreamWithOwnReservation(), 10 )
    ).containsExactly(
        3L,
        6L,
        5L,
        7L,
        8L,
        9L,
        10L,
        11L,
        12L,
        13L
    ) ;
  }

  @Test
  void xoroshiroBetweenBounds() {
    final int lowerInclusive = 0 ;
    final int higherExclusive = 10 ;
    final UniqueLongGenerator generator = newPseudorandom(
        lowerInclusive, higherExclusive, 1_000_000, 0 );
    LOGGER.info( "Generating random values in [" +lowerInclusive + ".." + higherExclusive +
        "[ ..." ) ;
    final LongPredicate reservation = generator.newCollisionEvaluator() ;
    assertThat(
        generate( generator.asLongStream( reservation ), higherExclusive - lowerInclusive )
    ).contains( rangeFrom( 0, 9 ) ) ;
    final UniqueLongGenerator.Setup setup = generator.exportSetup() ;
    LOGGER.info( "Now we have " + setup + "." ) ;
    assertThat( setup.progress.kind() ).isEqualTo( LongGenerator.Kind.XOROSHIRO ) ;

    LOGGER.info( "Generating one more value, we should switch to " + LongGenerator.Kind.MONOTONIC +
        " since " + reservation + " is full." ) ;
    assertThat( generate( generator.asLongStream( reservation ), 1 ) )
        .containsExactly( ( long ) ( higherExclusive ) ) ;
    assertThat( generator.exportSetup().progress.kind() )
        .isEqualTo( LongGenerator.Kind.MONOTONIC ) ;
  }


  @Test
  void exportSetup() {
    final int generationCount = 20 ;
    final LongFormatter longFormatter = new LongFormatter( generationCount ) ;
    final UniqueLongGenerator generator1 = newPseudorandom( 2, 15, 2, 999L ) ;
    final UniqueLongGenerator.CollisionEvaluator collisionEvaluator1 =
        generator1.newCollisionEvaluator() ;
    final UniqueLongGenerator.CollisionEvaluator collisionEvaluator2 =
        generator1.newCollisionEvaluator() ;

    for( int i = 0 ; i < generationCount ; i ++ ) {
      final UniqueLongGenerator.Setup exported1 = generator1.exportSetup() ;
      final long generated1 = generator1.nextRandomValue( collisionEvaluator1 ) ;
      final UniqueLongGenerator generator2 = UniqueLongGenerator.newFromSetup( exported1 ) ;
      final long generated2 = generator2.nextRandomValue( collisionEvaluator2 ) ;
      final UniqueLongGenerator.Setup reexported1 = generator1.exportSetup() ;
      LOGGER.info( "Generated " + longFormatter.format( generated1 ) + ", " +
          "now setup is " + reexported1 + "." ) ;
      assertThat( generated2 ).isEqualTo( generated1 ) ;
      assertThat( generator2.exportSetup() ).isEqualTo( reexported1 ) ;
    }
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( UniqueLongGeneratorTest.class ) ;

  private static void print( final int occurenceCount, final LongStream longStream ) {
    final LongFormatter longFormatter = new LongFormatter( occurenceCount - 1 ) ;
    longStream
        .limit( occurenceCount )
        .forEach( l -> LOGGER.info( "Generated" + longFormatter.format( l ) + "." ) )
    ;
  }

  private static class LongFormatter {
    private int count = 0 ;
    private final int maximumCount ;
    private final String formatString ;

    private LongFormatter( int count ) {
      this.maximumCount = count - 1 ;
      final int numberOfDigits = NumberTools.numberOfDigits( maximumCount ) ;
      formatString = "%0" + numberOfDigits + "d" ;
    }

    private String format( final long l ) {
      return
          "[" + String.format( formatString, count++ ) + "/" +
          String.format( formatString, maximumCount ) + "]: " +
          String.format( "%64s", Long.toBinaryString( l ) ).replace( ' ', '0' ) +
          " " + ( l >= 0 ? "+" : "" ) + l
      ;
    }
  }

  private ImmutableList< Long > generate( final LongSupplier longSupplier, final int count ) {
    return generate( LongStream.generate( longSupplier ), count ) ;
  }

  private ImmutableList< Long > generate( final LongStream longStream, final int count ) {
    final LongFormatter longFormatter = new LongFormatter( count ) ;
    return longStream
        .limit( count )
        .peek( l -> LOGGER.info( "Generated" + longFormatter.format( l ) + "." ) )
        .boxed()
        .collect( ImmutableList.toImmutableList() )
    ;
  }

  private static Long[] toArray( final Collection< Long > collection ) {
    return collection.toArray( new Long[ 0 ] ) ;
  }

  private static Long[] rangeAsArray( final int count ) {
    return rangeAsArray( 0, count - 1 ) ;
  }

  private static Long[] rangeFrom( final int startInclusive, final int count ) {
    return rangeAsArray( startInclusive, startInclusive + count - 1 ) ;
  }

  private static Long[] rangeAsArray( final int startInclusive, final int endInclusive ) {
    Preconditions.checkArgument( startInclusive <= endInclusive ) ;
    Preconditions.checkArgument( startInclusive >= 0 ) ;

    final Long[] array = new Long[ ( endInclusive - startInclusive + 1 ) ] ;
    long value = startInclusive ;
    for( int i = 0 ; i < array.length ; i ++ ) {
      array[ i ] = value ++ ;
    }
    return array ;
  }


  @BeforeEach
  void init( final TestInfo testInfo) {
    LOGGER.info( "*** Running " + testInfo.getDisplayName() + " ***" ) ;
  }

}