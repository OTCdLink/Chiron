package io.github.otcdlink.chiron.command;

import io.github.otcdlink.chiron.command.momentjs.MomentJs;
import io.github.otcdlink.chiron.command.momentjs.NashornTools;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import static io.github.otcdlink.chiron.command.Stamp.FLOOR_MILLISECONDS;
import static io.github.otcdlink.chiron.command.Stamp.MODULE;
import static org.assertj.core.api.Assertions.assertThat;

public class StampJavaScriptTest {

  @Test
  @Ignore( "Only for debug" )
  public void sameFormattingForMomentjsAndJodatime() throws Exception {
    check( System.currentTimeMillis() ) ;
  }

  @Test
  @Ignore( "Only for debug" )
  public void parseMethod() throws Exception {
    scriptEngine.eval( "function hello( who ) { return 'Hello, ' + who ; } " ) ;
    final Object evaluated = scriptEngine.eval( "hello( 'you' )" ) ;
    assertThat( evaluated ).isEqualTo( "Hello, you" ) ;
  }

  @Test
  @Ignore( "Only for debug" )
  public void printScript() throws Exception {
    LOGGER.info( "Using \n" + Stamp.SCRIPT ) ;
  }

  /**
   * Checks too many things at once, but avoids instantiating {@link #scriptEngine} too many times.
   */
  @Test
  public void runModule() throws Exception {
    scriptEngine.eval( Stamp.SCRIPT ) ;

    final Long parsedLongFromBase36 = parse36( Long.toString( 12345L, 36 ) ) ;
    assertThat( parsedLongFromBase36 ).isEqualTo( 12345L ) ;

    final Stamp stamp = Stamp.raw( 12345 * 1000 + FLOOR_MILLISECONDS, 678 ) ;
    checkParsing(
        stamp,
        this::parseToTuple2,
        "" + stamp.timestamp + ":" + stamp.counter
    ) ;

    checkParseAndExpand( stamp ) ;

    checkParseAndExpand(
        Stamp.raw(
            new DateTime( 2025, 12, 31, 23, 59, 59, DateTimeZone.UTC ).getMillis(),
            9876543210L
        ) ) ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger(
      StampJavaScriptTest.class ) ;

  private final ScriptEngine scriptEngine ;
  private final MomentJs momentJs ;

  public StampJavaScriptTest() throws ScriptException {
    scriptEngine = NashornTools.getScriptEngine() ;
    momentJs = new MomentJs( scriptEngine ) ;
  }

  private interface Transformation {
    String apply( final String s ) throws ScriptException ;
  }

  private Long parse36( final String s ) throws ScriptException {
    final Object evaluated = scriptEngine.eval( MODULE + ".parse36( '" + s + "' )" ) ;
    return evaluated == null ? null: ( ( Double ) evaluated ).longValue() ;
  }

  private String parseToTuple2( final String s ) throws ScriptException {
    final Object evaluated = scriptEngine.eval( MODULE + ".parseToTuple2( '" + s + "' ).raw()" ) ;
    return ( String ) evaluated ;
  }

  private String parseAndExpand( final String s ) throws ScriptException {
    final Object evaluated = scriptEngine.eval( MODULE + ".parseAndExpand( '" + s + "' )" ) ;
    return ( String ) evaluated ;
  }


  private void check( final long milliseconds1970utc ) throws ScriptException {
    final String momentjsFormat = momentjsFormat( milliseconds1970utc ) ;
    final String jodatimeFormat = jodatimeFormat( milliseconds1970utc ) ;
    LOGGER.info( "From " + milliseconds1970utc + ", Moment.js: '" + momentjsFormat + "', " +
        "Joda Time: '" + jodatimeFormat + "'." ) ;
    assertThat( momentjsFormat ).isEqualTo( jodatimeFormat ) ;
  }


  private String momentjsFormat( final long milliseconds1970utc ) throws ScriptException {
    final NashornTools.JsInvocable moment = momentJs.newMoment( milliseconds1970utc ) ;
    return moment.invoke( "format", Stamp.MOMENTJS_FORMAT ) ;
  }

  private static String jodatimeFormat( final long milliseconds1970utc ) {
    return JODATIME_FORMATTER.print( milliseconds1970utc ) ;
  }

  /**
   * Gives the same result as {@link Stamp#MOMENTJS_FORMAT}.
   */
  private static final DateTimeFormatter JODATIME_FORMATTER =
      DateTimeFormat.forPattern( "YYYY-MM-dd_HH:mm:ss.SSS Z" ) ;

  private static void logParsingOutcome(
      final Stamp stamp,
      final String outcome
  ) {
    LOGGER.info(
        "Parsed " + stamp.toString() +
        " (" + stamp.timestamp + "|" + stamp.flooredSeconds() +
        ":" + stamp.counter + ")" + " into '" + outcome + "'."
    ) ;
  }

  private static void checkParsing(
      final Stamp stamp,
      final Transformation transformation,
      final String expected
  ) throws ScriptException {
    final String fragment = transformation.apply( stamp.asStringRoundedToFlooredSecond() ) ;
    logParsingOutcome( stamp, fragment ) ;
    assertThat( fragment ).isEqualTo( expected ) ;
  }

  private void checkParseAndExpand( final Stamp stamp )
      throws ScriptException
  {
    checkParsing(
        stamp,
        this::parseAndExpand,
        defaultScriptFormatting( stamp )
    ) ;
  }

  private static String defaultScriptFormatting( final Stamp stamp ) {
    return jodatimeFormat( stamp.timestamp ) + " :: " + stamp.counter ;
  }

}
