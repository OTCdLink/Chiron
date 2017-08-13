package com.otcdlink.chiron.command.momentjs;

import com.google.common.base.Charsets;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import com.otcdlink.chiron.command.StampJavaScriptTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.Reader;

import static com.google.common.base.Preconditions.checkArgument;

public final class NashornTools {

  private static final Logger LOGGER = LoggerFactory.getLogger( StampJavaScriptTest.class ) ;

  private static final String SCRIPT_ENGINE_NAME = "nashorn" ;

  private NashornTools() { }

  public static void loadScriptInto(
      final ScriptEngine scriptEngine,
      final String scriptResourceName
  ) throws ScriptException {
    LOGGER.info( "Loading '" + scriptResourceName + "' into " + scriptEngine + " ..." ) ;
    checkScriptEngine( scriptEngine ) ;

    final CharSource charSource = Resources.asCharSource(
        StampJavaScriptTest.class.getResource( scriptResourceName ), Charsets.UTF_8 ) ;

    try( final Reader reader = charSource.openStream() ) {
      scriptEngine.eval( reader ) ;
    } catch( final Exception ex ) {
      throw new ScriptException( ex ) ;
    }
  }

  public static ScriptEngine checkScriptEngine( final ScriptEngine scriptEngine ) {
    final String engineName = scriptEngine.getFactory().getEngineName() ;
    checkArgument( engineName.toLowerCase().contains( SCRIPT_ENGINE_NAME ),
        "Unexpected engine name: '" + engineName + "'" ) ;
    return scriptEngine ;
  }

  public static ScriptEngine getScriptEngine() {
    LOGGER.debug( "Loading Nashorn ..." ) ;
    final ScriptEngineManager manager = new ScriptEngineManager() ;
    final ScriptEngine scriptEngine = manager.getEngineByName( SCRIPT_ENGINE_NAME ) ;
    LOGGER.info( "Loaded " + scriptEngine + "." ) ;
    return scriptEngine ;
  }

  /**
   * https://gist.github.com/UnquietCode/5614860
   */
  public static class JsInvocable {
    private final Invocable invocable ;
    private final Object object ;

    public JsInvocable( final Invocable invocable, final Object object ) {
      this.invocable = invocable ;
      this.object = object ;
    }

    public String invoke( final String method, final Object... args ) {
      try {
        return invocable.invokeMethod( object, method, args ).toString() ;
      } catch( final Exception ex ) {
        throw new RuntimeException( ex ) ;
      }
    }
  }
}
