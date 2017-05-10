package io.github.otcdlink.chiron.upend.http.content.caching;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.MoreExecutors;
import io.github.otcdlink.chiron.upend.http.content.StaticContent;
import io.netty.buffer.ByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utilities for creating {@link StaticContentCache}s, that can share a {@code ConcurrentMap}
 * for common served resources.
 * <p>
 * When sharing, consistency rules apply. If the key-value parts resolve differently between
 * {@link StaticContentCache}s, this is detected and creation fails. We can achieve this only
 * when passed {@link ImmutableMap}s which can list all the cache content in advance.
 */
public final class StaticContentCacheFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger( StaticContentCacheFactory.class ) ;

  private final ConcurrentMap< String, BytebufContent > bytebufMap =
      new ConcurrentHashMap<>() ;



// =======
// Sharing
// =======

  public StaticContentCache sharedCache(
      final ImmutableMap< String, String > mimeTypeForExtension,
      final ImmutableMap< String, ByteSource > resourceMap
  ) {
    return sharedCache( mimeTypeForExtension, resourceMap, ByteBufAllocator.DEFAULT ) ;
  }

  public StaticContentCache sharedCache(
      final ImmutableMap< String, String > mimeTypeForExtension,
      final ImmutableMap< String, ByteSource > resourceMap,
      final ByteBufAllocator byteBufAllocator
  ) {
    final CacheDeclaration cacheDeclaration =
        new CacheDeclaration( mimeTypeForExtension, resourceMap ) ;
    verify( cacheDeclaration ) ;
    declarationList.add( cacheDeclaration ) ;
    return new StaticContentCache( bytebufMap, cacheDeclaration.asResolver(), byteBufAllocator ) ;
  }

  /**
   * Flushes the {@code Map} shared between {@link StaticContentCache} instances created by
   * {@link #sharedCache(ImmutableMap, ImmutableMap, ByteBufAllocator)}.
   */
  public void flushShare() {
    bytebufMap.clear() ;
  }

// ======================
// Static factory methods
// ======================


  public static StaticContentCache newCacheWithPreload(
      final ImmutableMap< String, String > mimeTypeForExtension,
      final ImmutableMap< String, ByteSource > resourceMap
  ) {
    return newCache( mimeTypeForExtension, resourceMap,
        ByteBufAllocator.DEFAULT, StaticContentPreloading.SEQUENTIAL, null ) ;
  }

  public static StaticContentCache newCache(
      final ImmutableMap< String, String > mimeTypeForExtension,
      final ImmutableMap< String, ByteSource > resourceMap
  ) {
    return newCache( mimeTypeForExtension, resourceMap,
        ByteBufAllocator.DEFAULT, StaticContentPreloading.LAZY, null ) ;
  }

  public static StaticContentCache newCache(
      final ImmutableMap< String, String > mimeTypeForExtension,
      final ImmutableMap< String, ByteSource > resourceMap,
      final Executor executor
  ) {
    return newCache( mimeTypeForExtension, resourceMap,
        ByteBufAllocator.DEFAULT, StaticContentPreloading.EXECUTOR_NOWAIT , executor ) ;
  }

  @SuppressWarnings( "unused" )
  public static StaticContentCache newCache(
      final StaticContentResolver< StaticContent.Streamed > resolver
  ) {
    return newCache( resolver, ByteBufAllocator.DEFAULT ) ;
  }

  @SuppressWarnings( "unused" )
  public static StaticContentCache newCache(
      final StaticContentResolver< StaticContent.Streamed > resolver,
      final ByteBufAllocator byteBufAllocator
  ) {
    return new StaticContentCache( new ConcurrentHashMap<>(), resolver, byteBufAllocator ) ;
  }

  /**
   * Should not be public because it doesn't check what could be in {@link #bytebufMap},
   * use {@link #sharedCache(ImmutableMap, ImmutableMap, ByteBufAllocator)} for {@code Map} sharing.
   */
  private static StaticContentCache newCache(
      final ImmutableMap< String, String > mimeTypeForExtension,
      final ImmutableMap< String, ByteSource > resourceMap,
      final ByteBufAllocator byteBufAllocator,
      final StaticContentPreloading staticContentPreloading,
      final Executor executor
  ) {
    if( staticContentPreloading.usesExecutor ) {
      checkNotNull( executor, "Can't be null along with " + staticContentPreloading ) ;
    } else {
      checkArgument( executor == null, "Must be null along with " + staticContentPreloading ) ;
    }

    final long start = System.currentTimeMillis() ;

    final StaticContentCache staticContentCache = new StaticContentCache(
        new ConcurrentHashMap<>(),
        asResolver( mimeTypeForExtension, resourceMap ),
        byteBufAllocator
    ) ;

    if( staticContentPreloading != StaticContentPreloading.LAZY ) {
      final Executor actualExecutor = staticContentPreloading.usesExecutor ?
          executor : MoreExecutors.directExecutor() ;
      final Semaphore allDoneSemaphore = staticContentPreloading.wait ? new Semaphore( 0 ) : null ;
      for( final String resourceName : resourceMap.keySet() ) {
        actualExecutor.execute( () -> {
          try {
            staticContentCache.staticContent( resourceName ) ;
          } finally {
            if( staticContentPreloading.wait ) {
              allDoneSemaphore.release() ;
            }
          }
        } ) ;
      }
      if( staticContentPreloading.wait ) {
        allDoneSemaphore.acquireUninterruptibly( resourceMap.size() ) ;
        LOGGER.debug( "Loaded " + resourceMap.size() + " " + StaticContent.class.getSimpleName() +
            " object" + ( ! resourceMap.isEmpty() ? "s" : "" ) +
            " in " + ( System.currentTimeMillis() - start ) + " ms."
        ) ;
      }
    }

    return staticContentCache ;
  }


// ==========
// More stuff
// ==========

  private final List< CacheDeclaration > declarationList = new ArrayList<>() ;

  private static class CacheDeclaration {
    public final ImmutableMap< String, String > mimeTypeForExtension ;
    public final ImmutableMap< String, ByteSource > resourceMap ;

    private CacheDeclaration(
        final ImmutableMap< String, String > mimeTypeForExtension,
        final ImmutableMap< String, ByteSource > resourceMap
    ) {
      this.mimeTypeForExtension = checkNotNull( mimeTypeForExtension ) ;
      this.resourceMap = checkNotNull( resourceMap ) ;
    }

    public StaticContentResolver< StaticContent.Streamed > asResolver() {
      return StaticContentCacheFactory.asResolver( mimeTypeForExtension, resourceMap ) ;
    }
  }


  private void verify( final CacheDeclaration adding ) {
    for( final CacheDeclaration previous : declarationList ) {
      for( final Map.Entry< String, String > previousMimeTypeEntry :
          previous.mimeTypeForExtension.entrySet()
      ) {
        for( final Map.Entry< String, String > newMimeTypeEntry :
            adding.mimeTypeForExtension.entrySet()
        ) {
          if( newMimeTypeEntry.getKey().equals( previousMimeTypeEntry.getKey() ) ) {
            if( ! newMimeTypeEntry.getValue().equals( previousMimeTypeEntry.getValue() ) ) {
              throw new IllegalArgumentException(
                  "Incompatible MIME types, trying to add " + string( newMimeTypeEntry ) +
                  " which clashes with " + previous.mimeTypeForExtension
              ) ;
            }
          }
        }
      }
      for( final Map.Entry< String, ByteSource > previousResourceMapEntry :
          previous.resourceMap.entrySet()
      ) {
        for( final Map.Entry< String, ByteSource > newResourceMapEntry :
            adding.resourceMap.entrySet()
        ) {
          if( newResourceMapEntry.getKey().equals( previousResourceMapEntry.getKey() ) ) {
            if( newResourceMapEntry.getValue() != previousResourceMapEntry.getValue() ) {
              throw new IllegalArgumentException(
                  "Incompatible resource values, trying to add " + string( newResourceMapEntry ) +
                  " which clashes with " + previous.resourceMap
              ) ;
            }
          }
        }
      }
    }
  }

  private static String string( final Map.Entry< ?, ? > mapEntry ) {
    return "(" + mapEntry.getKey() + "->" + mapEntry.getValue() + ")" ;
  }


  /**
   * Create a {@link StaticContentResolver} from a handy {@code Map}-based definition.
   * There is no sense to make this method {@code public} because we can achieve more things
   * by using a {@code Map} for describing resource.
   *
   * @param mimeTypeForExtension a {@code Map} with file extensions (without the dot)
   *     as keys, MIME type as value.
   * @param resourceMap a {@code Map} with resource name as key, and a mean to get
   *     its bytes as value (for not loading everything eagerly).
   */
  static StaticContentResolver< StaticContent.Streamed > asResolver(
      final ImmutableMap< String, String > mimeTypeForExtension,
      final ImmutableMap< String, ByteSource > resourceMap
  ) {
    checkArgument( ! mimeTypeForExtension.isEmpty() ) ;
    checkArgument( ! resourceMap.isEmpty() ) ;
    final Map< String, StaticContent.Streamed> staticContentMap =
        new HashMap<>( resourceMap.size() ) ;
    for( final Map.Entry< String, ByteSource > resourceEntry : resourceMap.entrySet() ) {
      final String resourceName = resourceEntry.getKey() ;
      final int lastDot = resourceName.lastIndexOf( '.' ) ;
      if( lastDot > 0 ) {
        final String extension = resourceName.substring( lastDot + 1 ) ;
        final String mimeType = mimeTypeForExtension.get( extension ) ;
        if( mimeType == null ) {
          throw new IllegalArgumentException(
              "No MIME type for extension '" + extension + "' in " + mimeTypeForExtension ) ;
        } else {
          staticContentMap.put( resourceName,
              new StaticContent.Streamed(  resourceName, resourceEntry.getValue(), mimeType ) ) ;
        }
      } else {
        throw new IllegalArgumentException(
            "Missing dot character in resource name '" + resourceName + "'" ) ;
      }
    }
    return resourceName -> {
      final StaticContent.Streamed staticContent = staticContentMap.get( resourceName ) ;
      if( staticContent != null ) {
        LOGGER.debug( "Fully loaded " + staticContent + "." ) ;
      }
      return staticContent ;
    } ;
  }

}
