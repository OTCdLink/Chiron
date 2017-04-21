package io.github.otcdlink.chiron.toolbox.collection;

import java.util.Comparator;
import java.util.SortedMap;
import java.util.TreeMap;

public final class SortedKeyHolderMap<
    KEY extends KeyHolder.Key< KEY >,
    VALUE extends KeyHolder<KEY >
>
    extends AbstractKeyHolderMap< TreeMap< KEY, VALUE >, KEY, VALUE >
    implements SortedMap< KEY, VALUE >
{
  public SortedKeyHolderMap( final Comparator< KEY > keyComparator ) {
    super( new TreeMap<>( keyComparator ) ) ;
  }


  public VALUE lastValue() {
    return get( lastKey() ) ;
  }

// ===============================================
// More delegated methods, this time for SortedMap
// ===============================================

    @Override
  public Comparator<? super KEY> comparator() {
    return delegate.comparator() ;
  }

  @Override
  public SortedMap< KEY, VALUE > subMap( final KEY fromKey, final KEY toKey ) {
    return delegate.subMap( fromKey, toKey ) ;
  }

  @Override
  public SortedMap< KEY, VALUE > headMap( final KEY toKey ) {
    return delegate.headMap( toKey ) ;
  }

  @Override
  public SortedMap< KEY, VALUE > tailMap( final KEY fromKey ) {
    return delegate.tailMap( fromKey ) ;
  }

  @Override
  public KEY firstKey() {
    return delegate.firstKey() ;
  }

  @Override
  public KEY lastKey() {
    return delegate.lastKey() ;
  }


}
