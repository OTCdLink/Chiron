package io.github.otcdlink.chiron.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

class ConfigurationInspector< C extends Configuration > implements Configuration.Inspector< C > {

  private final Configuration.Factory< C > factory ;

  private final ImmutableSet< Configuration.Source > sources ;

  private final ImmutableSortedMap< String, ValuedProperty > valuedProperties ;

  /**
   * Mutable object.
   */
  private final List<Configuration.Property< C >> lastAccessed ;

  public ConfigurationInspector(
      final Configuration.Factory< C > factory,
      final ImmutableSet< Configuration.Source > sources,
      final ImmutableSortedMap< String, ValuedProperty > valuedProperties,
      final List<Configuration.Property< C >> lastAccessed
  ) {
    this.factory = checkNotNull( factory ) ;
    this.sources = checkNotNull( sources ) ;
    this.valuedProperties = checkNotNull( valuedProperties ) ;
    this.lastAccessed = checkNotNull( lastAccessed ) ;
  }

  public ValuedProperty valuedProperty( final Configuration.Property< C > property ) {
    return valuedProperties.get( property.name() ) ;
  }

  interface InspectorEnabled {
    ThreadLocal< Map<Configuration.Inspector, List<Configuration.Property> > > $$inspectors$$() ;
    ImmutableSortedMap< String, ValuedProperty > $$properties$$() ;
    ImmutableSet< Configuration.Source > $$sources$$() ;
    Configuration.Factory $$factory$$() ;
  }

// =======
// Support
// =======

  @Override
  public ImmutableMap< String, Configuration.Property< C >> properties() {
    final ImmutableMap.Builder< String, Configuration.Property< C >> builder
        = ImmutableMap.builder() ;
    for( final ValuedProperty valuedProperty : valuedProperties.values() ) {
      builder.put( valuedProperty.property.name(), valuedProperty.property ) ;
    }
    return builder.build() ;
  }

  @Override
  public Configuration.Property.Origin origin( final Configuration.Property< C > property ) {
    return valuedSlot( property ).origin ;
  }

  @Override
  public Configuration.Source sourceOf( final Configuration.Property< C > property ) {
    return valuedSlot( property ).source ;
  }

  @Override
  public ImmutableSet< Configuration.Source > sources() {
    return sources ;
  }

  @Override
  public String stringValueOf( final Configuration.Property< C > property ) {
    final ValuedProperty valuedProperty = valuedProperties.get( property.name() ) ;
    return valuedProperty == null ? null : valuedProperty.stringValue ;
  }

  @Override
  public String safeValueOf(
      final Configuration.Property< C > property,
      final String replacement
  ) {
    final ValuedProperty valuedProperty = valuedSlot( property ) ;
    final String stringValue ;
    if( valuedProperty.origin == Configuration.Property.Origin.BUILTIN ) {
      stringValue = valuedProperty.property.defaultValueAsString() ;
    } else {
      stringValue = valuedProperty.stringValue ;
    }
    if( stringValue == null ) {
      return null ;
    } else {
      if( property.obfuscator() == null ) {
        return stringValue ;
      } else {
        return property.obfuscator().obfuscate( stringValue, replacement ) ;
      }
    }
  }

  @Override
  public ImmutableList<Configuration.Property< C >> lastAccessed() {
    return ImmutableList.copyOf( lastAccessed ) ;
  }

  @Override
  public void clearLastAccessed() {
    lastAccessed.clear() ;
  }

  @Override
  public Configuration.Factory< C > factory() {
    return factory ;
  }

// ======
// Boring
// ======

  private ValuedProperty valuedSlot( final Configuration.Property< C > property ) {
    final ValuedProperty valuedProperty = valuedProperties.get( property.name() ) ;
    if( valuedProperty == null ) {
      throw new IllegalArgumentException( "Unknown: " + property ) ;
    }
    return valuedProperty;
  }


}
