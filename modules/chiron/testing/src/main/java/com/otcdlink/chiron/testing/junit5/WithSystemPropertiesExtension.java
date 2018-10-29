package com.otcdlink.chiron.testing.junit5;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;

public class WithSystemPropertiesExtension
    implements BeforeEachCallback, AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      WithSystemPropertiesExtension.class ) ;

  @Override
  public void beforeEach( final ExtensionContext extensionContext ) {
    final AnnotatedElement annotatedElement = extensionContext.getElement().get() ;
    final WithSystemProperties annotationRepeated =
        annotatedElement.getAnnotation( WithSystemProperties.class ) ;
    final ImmutableMap.Builder< String, String > originalProperties = ImmutableMap.builder() ;
    if( annotationRepeated == null ) {
      final WithSystemProperty annotationAlone =
          annotatedElement.getAnnotation( WithSystemProperty.class ) ;
      processSinglePropertyEntry( originalProperties, annotationAlone ) ;
    } else {
      for( final WithSystemProperty entry : annotationRepeated.value() ) {
        processSinglePropertyEntry( originalProperties, entry ) ;
      }
    }
    store( extensionContext ).put( STORE_KEY, originalProperties.build() ) ;
  }

  private static void processSinglePropertyEntry( ImmutableMap.Builder<String, String> originalProperties, WithSystemProperty entry ) {
    LOGGER.debug( "Setting alternate property( '" + entry.name() + "', '" +
        entry.value() + "' )" ) ;
    final String originalValue = System.getProperty( entry.name() ) ;
    originalProperties.put(
        entry.name(),
        originalValue == null ? NULL_SYSTEM_PROPERTY_VALUE : originalValue
    ) ;
    System.setProperty( entry.name(), entry.value() ) ;
  }

  @Override
  public void afterEach( final ExtensionContext extensionContext ) throws Exception {
    final ImmutableMap< String, String > savedProperties =
        store( extensionContext ).get( STORE_KEY, ImmutableMap.class ) ;
    for( final Map.Entry< String, String > entry : savedProperties.entrySet() ) {
      final String value = entry.getValue() ;
      if( isNullPropertyValue( value ) ) {
        System.clearProperty( entry.getKey() ) ;
      } else {
        System.setProperty( entry.getKey(), entry.getValue() ) ;
      }
    }
  }

  @SuppressWarnings( "StringEquality" )
  private static boolean isNullPropertyValue( String value ) {
    return value == NULL_SYSTEM_PROPERTY_VALUE ;
  }

  private static final String NULL_SYSTEM_PROPERTY_VALUE =
      WithSystemPropertiesExtension.class.getSimpleName() + "{MAGIC_FOR_NULL}" ;

  private static final String STORE_KEY = "PreTestEntries" ;

  private static ExtensionContext.Store store( final ExtensionContext extensionContext ) {
    final ExtensionContext.Store store = extensionContext.getStore(
        ExtensionContext.Namespace.create( extensionContext.getUniqueId() ) ) ;
    return store ;
  }

}
