package com.otcdlink.chiron.toolbox.collection;

import java.util.LinkedHashMap;

/**
 * A mutable {@code Map} that guarantees that the key for each value is a key extracted from
 * the value itself. It retains insertion order.
 *
 * @see ImmutableKeyHolderMap
 * @see KeyHolderMap
 */
public final class LinkedKeyHolderMap<
    KEY extends KeyHolder.Key< KEY >,
    VALUE extends KeyHolder<KEY >
>
    extends AbstractKeyHolderMap< LinkedHashMap< KEY, VALUE >, KEY, VALUE >
{
  public LinkedKeyHolderMap() {
    super( new LinkedHashMap<>() ) ;
  }
}
