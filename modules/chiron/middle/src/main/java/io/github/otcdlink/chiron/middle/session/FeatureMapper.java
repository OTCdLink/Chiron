package io.github.otcdlink.chiron.middle.session;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Associates a method with its implementing object.
 */
public final class FeatureMapper {
  private final ImmutableMap< Method, Object > featureMethods ;
  public final ImmutableSet< Class< ? > > featureInterfaces ;

  public static FeatureMapper from(
      final Class< ? > class1, final Object object1
  ) {
    return new FeatureMapper( ImmutableClassToInstanceMap.copyOf(
        ImmutableBiMap.of( class1, object1 ) ) ) ;
  }

  public FeatureMapper from(
      final Class< ? > class1, final Object object1,
      final Class< ? > class2, final Object object2
  ) {
    return new FeatureMapper( ImmutableClassToInstanceMap.copyOf( ImmutableBiMap.of(
        class1, object1,
        class2, object2
    ) ) ) ;
  }

  public static FeatureMapper from( final Object delegate ) {
    final Class< ? >[] interfaces = delegate.getClass().getInterfaces() ;
    checkArgument( interfaces.length == 1 ) ;
    return new FeatureMapper( ImmutableClassToInstanceMap.copyOf( ImmutableBiMap.of(
        interfaces[ 0 ], delegate
    ) ) ) ;
  }

  /**
   * The {@link ImmutableClassToInstanceMap} performs useful checks.
   */
  public FeatureMapper( final ImmutableClassToInstanceMap< Object > features ) {
    final Map< Method, Object > featureMethodsBuilder = new HashMap<>() ;
    final Set< Class< ? > > featureClassBuilder = new HashSet<>() ;
    for( final Map.Entry< Class< ? >, Object > entry : features.entrySet() ) {
      final Method[] methods = entry.getKey().getMethods() ;
      for( final Method method : methods ) {
        if( ! method.getDeclaringClass().isInterface() ) {
          throw new IllegalArgumentException(
              "Should be an interface: " + method.getDeclaringClass() ) ;
        }
        if( ! Object.class.equals( method.getDeclaringClass() ) ) {
          if( featureMethodsBuilder.containsKey( method ) ) {
            throw new IllegalArgumentException( "Duplicate method " + method ) ;
          }
          featureMethodsBuilder.put( method, entry.getValue() ) ;
          featureClassBuilder.add( method.getDeclaringClass() ) ;
        }
      }
    }
    featureMethods = ImmutableMap.copyOf( featureMethodsBuilder ) ;
    featureInterfaces = ImmutableSet.copyOf( featureClassBuilder ) ;
  }

  Object implementor( final Method method ) {
    return featureMethods.get( method ) ;
  }

  Class[] addFeatureInterfaces( final Class firstElement ) {
    final Class[] array = new Class[ featureInterfaces.size() + 1 ] ;
    array[ 0 ] =  firstElement ;
    int index = 1 ;
    for( final Class featureInterface : featureInterfaces ) {
      array[ index ++ ] = featureInterface ;
    }
    return array ;
  }
}
