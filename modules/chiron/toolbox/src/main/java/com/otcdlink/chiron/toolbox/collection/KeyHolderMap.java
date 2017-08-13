package com.otcdlink.chiron.toolbox.collection;

import java.util.HashMap;

/**
 * A mutable {@code Map} that guarantees that the key for each value is a key extracted from
 * the value itself. It retains insertion ordering and prohibits null keys and null values.
 *
 * @see ImmutableKeyHolderMap
 */
public final class KeyHolderMap<
    KEY extends KeyHolder.Key< KEY >,
    VALUE extends KeyHolder<KEY >
>
    extends AbstractKeyHolderMap< HashMap< KEY, VALUE >, KEY, VALUE >
{
  public KeyHolderMap() {
    super( new HashMap<>() ) ;
  }
}
