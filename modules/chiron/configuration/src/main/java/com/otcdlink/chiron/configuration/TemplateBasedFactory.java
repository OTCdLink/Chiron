package com.otcdlink.chiron.configuration;

import com.google.common.base.Converter;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.reflect.AbstractInvocationHandler;
import com.otcdlink.chiron.configuration.Configuration.Source;
import com.otcdlink.chiron.toolbox.converter.ConverterException;
import com.otcdlink.chiron.toolbox.converter.DefaultStringConverters;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Extend this class to create a {@link Configuration.Factory} with additional constraints
 * and defaults.
 *
 * @see ConfigurationTools#newFactory(Class)
 */
public abstract class TemplateBasedFactory< C extends Configuration >
    implements Configuration.Factory< C >
{
  private final Class< C > configurationClass ;
  private ConstructionKit< C > constructionKit = new ConstructionKit<>() ;
  protected final C using;
  private final ImmutableMap< String, Configuration.Property< C > > propertySet ;
  private final boolean checkAllPropertiesDefined ;

  protected TemplateBasedFactory( final Class< C > configurationClass ) throws DefinitionException {
    this.configurationClass = checkNotNull( configurationClass ) ;

    Class< ? > currentClass = configurationClass ;
    while( currentClass != null && ! currentClass.isAssignableFrom( Object.class ) ) {
      addClassMethodFeatures( currentClass ) ;
      addInterfaceMethodFeatures( currentClass ) ;
      currentClass = currentClass.getSuperclass() ;
    }
    constructionKit.collector
        = new PropertySetupCollector<>( configurationClass, this::acceptFeature ) ;

    using = constructionKit.collector.template ;
    //noinspection OverridableMethodCallDuringObjectConstruction
    initialize() ;
    propertySet = buildPropertyMap(
        constructionKit.features,
        constructionKit.transientConverters,
        constructionKit.transientNameTransformer
    ) ;
    this.checkAllPropertiesDefined = constructionKit.checkAllPropertiesDefined ;
    constructionKit = null ;
  }

  private void addInterfaceMethodFeatures( final Class< ? > classOrInterface ) {
    final Class< ? >[] interfaces = classOrInterface.getInterfaces() ;
    for( final Class< ? > someInterface : interfaces ) {
      addClassMethodFeatures( someInterface ) ;
      addInterfaceMethodFeatures( someInterface ) ;
    }
  }

  private void addClassMethodFeatures( final Class< ? > currentClass ) {
    final Method[] methods = currentClass.getDeclaredMethods() ;
    for( final Method method : methods ) {
      if( method.getParameterTypes().length == 0 ) {
        constructionKit.features.put( method, new HashMap<>() ) ;
      } else if( ! Modifier.isStatic( method.getModifiers() ) ) {
        throw new DefinitionException(
            "Should have no parameters: " + method.toGenericString() ) ;
      }
    }
  }

  private static class ConstructionKit< C extends Configuration > {
    private final Map< Method, Map< Configuration.PropertySetup.Feature, Object > > features
        = new HashMap<>() ;
    public PropertySetupCollector< C > collector = null ;
    public ImmutableMap< Class< ? >, Converter > transientConverters
        = DefaultStringConverters.ALL;
    public Configuration.NameTransformer transientNameTransformer = NameTransformers.IDENTITY ;
    public boolean checkAllPropertiesDefined = true ;
  }

  private void acceptFeature(
      final Method method,
      final Configuration.PropertySetup.Feature feature,
      final Object object
  ) {
    final Map< Configuration.PropertySetup.Feature, Object > propertyFeaturesMap
        = constructionKit.features.get( method ) ;
    if( propertyFeaturesMap == null ) {
      throw new IllegalArgumentException( "Unknown: " + method ) ;
    }
    propertyFeaturesMap.put( feature, object ) ;
  }


  /**
   * Only call from {@link #initialize()}.
   *
   * @param converters a non-null object.
   */
  protected final void setConverters(
      final ImmutableMap< Class< ? >, Converter > converters
  ) {
    checkInitializing() ;
    constructionKit.transientConverters = checkNotNull( converters ) ;
  }

  protected final void checkAllPropertiesDefined( final boolean check ) {
    checkInitializing() ;
    constructionKit.checkAllPropertiesDefined = check ;
  }

  /**
   * Only call from {@link #initialize()}.
   *
   * @param nameTransformer a non-null object.
   */
  protected final void setGlobalNameTransformer(
      final Configuration.NameTransformer nameTransformer
  ) {
    checkInitializing() ;
    constructionKit.transientNameTransformer = checkNotNull( nameTransformer ) ;
  }

  protected void initialize() { }


  /**
   * Override this method to hack values as
   * @param configuration a {@link Configuration}
   * @return
   */
  protected ImmutableMap< Configuration.Property< C >, TweakedValue > tweak(
      final C configuration
  ) {
    return ImmutableMap.of() ;
  }

  protected final < T > Configuration.PropertySetup< C, T > property(
      final T templateCallResult
  ) {
    checkInitializing() ;
    return constructionKit.collector.on( templateCallResult ) ;
  }

  private void checkInitializing() {
    checkState( constructionKit != null, "Don't call this method outside of #initialize()" ) ;
  }

// ==============================
// Building the map of properties
// ==============================

  private static< C extends Configuration > ImmutableMap< String, Configuration.Property< C >>
  buildPropertyMap(
      final Map< Method, Map< Configuration.PropertySetup.Feature, Object > > allFeatures,
      final ImmutableMap< Class< ? >, Converter > defaultConverters,
      final Configuration.NameTransformer globalNameTransformer
  ) throws DefinitionException {

    final Map< String, Configuration.Property< C >> propertyBuilder = new HashMap<>() ;

    for( final Map.Entry< Method, Map< Configuration.PropertySetup.Feature, Object > > entry
        : allFeatures.entrySet()
    ) {
      final Method method = entry.getKey() ;
      final Map< Configuration.PropertySetup.Feature, Object > features = entry.getValue() ;

      final Converter converter = resolveConverter( method, features, defaultConverters ) ;
      final Configuration.NameTransformer specificNameTransformer
          = resolveNameTransformer( features ) ;
      final String explicitName = resolveExplicitName( features ) ;
      final String propertyName = resolvePropertyName(
          method, explicitName, specificNameTransformer, globalNameTransformer ) ;
      checkUniqueName( method, propertyBuilder, propertyName ) ;
      final Object defaultValue = resolveDefaultValue( features ) ;
      final String defaultValueAsString
          = resolveDefaultValueAsString( features, defaultValue ) ;
      final boolean maybeNull = resolveMayBeNull( features ) ;
      final String documentation = resolveDocumentation( features ) ;
      final ConfigurationProperty< C > configurationProperty = new ConfigurationProperty<>(
          method,
          propertyName,
          defaultValue,
          defaultValueAsString,
          converter,
          maybeNull,
          documentation
      ) ;
      propertyBuilder.put( propertyName, configurationProperty ) ;
    }
    return ImmutableMap.copyOf( propertyBuilder ) ;
  }

  private static < C extends Configuration > void checkUniqueName(
      final Method originatingMethod,
      final Map< String, Configuration.Property< C >> propertyBuilder,
      final String propertyName
  ) {
    for( final Configuration.Property< C > property : propertyBuilder.values() ) {
      if( propertyName.equals( property.name() ) ) {
        throw new DefinitionException(
              "Duplicate name: property '" + propertyName
            + " in #" + originatingMethod.getName() + "()"
            + " collides with the one defined in #" + property.declaringMethod() + "()"
        ) ;
      }
    }
  }

  private static Object resolveDefaultValue(
      final Map< Configuration.PropertySetup.Feature, Object > features
  ) {
    return features.get(
        Configuration.PropertySetup.Feature.DEFAULT_VALUE );
  }

  private static String resolvePropertyName(
      final Method method,
      final String explicitName,
      final Configuration.NameTransformer specificNameTransformer,
      final Configuration.NameTransformer globalNameTransformer
  ) {
    if( explicitName == null ) {
      if( specificNameTransformer == null ) {
        if( globalNameTransformer == null ) {
          return method.getName() ;
        } else {
          return globalNameTransformer.transform( method.getName() ) ;
        }
      } else {
        return specificNameTransformer.transform( method.getName() ) ;
      }
    } else {
      return explicitName ;
    }
  }

  private static String resolveExplicitName(
      final Map<Configuration.PropertySetup.Feature, Object> features
  ) {
    return ( String ) features.get(
        Configuration.PropertySetup.Feature.NAME ) ;
  }

  private static Configuration.NameTransformer resolveNameTransformer(
      final Map< Configuration.PropertySetup.Feature, Object > features
  ) {
    return ( Configuration.NameTransformer ) features.get(
        Configuration.PropertySetup.Feature.NAME_TRANSFORMER );
  }

  private static Converter resolveConverter(
      final Method method,
      final Map< Configuration.PropertySetup.Feature, Object > features,
      final ImmutableMap< Class< ? >, Converter > defaultConverters
  ) {
    final Converter converter ;
    final Converter explicitConverter = ( Converter )
        features.get( Configuration.PropertySetup.Feature.CONVERTER ) ;
    if( explicitConverter == null ) {
      final Converter defaultConverter
          = defaultConverters.get( method.getReturnType() ) ;
      if( defaultConverter == null ) {
        throw new DefinitionException(
            "No " + ConfigurationTools.getNiceName( Converter.class )
                + " declared for " + method.getReturnType().getName() ) ;
      } else {
        converter = defaultConverter ;
      }
    } else {
      converter = explicitConverter ;
    }
    return converter ;
  }

  private static boolean resolveMayBeNull(
      final Map< Configuration.PropertySetup.Feature, Object > features
  ) {
    final boolean maybeNull ;
    final Boolean explicitMayBeNull = ( Boolean ) features.get(
        Configuration.PropertySetup.Feature.MAYBE_NULL ) ;
    maybeNull = explicitMayBeNull != null && explicitMayBeNull ;
    return maybeNull ;
  }

  private static String resolveDocumentation(
      final Map< Configuration.PropertySetup.Feature, Object > features
  ) {
    final String documentation = ( String ) features.get(
        Configuration.PropertySetup.Feature.DOCUMENTATION ) ;
    return documentation ;
  }

  private static String resolveDefaultValueAsString(
      final Map< Configuration.PropertySetup.Feature, Object > features,
      final Object defaultValue
  ) {
    final String defaultValueAsString ;
    final String explicitValueAsString = ( String ) features.get(
        Configuration.PropertySetup.Feature.DEFAULT_VALUE_AS_STRING ) ;
    if( explicitValueAsString == null && defaultValue != null ) {
      if( defaultValue == ValuedProperty.NULL_VALUE ) {
        defaultValueAsString = null ;
      } else {
        defaultValueAsString = defaultValue.toString() ;
      }
    } else {
      defaultValueAsString = explicitValueAsString ;
    }
    return defaultValueAsString ;
  }

// ======================
// public Factory methods
// ======================

  @Override
  public final C create( final Source source1, final Source... others )
      throws DeclarationException
  {
    final List< Source > sources = new ArrayList<>( others.length + 2 ) ;
    sources.add( new PropertyDefaultSource<>(
        configurationClass, ImmutableSet.copyOf( propertySet.values() ) ) ) ;
    sources.add( source1 ) ;
    Collections.addAll( sources, others ) ;

    final Map< String, ValuedProperty > values = new HashMap<>() ;
    final Set< Validation.Bad > exceptions = new HashSet<>() ;

    if( checkAllPropertiesDefined ) {
      for( final Source source : sources ) {
        if( source instanceof Source.Stringified ) {
          final Source.Stringified stringified =
              ( Source.Stringified ) source ;
          checkPropertyNamesAllDeclared(
              stringified, stringified.map().keySet(), propertySet, exceptions ) ;
        } else if( source instanceof Source.Raw ) {
          final Source.Raw raw = ( Source.Raw ) source ;
          checkPropertyNamesAllDeclared(
              raw, raw.map().keySet(), ImmutableSet.copyOf( propertySet.values() ), exceptions ) ;
        } else {
          throw new IllegalArgumentException( "Unsupported: " + source ) ;
        }
      }
    }

    for( final Map.Entry< String, Configuration.Property< C > > entry : propertySet.entrySet() ) {
      final Configuration.Property< C > property = entry.getValue() ;
      if( property.maybeNull() ) {
        feedWithDefaultNull( property, values ) ;
      }
      for( final Source source : sources ) {
        if( source instanceof Source.Stringified ) {
          feedWithValue(
              ( Source.Stringified ) source, values, property, exceptions ) ;
        } else if( source instanceof Source.Raw ) {
          feedWithValue( ( Source.Raw ) source, values, property, exceptions ) ;
        } else {
          throw new IllegalArgumentException( "Unsupported:" + source ) ;
        }
      }
    }

    if( ! exceptions.isEmpty() ) {
      throw new DeclarationException( this, ImmutableList.copyOf( exceptions ) ) ;
    }

    final ImmutableSortedMap< String, ValuedProperty > valuedProperties ;
    final C configuration ;
    {
      final ImmutableSortedMap< String, ValuedProperty > untweakedValuedProperties
          = ImmutableSortedMap.copyOf( values ) ;
      final C untweakedConfiguration = createProxy(
          ImmutableSet.copyOf( sources ), untweakedValuedProperties ) ;
      final ImmutableMap<Configuration.Property< C >, TweakedValue > tweak ;
      tweak = tweak( untweakedConfiguration ) ;
      if ( tweak == null || tweak.isEmpty() ) {
        valuedProperties = untweakedValuedProperties ;
        configuration = untweakedConfiguration ;
      } else {
        valuedProperties = verifyTweak( untweakedValuedProperties, tweak ) ;
        configuration = createProxy( ImmutableSet.copyOf( sources ), valuedProperties ) ;
      }
    }

    verifyNoUndefinedProperty( configuration, propertySet, valuedProperties ) ;

    final ImmutableList< Validation.Bad > validation = validate( configuration ) ;
    if( ! validation.isEmpty() ) {
      throw new ValidationException( this, validation ) ;
    }

    return configuration ;
  }

  private ImmutableSortedMap< String, ValuedProperty > verifyTweak(
      final ImmutableSortedMap< String, ValuedProperty > untweakedValuedProperties,
      final ImmutableMap<Configuration.Property< C >, TweakedValue> tweak
  ) throws DeclarationException {
    final Set< Validation.Bad > exceptions = new HashSet<>() ;
    final SortedMap< String, ValuedProperty > builder = new TreeMap<>() ;
    builder.putAll( untweakedValuedProperties ) ;
    for( final Map.Entry<Configuration.Property< C >, TweakedValue > tweakedEntry : tweak.entrySet() ) {
      final Configuration.Property< C > property = tweakedEntry.getKey() ;
      final TweakedValue tweakedValue = tweakedEntry.getValue() ;
      final Configuration.Property< C > existingProperty = propertySet.get( property.name() );
      if( existingProperty == null || existingProperty != property ) {
        addBadTweakedEntry( propertySet, exceptions, tweakedEntry,
            "No defined property " + property + " for value '" + tweakedValue.stringValue + "'" ) ;
      }
      if( tweakedValue.resolvedValue != null
          && ! mayAssign( property.type(), tweakedValue.resolvedValue.getClass() )
      ) {
        addBadTweakedEntry(
            propertySet,
            exceptions,
            tweakedEntry,
            "Can't assign a value of type " + tweakedValue.resolvedValue.getClass().getName()
                + " to a property of type " + property.type().getName()
        ) ;
      }
      builder.put(
          property.name(),
          new ValuedProperty(
              property,
              Sources.TWEAKING,
              tweakedValue.stringValue,
              tweakedValue.resolvedValue,
              Configuration.Property.Origin.TWEAK
          ) ) ;
    }
    if( ! exceptions.isEmpty() ) {
      throw new DeclarationException( this, ImmutableList.copyOf( exceptions ) ) ;
    }
    return ImmutableSortedMap.copyOf( builder ) ;
  }

  private static boolean mayAssign(
      final Class< ? > assigned,
      final Class< ? > assignee
  ) {
    return assigned.isAssignableFrom( assignee )
        || ( Integer.TYPE.equals( assigned ) && Integer.class.equals( assignee ) )
    ;
  }

  private static< C extends Configuration > void addBadTweakedEntry(
      final ImmutableMap< String, Configuration.Property< C >> definedProperties,
      final Set< Validation.Bad > exceptions,
      final Map.Entry<Configuration.Property< C >, TweakedValue > tweakedEntry,
      final String message
  ) {
    final ValuedProperty tweakedValuedProperty
        = new ValuedProperty( definedProperties.get( tweakedEntry.getKey().name() ) ) ;
    exceptions.add(
        new Validation.Bad( ImmutableList.of( tweakedValuedProperty ), message ) ) ;
  }

  @Override
  public final Class< C > configurationClass() {
    return configurationClass ;
  }

  @Override
  public final ImmutableMap< String, Configuration.Property< C >> properties() {
    final ImmutableMap.Builder< String, Configuration.Property< C >> builder
        = ImmutableMap.builder() ;
    for( final Configuration.Property< C > property : propertySet.values() ) {
      builder.put( property.name(), property ) ;
    }
    return builder.build() ;
  }

  protected ImmutableList< Validation.Bad > validate( final C configuration ) {
    return ImmutableList.of() ;
  }

// ============
// More methods
// ============

  private static < C extends Configuration > void verifyNoUndefinedProperty(
      final C configuration,
      final ImmutableMap< String, Configuration.Property< C >> properties,
      final ImmutableSortedMap< String, ValuedProperty > valuedProperties
  ) throws DeclarationException {
    final Validation.Accumulator< C > accumulator = new Validation.Accumulator<>( configuration ) ;
    for( final Configuration.Property< C > property : properties.values() ) {
      final ValuedProperty valuedProperty = valuedProperties.get( property.name() ) ;
      if( valuedProperty == null
          && ! ( property.maybeNull() || property.defaultValue() == ValuedProperty.NULL_VALUE )
      ) {
        accumulator.addInfrigementForNullity( ImmutableList.of( property ), "No value set" ) ;
      }
    }
    accumulator.throwDeclarationExceptionIfHasInfrigements() ;
  }

  @SuppressWarnings( "unchecked" )
  private C createProxy(
      final ImmutableSet<Source> sources,
      final ImmutableSortedMap< String, ValuedProperty > properties
  ) {
    final ThreadLocal< Map<Configuration.Inspector, List<Configuration.Property> > > inspectors = new ThreadLocal<>() ;
    final ImmutableMap.Builder< Method, ValuedProperty > builder = ImmutableMap.builder() ;
    for( final ValuedProperty valuedProperty : sortByName( properties.values() ) ) {
      builder.put( valuedProperty.property.declaringMethod(), valuedProperty ) ;
    }
    final ImmutableMap< Method, ValuedProperty > valuedPropertiesByMethod = builder.build() ;
    return ( C ) Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class[]{ configurationClass, ConfigurationInspector.InspectorEnabled.class },
        new ConfigurationInvocationHandler(
            inspectors,
            this,
            sources,
            properties,
            valuedPropertiesByMethod )
    ) ;
  }

  private static ImmutableList< ValuedProperty > sortByName(
      final ImmutableCollection< ValuedProperty > properties
  ) {
    final List< ValuedProperty > list = new ArrayList<>( properties.size() ) ;
    list.addAll( properties ) ;
    Collections.sort( list, new Comparator<ValuedProperty>() {
      @Override
      public int compare( final ValuedProperty first, final ValuedProperty second ) {
        return first.property.name().compareTo( second.property.name() ) ;
      }
    } ) ;
    return ImmutableList.copyOf( list ) ;
  }

  private void checkPropertyNamesAllDeclared(
      final Source source,
      final ImmutableSet< String > actualPropertyNames,
      final ImmutableMap< String, ? extends Configuration.Property> declaredProperties,
      final Set< Validation.Bad > exceptions
  ) {
    for( final String actualPropertyName : actualPropertyNames ) {
      if( ! declaredProperties.containsKey( actualPropertyName ) ) {
        exceptions.add( new Validation.Bad(
            "Unknown property name '" + actualPropertyName + "'",
            ImmutableSet.of( source )
        ) ) ;
      }
    }
  }

  private void checkPropertyNamesAllDeclared(
      final Source.Raw source,
      final ImmutableSet<Configuration.Property< C >> actualProperties,
      final ImmutableSet<Configuration.Property< C >> declaredProperties,
      final Set< Validation.Bad > exceptions
  ) {
    for( final Configuration.Property actualProperty : actualProperties ) {
      if( ! declaredProperties.contains( actualProperty ) ) {
        exceptions.add( new Validation.Bad(
            "Unknown property '" + actualProperty.name() + "'",
            ImmutableSet.<Source>of( source )
        ) ) ;
      }
    }
  }


  private static String toString(
      final ImmutableCollection< ValuedProperty > valuedProperties
  ) {
    final StringBuilder stringBuilder = new StringBuilder() ;
    boolean first = true ;
    for( final ValuedProperty valuedProperty : valuedProperties ) {
      if( first ) {
        first = false ;
      } else {
        stringBuilder.append( "; " ) ;
      }
      stringBuilder.append( valuedProperty.property.name() ) ;
      stringBuilder.append( '=' ) ;
      stringBuilder.append( valuedProperty.resolvedValueToString ) ;
    }
    return stringBuilder.toString() ;
  }

  private static< C extends Configuration > void feedWithValue(
      final Source.Stringified source,
      final Map< String, ValuedProperty > values,
      final Configuration.Property< C > property,
      final Set< Validation.Bad > exceptions
  ) {
    final String valueFromSource = source.map().get( property.name() ) ;
    if ( valueFromSource != null || source.map().containsKey( property.name() ) ) {
      final Object convertedValue = convertSafe( exceptions, property, valueFromSource, source ) ;
      if( convertedValue != CONVERSION_FAILED ) {
        final String valueToString = convertBack( exceptions, property, convertedValue, source ) ;
        final ValuedProperty valuedProperty = new ValuedProperty(
            property,
            source,
            valueToString,
            convertedValue,
            Configuration.Property.Origin.EXPLICIT
        ) ;
        values.put( property.name(), valuedProperty ) ;
      }
    }
  }

  private void feedWithValue(
      final Source.Raw source,
      final Map< String, ValuedProperty > values,
      final Configuration.Property< C > property,
      final Set< Validation.Bad > exceptions
  ) {
    if( source.map().containsKey( property ) ) {
      final boolean usingDefault = source instanceof PropertyDefaultSource ;
      final Object valueFromSource = source.map().get( property ) ;
      final Object value = valueFromSource == ValuedProperty.NULL_VALUE ? null : valueFromSource ;
      if( value != null && ! mayAssign( property, value ) ) {
        exceptions.add( new Validation.Bad(
            "Can't use '" + value + "' as a value for " + property.name(),
            ImmutableSet.<Source>of( source )
        ) ) ;
      }
      final String valueFromSourceToString = valueFromSource == ValuedProperty.NULL_VALUE ?
          null : ( String ) property.converter().reverse().convert( valueFromSource ) ;
      final ValuedProperty valuedProperty = new ValuedProperty(
          property,
          source,

          /** {@link Source.Raw} means there is no original String representation. */
          valueFromSourceToString,
          value,
          usingDefault ? Configuration.Property.Origin.BUILTIN :
              Configuration.Property.Origin.EXPLICIT
      ) ;
      values.put( property.name(), valuedProperty ) ;
    }
  }

  private boolean mayAssign( final Configuration.Property< C > property, final Object value ) {
    final Class< ? > propertyType = property.declaringMethod().getReturnType() ;
    if( propertyType.isPrimitive() ) {
      if( Integer.TYPE.equals( propertyType ) ) {
        return value instanceof Integer ;
      } else if ( Byte.TYPE.equals( propertyType ) ) {
        return value instanceof Byte ;
      } else if ( Short.TYPE.equals( propertyType ) ) {
        return value instanceof Short ;
      } else if ( Long.TYPE.equals( propertyType ) ) {
        return value instanceof Long ;
      } else if ( Double.TYPE.equals( propertyType ) ) {
        return value instanceof Double ;
      } else if ( Float.TYPE.equals( propertyType ) ) {
        return value instanceof Float ;
      } else if ( Character.TYPE.equals( propertyType ) ) {
        return value instanceof Character ;
      } else if ( Boolean.TYPE.equals( propertyType ) ) {
        return value instanceof Boolean ;
      }
      throw new IllegalArgumentException( "This should not happen. "
          + "Property: " + property + ", value: " + value ) ;
    } else {
      return propertyType.isAssignableFrom( value.getClass() );
    }
  }

  private void feedWithDefaultNull(
      final Configuration.Property< C > property,
      final Map< String, ValuedProperty > values
  ) {
    final ValuedProperty valuedProperty = new ValuedProperty(
        property,
        Sources.UNDEFINED,
        null,
        ValuedProperty.NULL_VALUE,
        Configuration.Property.Origin.BUILTIN
    ) ;
    values.put( property.name(), valuedProperty ) ;
  }

  private static Object convertSafe(
      final Set< Validation.Bad > exceptions,
      final Configuration.Property property,
      final String valueFromSource,
      final Source source
  ) {
    try {
      if( valueFromSource != null ) {
        return property.converter().convert( valueFromSource ) ;
      }
    } catch ( Exception e ) {
      addBadValidation( exceptions, property, source, e, "Conversion failed on property '" ) ;
    }
    return CONVERSION_FAILED ;
  }

  private static String convertBack(
      final Set< Validation.Bad > exceptions,
      final Configuration.Property property,
      final Object valueToConvertBack,
      final Source source
  ) {
    try {
      if( valueToConvertBack != null ) {
        return ( String ) property.converter().reverse().convert( valueToConvertBack ) ;
      }
    } catch ( Exception e ) {
      addBadValidation( exceptions, property, source, e, "Retroconversion failed on property '" ) ;
    }
    return null ;
  }

  private static void addBadValidation(
      final Set< Validation.Bad > exceptions,
      final Configuration.Property property,
      final Source source,
      Exception e,
      final String message
  ) {
    if( e instanceof ConverterException ) {
      e = ( Exception ) e.getCause();
    }
    exceptions.add( new Validation.Bad(
        message + property.name() + "': "
            + e.getClass().getName() + ", " + e.getMessage(),
        ImmutableSet.of( source )
    ) );
  }

  private static final Object CONVERSION_FAILED = new Object() {
    @Override
    public String toString() {
      return TemplateBasedFactory.class.getSimpleName() + "{(magic)CONVERSION_FAILED}" ;
    }
  } ;

  private class ConfigurationInvocationHandler
      extends AbstractInvocationHandler
      implements ConfigurationInspector.InspectorEnabled
  {
    private final ThreadLocal< Map<Configuration.Inspector, List<Configuration.Property> > > inspectors ;
    private final ImmutableSortedMap< String, ValuedProperty > properties ;
    private final Configuration.Factory factory ;
    private final ImmutableSet<Source> sources ;
    private final ImmutableMap< Method, ValuedProperty> valuedPropertiesByMethod ;

    public ConfigurationInvocationHandler(
        final ThreadLocal< Map<Configuration.Inspector, List<Configuration.Property> > > inspectors,
        final Configuration.Factory factory,
        final ImmutableSet<Source> sources,
        final ImmutableSortedMap< String, ValuedProperty > properties,
        final ImmutableMap< Method, ValuedProperty > valuedPropertiesByMethod
    ) {
      this.inspectors = inspectors ;
      this.factory = factory ;
      this.sources = sources ;
      this.properties = properties ;
      this.valuedPropertiesByMethod = valuedPropertiesByMethod ;
    }

    @SuppressWarnings( "NullableProblems" )
    @Override
    protected Object handleInvocation(
        final Object proxy,
        final Method method,
        final Object[] args
    ) throws Throwable {
      if ( method.getDeclaringClass()
          .equals( ConfigurationInspector.InspectorEnabled.class )
      ) {
        if ( "$$inspectors$$".equals( method.getName() ) ) {
          return inspectors ;
        } else if ( "$$properties$$".equals( method.getName() ) ) {
          return properties ;
        } else if ( "$$factory$$".equals( method.getName() ) ) {
          return factory ;
        } else if ( "$$sources$$".equals( method.getName() ) ) {
          return sources ;
        } else {
          throw new UnsupportedOperationException( "Unsupported: "
              + method.getDeclaringClass() + "#" + method.getName() ) ;
        }
      }

      ValuedProperty valuedProperty = valuedPropertiesByMethod.get( method ) ;
      final Map<Configuration.Inspector, List<Configuration.Property> > inspectorMap = inspectors.get() ;
      final boolean unresolvedProperty = valuedProperty == null ;
      if( unresolvedProperty ) {
        for( final Configuration.Property property
            : ( ( ImmutableMap< String, Configuration.Property> ) factory.properties() ).values()
        ) {
          if( method.equals( property.declaringMethod() ) ) {
            valuedProperty = new ValuedProperty( property ) ;
            break ;
          }
        }
        if( valuedProperty == null ) {
          throw new IllegalStateException(
              "Failed to generate a dumb " + ValuedProperty.class.getSimpleName()
                  + " object. This is annoying. Dumb " + ValuedProperty.class.getSimpleName()
                  + " object solves the case of uninitialized property when using "
                  + TemplateBasedFactory.class.getSimpleName() + "#tweak()"
          ) ;
        }
      }
      if( inspectorMap != null ) {
        for( final List<Configuration.Property> lastAccessedProperties : inspectorMap.values() ) {
          lastAccessedProperties.add( 0, valuedProperty.property ) ;
        }
      }
      if( unresolvedProperty ) {
        return ValuedProperty.safeNull( valuedProperty.property.type() ) ;
      } else {
        return valuedProperty.resolvedValue == ValuedProperty.NULL_VALUE
            ? ValuedProperty.safeNull( valuedProperty.property.type() )
            : valuedProperty.resolvedValue
        ;
      }
    }

    @Override
    public String toString() {
      return ConfigurationTools.getNiceName( configurationClass ) + "{"
          + TemplateBasedFactory.toString( valuedPropertiesByMethod.values() )
          + "}"
          ;
    }

    @Override
    public boolean equals( final Object other ) {
      if ( this == other ) {
        return true ;
      }
      if ( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final ConfigurationInspector.InspectorEnabled that
        = ( ConfigurationInspector.InspectorEnabled ) other ;
      return properties.equals( that.$$properties$$() ) ;
    }

    @Override
    public int hashCode() {
      return properties.hashCode() ;
    }

    @Override
    public ThreadLocal< Map<Configuration.Inspector, List<Configuration.Property> > > $$inspectors$$() {
      throw new UnsupportedOperationException(
          "Don't call this method. Implementing "
          + ConfigurationInspector.InspectorEnabled.class.getSimpleName()
          + " is just a hack to make #equals(Object) work"
      ) ;
    }

    @Override
    public ImmutableSortedMap< String, ValuedProperty > $$properties$$() {
      return properties ;
    }

    @Override
    public ImmutableSet<Source> $$sources$$() {
      return sources ;
    }

    @Override
    public Configuration.Factory $$factory$$() {
      return factory ;
    }
  }

}
