package io.github.otcdlink.chiron.command.momentjs;

import io.github.otcdlink.chiron.command.Stamp;
import io.github.otcdlink.chiron.command.StampJavaScriptTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * Moment.js performs various time-related operations, especially formatting, with no reliance
 * on a specific Web browser. So we can test JavaScript code for parsing {@link Stamp}.
 * http://momentjs.com
 */
public final class MomentJs {

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER =
      LoggerFactory.getLogger( StampJavaScriptTest.class ) ;

  /**
   * 103 kB, non-minified.
   */
  private static final String MOMENT_JS_FILE = "/moment-2.10.3.js" ;

  private final ScriptEngine scriptEngine ;

  @SuppressWarnings( "unused" )
  public MomentJs() throws ScriptException {
    this( NashornTools.getScriptEngine() ) ;
  }
  public MomentJs( final ScriptEngine scriptEngine ) throws ScriptException {
    this.scriptEngine = scriptEngine ;
    NashornTools.loadScriptInto( scriptEngine, MOMENT_JS_FILE ) ; // Includes validity check.
  }

  /**
   * https://gist.github.com/UnquietCode/5614860
   */
  public NashornTools.JsInvocable newMoment( final Long epoch ) throws ScriptException {
    final Object moment ;
    try {
      if( epoch == null ) {
        moment = ( ( Invocable ) scriptEngine ).invokeFunction( "moment" ) ;
      } else {
        moment = ( ( Invocable ) scriptEngine ).invokeFunction( "moment", epoch ) ;
      }
    } catch( final Exception ex ) {
      throw new ScriptException( ex ) ;
    }
    return new NashornTools.JsInvocable( ( Invocable ) scriptEngine, moment ) ;
  }

}
