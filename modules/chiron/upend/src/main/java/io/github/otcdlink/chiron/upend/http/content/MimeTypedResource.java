package io.github.otcdlink.chiron.upend.http.content;

import com.google.common.base.Strings;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.upend.http.content.caching.StaticContentCache;
import io.github.otcdlink.chiron.upend.http.content.file.StaticFileContentProvider;
import io.github.otcdlink.chiron.upend.http.dispatch.HttpDispatcher;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Base class representing resources as byte sequence to be served over HTTP.
 *
 * <h1>TODO: support client caching</h1>
 * There are plenty of explainations on the Web but Netty's
 * <a href="https://netty.io/4.1/xref/io/netty/example/http/file/HttpStaticFileServerHandler.html">one</a>
 * is a good start.
 * Each served {@link StaticContent} should have a creation date along with its MIME type.
 * Content-serving methods like
 * {@link HttpDispatcher#resourceMatch(StaticContentCache)} or
 * {@link HttpDispatcher#file(StaticFileContentProvider)}
 * should support an option to activate caching (cache deactivation should be the default).
 * By the way, a system time set to the future on a server could cause overaggressive caching
 * (with update misses as a consequence).
 *
 */
public abstract class MimeTypedResource {

  public final String mimeType ;

  /**
   * May be {@code null}, only used in {@link #toString()}.
   */
  private final String resourceName ;

  protected MimeTypedResource( final String resourceName, final String mimeType ) {
    this.resourceName = resourceName ;
    checkArgument( ! Strings.isNullOrEmpty( mimeType ) ) ;
    this.mimeType = mimeType ;
  }

  @Override
  public final String toString() {
    final StringBuilder stringBuilder = new StringBuilder() ;
    stringBuilder.append( ToStringTools.getNiceClassName( this ) ).append( '{' ) ;
    if( resourceName == null ) {
      stringBuilder.append( bodyAsString() ).append( ';' ).append( mimeType ) ;
    } else {
      stringBuilder.append( resourceName ) ;
    }
    stringBuilder.append( '}' ) ;
    return stringBuilder.toString() ;
  }

  protected abstract String bodyAsString() ;
}
