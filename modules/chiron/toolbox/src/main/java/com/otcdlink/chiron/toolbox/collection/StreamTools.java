package com.otcdlink.chiron.toolbox.collection;

import java.util.function.Consumer;
import java.util.stream.Collector;

public class StreamTools {

  private static final Collector NULL_COLLECTOR = Collector.of(
      () -> null,
      ( o, item ) -> { },
      ( any1, any2 ) -> any1,
      any -> null
  ) ;

  /**
   * Returns a {@link Collector} that does nothing, and returns {@code null} as collection result.
   */
  public static < ITEM, COLLECTION > Collector< ITEM, Object, COLLECTION > nullCollector() {
    return ( Collector< ITEM, Object, COLLECTION > ) NULL_COLLECTOR ;
  }

  /**
   * Returns a {@link Collector} that does nothing, and returns {@code null} as collection result.
   * This is a contract violation of the {@link Collector}.
   */
  public static < ITEM, COLLECTION > Collector< ITEM, Object, COLLECTION > consumingCollector(
      final Consumer< ITEM > consumer

  ) {
    return Collector.of(
        () -> null,
        ( o, item ) -> consumer.accept( item ),
        ( any1, any2 ) -> any1,
        any -> null
    ) ;
  }
}
