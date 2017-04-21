package io.github.otcdlink.chiron.command.automatic;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Keeps a {@code String =>} {@link FeatureRegistry.FeatureMethod} {@code Map}
 */
public class FeatureRegistry {

  public final ImmutableList< Class< ? > > featureClasses ;
  public final ImmutableSet< Type > supportedTypes ;
  public final ImmutableMap< String, FeatureMethod > featureMethods ;

  public FeatureRegistry(
      final ImmutableList< Class< ? > > featureClasses,
      final ImmutableSet< Type > supportedTypes
  ) {
    this.featureClasses = checkNotNull( featureClasses ) ;
    this.supportedTypes = checkNotNull( supportedTypes ) ;
    final BiMap< String, FeatureMethod > featureMethodsBuilder = HashBiMap.create() ;

    for( final Class< ? > featureClass : featureClasses ) {
      for( final Method method : featureClass.getDeclaredMethods() ) {
        featureMethodsBuilder.put( method.getName(), new FeatureMethod( method ) ) ;
      }
    }
    featureMethods = ImmutableMap.copyOf( featureMethodsBuilder ) ;
  }

  public FeatureMethod featureMethod( final Method method ) {
    for( final FeatureMethod featureMethod : featureMethods.values() ) {
      if( featureMethod.method.equals( method ) ) {
        return featureMethod ;
      }
    }
    throw new IllegalArgumentException(
        "Unregistered method " + method + " in " + featureMethods ) ;
  }

  public static final class FeatureMethod {
    public final Method method ;
    public final String commandName ;
    public final AsCommand commandAnnotation ;

    /**
     * Skips the first parameter which always corresponds to
     * {@link io.github.otcdlink.chiron.command.Command#endpointSpecific}.
     */
    public final ImmutableList< Type > specificArgumentTypes ;

    private FeatureMethod( final Method method ) {
      this(
          method,
          method.getName(),
          method.getAnnotation( AsCommand.class ),
          specificArgumentTypes( method )
      ) ;
    }

    private static ImmutableList< Type > specificArgumentTypes( final Method method ) {
      final Class< ? >[] argumentTypes = method.getParameterTypes() ;
      checkArgument( argumentTypes.length >= 1,
          "Must have at least one argument (endpoint-specific): " + method ) ;
      final ImmutableList.Builder< Type > typeListBuilder = ImmutableList.builder() ;
      for( int i = 1 ; i < argumentTypes.length ; i ++ ) {
        typeListBuilder.add( argumentTypes[ i ] ) ;
      }
      return typeListBuilder.build() ;
    }

    private FeatureMethod(
        final Method method,
        final String commandName,
        final AsCommand commandAnnotation,
        final ImmutableList< Type > specificArgumentTypes
    ) {
      this.method = checkNotNull( method ) ;
      this.commandName = checkNotNull( commandName ) ;
      this.commandAnnotation = checkNotNull( commandAnnotation ) ;
      this.specificArgumentTypes = checkNotNull( specificArgumentTypes ) ;
    }

    public ArgumentIterator argumentIterator() {
      return new InternalArgumentInterator() ;
    }

    public interface ArgumentIterator extends Iterator< Type > {
      /**
       * Starts at 1 because we skipped the one corresponding to
       * {@link io.github.otcdlink.chiron.command.Command#endpointSpecific}.
       */
      int index() ;
    }

    private class InternalArgumentInterator
        extends AbstractIterator< Type >
        implements ArgumentIterator
    {
      private int index = 0 ;
      @Override
      protected Type computeNext() {
        if( index == specificArgumentTypes.size() ) {
          return endOfData() ;
        } else {
          final Type type = specificArgumentTypes.get( index ) ;
          index ++ ;
          return type ;
        }
      }

      @Override
      public int index() {
        return index ;
      }
    }
  }
}
