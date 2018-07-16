package com.otcdlink.chiron.command;

import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.toolbox.StringWrapper;
import com.otcdlink.chiron.toolbox.ToStringTools;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a request for code execution across different execution contexts.
 *
 * <h1>About {@link Description}</h1>
 * We don't need that because we use every method of {@link Description} at a time when
 * {@link Command} is already instantiated.
 *
 * @param <ENDPOINT_SPECIFIC> things to know about execution context.
 * @param <CALLABLE_RECEIVER> the interface defining Upend Logic's duties.
 */
public abstract class Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > {

  public final ENDPOINT_SPECIFIC endpointSpecific ;
  private final boolean persist ;

  /**
   * Caching the result of a rather expensive operation.
   */
  @SuppressWarnings( "OverridableMethodCallDuringObjectConstruction" )
  private final String niceClassName = niceClassName() ;

  /**
   * Subclasses that get instanciated a lot should override this method to return a constant.
   * Subclasses declared as nested classees could use
   * {@link ToStringTools#getNiceClassName(java.lang.Object)}, which takes rather long to execute
   * as shown by profiler.
   */
  protected String niceClassName() {
    return getClass().getSimpleName() ;
  }

  protected Command( final ENDPOINT_SPECIFIC endpointSpecific ) {
    this( endpointSpecific, true ) ;
  }

  protected Command( final ENDPOINT_SPECIFIC endpointSpecific, final boolean persist ) {
    this.endpointSpecific = checkNotNull( endpointSpecific ) ;
    this.persist = persist ;
  }

  public final boolean persist() {
    return persist ;
  }

  public abstract void callReceiver( CALLABLE_RECEIVER callableReceiver ) ;

  /**
   * Encode {@link Command}'s parameter, but not its
   * {@link Command.Description#name() name} nor its
   * {@link #endpointSpecific} part.
   * @param positionalFieldWriter
   */
  public abstract void encodeBody( PositionalFieldWriter positionalFieldWriter ) throws IOException ;

  /**
   * We use a thread-safe {@code Map} only for keeping its internal structure consistent.
   * Because every value will be the same each time we calculate it, we don't care about
   * putting it a couple of times in a context of concurrent access.
   */
  private static final Map< Class< ? extends Command >, Description > descriptionCache =
      new ConcurrentHashMap<>() ;

  public static Description description( final Class< ? extends Command > commandClass ) {
    Description description = descriptionCache.get( commandClass ) ;
    if( description == null ) {
      description = extractDescription( commandClass ) ;
      if( description == null ) {
        throw new Error( "Missing " + Description.class.getSimpleName() + " for " +
            commandClass.getName() ) ;
      }
      descriptionCache.put( commandClass, description ) ;
    }
    return description ;
  }

  private static Description extractDescription( final Class< ? extends Command > commandClass ) {
    return commandClass.getAnnotation( Description.class ) ;
  }

  /**
   * Overridable method, so we can instantiate it from generated code with language level
   * that doesn't support generics (like Javassist's).
   */
  public Description description() {
    return description( getClass() ) ;
  }

  @Override
  public final String toString() {
    return niceClassName + '{' + toStringBody() + '}' ;
  }

  protected String toStringBody() {
    return endpointSpecific.toString() + ';' ;
  }

  @Override
  public boolean equals( final Object other ) {
    return this == other ;
  }

  @Override
  public int hashCode() {
    return endpointSpecific.hashCode() ;
  }

  @Target( ElementType.TYPE )
  @Retention( RetentionPolicy.RUNTIME )
  @Inherited
  @Documented
  public @interface Description {
    /**
     * Used by serialisation.
     */
    String name() ;

    /**
     * If {@code true}, {@code TrackerCurator} will track the {@link Command}.
     */
    boolean tracked() default true ;
  }

  /**
   * When sending an Upward Command, the Downend sets a {@link Tag} as a part of
   * the {@link #endpointSpecific}. The Upend sends downward exactly one {@link Command}
   * with the same {@link Tag} so the Downend knows that originating Upward Command
   * has been fully processed. The Downward Command might indicate a failure.
   *
   * <h1>Naming</h1>
   * "Sticker" would be cooler, and then would become a top-level class.
   */
  public static final class Tag extends StringWrapper< Tag > {

    public Tag( final String value ) {
      super( value ) ;
    }

    public String asString() {
      return wrapped ;
    }

    public static String stringOrNull( final Tag tag ) {
      return tag == null ? null : tag.asString() ;
    }

    public static final Comparator< Tag > COMPARATOR = new WrapperComparator<>() ;
  }

}
