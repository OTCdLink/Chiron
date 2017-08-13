package com.otcdlink.chiron.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Validates a whole {@link Configuration} before a {@link Configuration.Factory}
 * makes it available.
 */
public interface Validation {

  class Bad {
    final ImmutableList< ValuedProperty > properties ;
    final ImmutableSet< Configuration.Source > sources ;
    public final String message ;

    public Bad( final ImmutableList< ValuedProperty > properties, final String message ) {
      this.properties = checkNotNull( properties ) ;
      this.message = checkNotNull( message ) ;
      final ImmutableSet.Builder< Configuration.Source > builder = ImmutableSet.builder() ;
      for( final ValuedProperty property : properties ) {
        builder.add( property.source ) ;
      }
      this.sources = builder.build() ;
    }

    public Bad( final String message ) {
      this.properties = ImmutableList.of() ;
      this.sources = ImmutableSet.of() ;
      this.message = checkNotNull( message ) ;
    }

    public Bad( final String message, final ImmutableSet< Configuration.Source > sources ) {
      this.properties = ImmutableList.of() ;
      this.sources = checkNotNull( sources ) ;
      this.message = checkNotNull( message ) ;
    }

    @Override
    public boolean equals( final Object other ) {
      if ( this == other ) {
        return true  ;
      }
      if ( other == null || getClass() != other.getClass() ) {
        return false ;
      }

      Bad bad = ( Bad ) other ;

      if ( ! message.equals( bad.message ) ) {
        return false ;
      }
      if ( ! properties.equals( bad.properties ) ) {
        return false ;
      }
      if ( ! sources.equals( bad.sources ) ) {
        return false ;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = properties.hashCode() ;
      result = 31 * result + sources.hashCode() ;
      result = 31 * result + message.hashCode() ;
      return result ;
    }
  }

  /**
   * Offers built-in validation methods that accumulate {@link Validation.Bad} instances.
   */
  class Accumulator< C extends Configuration > {

    private final ImmutableList.Builder< Bad > builder = ImmutableList.builder() ;

    private final ConfigurationInspector< C > inspector ;

    public Accumulator( final C configuration ) {
      this.inspector = ( ConfigurationInspector< C > ) ConfigurationTools.newInspector( configuration ) ;
    }

    public ImmutableList< Bad > done() {
      return builder.build() ;
    }

    public void throwDeclarationExceptionIfHasInfrigements() throws DeclarationException {
      final ImmutableList< Bad > done = done() ;
      if( ! done.isEmpty() ) {
        throw new DeclarationException( inspector.factory(), done ) ;
      }
    }


    public Accumulator< C > justVerify( final boolean mustBeTrue, final String message ) {
      if( ! mustBeTrue ) {
        justAdd( message ) ;
      }
      return this ;
    }



    public Accumulator< C > justAdd( final String message ) {
      builder.add( new Bad( message ) ) ;
      return this ;
    }

    public Accumulator< C > clearLastAccessed() {
      inspector.clearLastAccessed() ;
      return this ;
    }


    public Accumulator< C > verify( final boolean mustBeTrue ) {
      if( ! mustBeTrue ) {
        add( null ) ;
      }
      return clearLastAccessed() ;
    }

    public Accumulator< C > verify( final boolean mustBeTrue, final String message ) {
      if( ! mustBeTrue ) {
        add( message ) ;
      }
      return clearLastAccessed() ;
    }

    public Accumulator< C > add( final String message ) {
      return add( resolveValuedProperties( inspector.lastAccessed() ), message ) ;
    }

    private ImmutableList< ValuedProperty > resolveValuedProperties(
        final ImmutableList< Configuration.Property< C > > properties
    ) {
      final ImmutableList.Builder< ValuedProperty > builder = ImmutableList.builder() ;
      for( final Configuration.Property< C > property : properties ) {
        final ValuedProperty valuedProperty = inspector.valuedProperty( property ) ;
        if ( valuedProperty == null ) {
          builder.add( new ValuedProperty( property ) ) ;
        } else {
          builder.add( valuedProperty ) ;
        }
      }
      return builder.build() ;
    }

    public Accumulator< C > add(
        final ImmutableList< ValuedProperty > property,
        final String message
    ) {
      builder.add( new Bad(
          property,
          message
      ) ) ;
      return this ;
    }

    Accumulator< C > addInfrigementForNullity(
        final ImmutableList< Configuration.Property< C > > properties,
        final String message
    ) {
      builder.add( new Bad( resolveValuedProperties( properties ), message ) ) ;
      return this ;
    }

  }
}
