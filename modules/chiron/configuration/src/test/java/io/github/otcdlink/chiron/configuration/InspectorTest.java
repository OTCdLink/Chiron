package io.github.otcdlink.chiron.configuration;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.otcdlink.chiron.configuration.Configuration.Inspector;
import static io.github.otcdlink.chiron.configuration.Configuration.Property;
import static io.github.otcdlink.chiron.configuration.ConfigurationTools.newFactory;
import static io.github.otcdlink.chiron.configuration.ConfigurationTools.newInspector;
import static io.github.otcdlink.chiron.configuration.Sources.newSource;
import static org.assertj.core.api.Assertions.assertThat;

public class InspectorTest {

  public interface Simple extends Configuration {
    int number() ;
    String string() ;
    File file() ;
  }

  @Test
  public void inspectorIsolation() throws Exception {
    final Configuration.Factory< Simple > factory = newFactory( Simple.class ) ;
    final Simple configuration = factory.create( newSource(
        "string = s",
        "number = 1",
        "file = f"
    ) ) ;
    final Property< Simple > numberProperty = factory.properties().get( "number" ) ;
    final Property< Simple > stringProperty = factory.properties().get( "string" ) ;
    final Property< Simple > fileProperty = factory.properties().get( "file" ) ;
    final Inspector< Simple > inspector1 = newInspector( configuration ) ;
    final Inspector< Simple > inspector2 = newInspector( configuration ) ;

    assertThat( inspector1.lastAccessed() ).isEmpty() ;

    configuration.string() ;
    assertThat( inspector1.lastAccessed() ).containsExactly( stringProperty ) ;
    assertThat( inspector2.lastAccessed() ).containsExactly( stringProperty ) ;
    configuration.number() ;
    assertThat( inspector1.lastAccessed() ).containsExactly( numberProperty, stringProperty ) ;
    assertThat( inspector2.lastAccessed() ).containsExactly( numberProperty, stringProperty ) ;

    final AtomicReference< ImmutableList< Property< Simple > > > lastAccessed3
        = new AtomicReference<>( null ) ;
    final Thread thread = new Thread( new Runnable() {
      @Override
      public void run() {
        final Inspector< Simple > inspector3 = newInspector( configuration ) ;
        configuration.file() ;
        lastAccessed3.set( inspector3.lastAccessed() ) ;
      }
    } ) ;
    thread.start() ;
    thread.join() ;

    assertThat( inspector1.lastAccessed() ).containsExactly( numberProperty, stringProperty ) ;
    assertThat( inspector2.lastAccessed() ).containsExactly( numberProperty, stringProperty ) ;
    assertThat( lastAccessed3.get() ).containsExactly( fileProperty ) ;
  }

  @Test
  public void factory() throws Exception {
    final Configuration.Factory< Simple > factory = newFactory( Simple.class ) ;
    final Simple configuration = factory.create( newSource(
        "string = s",
        "number = 1",
        "file = f"
    ) ) ;
    final Inspector< Simple > inspector = newInspector( configuration ) ;
    assertThat( inspector.factory() ).isSameAs( factory ) ;
  }

}
