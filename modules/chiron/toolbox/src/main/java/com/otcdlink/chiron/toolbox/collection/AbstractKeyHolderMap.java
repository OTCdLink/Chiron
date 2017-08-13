package com.otcdlink.chiron.toolbox.collection;

import com.google.common.base.Joiner;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A mutable {@code Map} that guarantees that the key for each value is a key extracted from
 * the value itself. It retains insertion ordering and prohibits null keys and null values.
 *
 * @see ImmutableKeyHolderMap
 */
abstract class AbstractKeyHolderMap<
    DELEGATE extends Map< KEY, VALUE >,
    KEY extends KeyHolder.Key< KEY >,
    VALUE extends KeyHolder< KEY >
>
    implements Map< KEY, VALUE >, Visitor.Visitable< VALUE >
{
  protected final DELEGATE delegate  ;

  public AbstractKeyHolderMap( final DELEGATE delegate ) {
    this.delegate = checkNotNull( delegate ) ;
  }

  @Override
  public boolean visitAll( final Visitor< VALUE > visitor ) {

    for( final Entry< KEY, VALUE > entry : delegate.entrySet() ) {
      final boolean prematureExit = ! visitor.visit( entry.getValue() ) ;
      if( prematureExit ) {
        return false ;
      }
    }
    return true ;
  }

  @Override
  public VALUE put( final KEY key, final VALUE value ) {
    checkArgument( value.key() == key,
        "Given key '" + key + "' differs from natural key '" + value.key() + "'" ) ;
    checkArgument( ! containsKey( key ), "Already contains '" + key + "'" ) ;
    return delegate.put( key, value ) ;
  }

  public void put( final VALUE value ) {
    put( value.key(), value ) ;
  }

  public VALUE replace( final VALUE value ) {
    final VALUE old = get( value.key() ) ;
    remove( value.key() ) ;
    put( value ) ;
    return old ;
  }

  public void putAll( final VALUE... values ) {
    for( final VALUE value : values ) {
      put( value ) ;
    }
  }

  public void putAll( final Collection< VALUE > values ) {
    values.forEach( this::put ) ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + Joiner.on( ',' ).join( delegate.values() ) + "]" ;
  }

// ==========
// Delegation
// ==========


  @Override
  public int size() {
    return delegate.size() ;
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty() ;
  }

  @Override
  public boolean containsKey( final Object key ) {
    return delegate.containsKey( key ) ;
  }

  @Override
  public boolean containsValue( final Object value ) {
    return delegate.containsValue( value ) ;
  }

  @Override
  public VALUE get( final Object key ) {
    return delegate.get( key ) ;
  }


  @Override
  public VALUE remove( final Object key ) {
    return delegate.remove( key ) ;
  }

  @Override
  public void putAll( final Map< ? extends KEY, ? extends VALUE > otherMap ) {
    delegate.putAll( otherMap ) ;
  }

  @Override
  public void clear() {
    delegate.clear() ;
  }

  @Override
  public Set< KEY > keySet() {
    return delegate.keySet() ;
  }

  @Override
  public Collection< VALUE > values() {
    return delegate.values() ;
  }

  @Override
  public Set< Entry< KEY,VALUE > > entrySet() {
    return delegate.entrySet() ;
  }

  @Override
  public boolean equals( final Object other ) {
    return delegate.equals( other ) ;
  }

  @Override
  public int hashCode() {
    return delegate.hashCode() ;
  }

}
