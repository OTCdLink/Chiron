package com.otcdlink.chiron.upend.http.content.caching;

import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;

public final class StaticContentTools {
  private StaticContentTools() { }

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( StaticContentTools.class ) ;

  /**
   * Resolve (but don't load yet) a resource by its name.
   *
   * @param resourceContextClass where to load the resource from.
   * @param resourceName resource name, a relative path appends to the one given by
   *     {@code resourceContextClass}.
   */
  public static ByteSource asByteSource(
      final Class resourceContextClass,
      final String resourceName
  ) {
    return Resources.asByteSource(
        Resources.getResource( resourceContextClass, resourceName ) ) ;
  }


  /**
   * Resolve (but don't load yet) a resource by its name.
   *
   * @param resourceContextClass where to load the resource from.
   * @param resourceName resource name, a relative path appends to the one given by
   *     {@code resourceContextClass}.
   */
  public static CharSource asCharSource(
      final Class resourceContextClass,
      final String resourceName,
      final Charset charset
  ) {
    return Resources.asCharSource(
        Resources.getResource( resourceContextClass, resourceName ), charset ) ;
  }

}
