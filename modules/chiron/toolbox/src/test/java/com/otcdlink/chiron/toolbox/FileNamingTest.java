package com.otcdlink.chiron.toolbox;

import com.google.common.primitives.Ints;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FileNamingTest {


  @Test
  public void simpleConversion() throws Exception {
    LOGGER.info( "Using " + INTEGER_FILE_NAMING + "." ) ;
    assertThat( INTEGER_FILE_NAMING.parse( "12" ) ).isEqualTo( 12 ) ;
    assertThat( INTEGER_FILE_NAMING.render( 12 ) ).isEqualTo( "12" ) ;
  }

  @Test
  public void suffixing() throws Exception {
    final FileNaming< Integer > sandwiched = INTEGER_FILE_NAMING.suffix( "i" ) ;
    LOGGER.info( "Using " + sandwiched + "." ) ;
    assertThat( sandwiched.parse( "12i" ) ).isEqualTo( 12 ) ;
    assertThat( sandwiched.render( 12 ) ).isEqualTo( "12i" ) ;
  }

  @Test
  public void timestampCompactUtc() throws Exception {
    final FileNaming.TimestampedCompactUtc fileNaming =
        new FileNaming.TimestampedCompactUtc( PREFIX, SUFFIX ) ;
    LOGGER.info( "Using " + fileNaming ) ;

    assertBadPrefix( fileNaming, "..." + COMPACT_TIMESTAMP_AS_STRING + SUFFIX ) ;

    assertBadSuffix( fileNaming, PREFIX + COMPACT_TIMESTAMP_AS_STRING + "..." ) ;

    assertThatThrownBy(
        () -> fileNaming.parse( PREFIX + "200000000" + SUFFIX ) )
        .isInstanceOf( FileNaming.ParseException.class )
        .hasMessageContaining( "Timestamp does not match pattern" )
    ;

    assertThat( fileNaming.render( TIMESTAMP ) )
        .isEqualTo( PREFIX + COMPACT_TIMESTAMP_AS_STRING + SUFFIX ) ;

    assertThat( fileNaming.parse( PREFIX + COMPACT_TIMESTAMP_AS_STRING + SUFFIX ) )
        .isEqualTo( TIMESTAMP ) ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( FileNamingTest.class ) ;

  private static final FileNaming< Integer > INTEGER_FILE_NAMING =
      new FileNaming< Integer >( Ints.stringConverter() ) {} ;

  private static final DateTime TIMESTAMP =
      new DateTime( 2017, 11, 13, 9, 1, 33, DateTimeZone.UTC ) ;

  private static final String COMPACT_TIMESTAMP_AS_STRING = DateTimeFormat
      .forPattern( FileNaming.TimestampedCompactUtc.PATTERN )
      .withZoneUTC()
      .print( TIMESTAMP )
  ;

  private static final String PREFIX = "prefix_" ;
  private static final String SUFFIX = "_suffix" ;


  private void assertBadPrefix(
      final FileNaming.TimestampedCompactUtc fileNaming,
      final String stringWithBadPrefix
  ) {
    assertThatThrownBy(
        () -> fileNaming.parse( stringWithBadPrefix ) )
        .isInstanceOf( FileNaming.ParseException.class )
        .hasMessageContaining( "Bad prefix" )
    ;
  }

  private void assertBadSuffix(
      final FileNaming.TimestampedCompactUtc fileNaming,
      final String stringWithBadSuffix
  ) {
    assertThatThrownBy(
        () -> fileNaming.parse( stringWithBadSuffix ) )
        .isInstanceOf( FileNaming.ParseException.class )
        .hasMessageContaining( "Bad suffix" )
    ;
  }



}