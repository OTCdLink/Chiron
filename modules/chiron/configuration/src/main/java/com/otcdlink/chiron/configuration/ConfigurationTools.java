package com.otcdlink.chiron.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Utility methods to create {@link Configuration.Factory} and {@link Configuration.Inspector}
 * objects.
 */
public final class ConfigurationTools {

  public static < C extends Configuration > Configuration.Factory< C > newFactory(
      final Class< C > configurationClass
  ) {
    return new TemplateBasedFactory< C >( configurationClass ) { } ;
  }


  /**
   * Creates a new {@link Configuration.Inspector}.
   * Warning: during its lifetime, an {@link Configuration.Inspector} keeps track of every method call on the
   * {@link Configuration} object. For this reason, a too broadly scoped {@link Configuration.Inspector}
   * can lead to excessive memory consumption.
   */
  public static < C extends Configuration > Configuration.Inspector< C > newInspector(
      final C configuration
  ) {
    final ConfigurationInspector.InspectorEnabled inspectorEnabled
        = ( ConfigurationInspector.InspectorEnabled ) configuration ;
    final ThreadLocal< Map<Configuration.Inspector, List<Configuration.Property> > > inspectorsThreadLocal
        = inspectorEnabled.$$inspectors$$() ;
    Map<Configuration.Inspector, List<Configuration.Property> > inspectors = inspectorsThreadLocal.get() ;
    if( inspectors == null ) {
      inspectors = new WeakHashMap<>() ;
      inspectorsThreadLocal.set( inspectors ) ;
    }
    final List<Configuration.Property> accessedProperties = new ArrayList<>() ;

    @SuppressWarnings( "unchecked" )
    final ConfigurationInspector< C > inspector = new ConfigurationInspector(
        inspectorEnabled.$$factory$$(),
        inspectorEnabled.$$sources$$(),
        inspectorEnabled.$$properties$$(),
        accessedProperties
    ) ;
    inspectors.put(
        inspector,
        accessedProperties
    ) ;
    return inspector ;
  }

  public static < C extends Configuration > String lastValueAsString(
      final Configuration.Inspector< C > inspector
  ) {
    return inspector.safeValueOf( inspector.lastAccessed().get( 0 ), "******" ) ;
  }
  
// ===============
// Our own cooking
// ===============


  public static String getNiceName( final Class originClass ) {
    String className = originClass.getSimpleName() ;
    Class enclosingClass = originClass.getEnclosingClass() ;
    while( enclosingClass != null ) {
      className = enclosingClass.getSimpleName() + "$" + className ;
      enclosingClass = enclosingClass.getEnclosingClass() ;
    }
    return className ;
  }


}
