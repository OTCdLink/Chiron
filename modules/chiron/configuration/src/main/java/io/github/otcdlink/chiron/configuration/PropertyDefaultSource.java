package io.github.otcdlink.chiron.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Internal {@link Configuration.Source} that represents values
 * set using
 * {@link Configuration.PropertySetup.SetupAcceptor#defaultValue(Object)}.
 */
class PropertyDefaultSource< C extends Configuration > implements Configuration.Source.Raw< C > {

  private final String sourceName ;
  private final ImmutableMap< Configuration.Property< C >, Object > map ;

  public PropertyDefaultSource(
      final Class< C > configurationClass,
      final ImmutableSet< ? extends Configuration.Property< ? > > properties
  ) {
    this.sourceName = "java:" + TemplateBasedFactory.class.getSimpleName()
        + "{" + ConfigurationTools.getNiceName( configurationClass ) + "}" ;

    final ImmutableMap.Builder< Configuration.Property< C >, Object > builder
        = ImmutableMap.builder() ;

    for( final Configuration.Property property : properties ) {
      if( property.defaultValue() != null ) {
        builder.put(
            property,
            property.defaultValue()
        ) ;
      }
    }
    this.map = builder.build() ;
  }

  @Override
  public ImmutableMap< Configuration.Property< C >, Object > map() {
    return map ;
  }

  @Override
  public String sourceName() {
    return sourceName ;
  }

}
