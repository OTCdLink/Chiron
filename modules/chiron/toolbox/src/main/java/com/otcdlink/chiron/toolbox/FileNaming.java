package com.otcdlink.chiron.toolbox;


import com.google.common.base.Converter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Transforms a {@link VALUE} into a file name in a well-defined format, and back.
 * This class does conceptually the same thing as a {@link Converter}, adding various
 * enhancements for {@code null} support, and better messages in exceptions.
 * The {@link FileWithValue} also ties a {@code File} and its {@link VALUE} together
 * when the two are needed, with consistency guarantee.
 * The {@link Sandwiched} and {@link Timestamped} subclasses are useful for widespread formatting
 * patterns.
 *
 * <h1>Name parsing</h1>
 * <p>
 * This class only parses {@code File.getName()} and doesn't care about directories.
 * But this could make sense (if directories have a meaning to determine the {@link VALUE}).
 * Such a feature would require a base directory to resolve names from. With such base
 * directory being null, we would fall back to a directoryless {@link VALUE} resolution.
 */
public abstract class FileNaming< VALUE > {

  /**
   * Does the real conversion stuff. May throw any {@link RuntimeException} but
   * {@link RuntimeParseException} is preferred when parsing (because it conveys better message).
   * The resulting {@code String} is supposed to be a valid file name (no checks on that).
   */
  private final Converter< String, VALUE > converter ;

  /**
   * Validates the {@code String} to parse, so parsing can fail fast without throwing
   * a costly exception.
   * However {@link #FileNaming(Converter)} (with no {@code Predicate}) creates a
   * {@link ConverterBasedValidator} which delegates to {@link #converter} and therefore
   * can trigger an exception-throwing parsing for validation.
   *
   * @see #valid(String)
   */
  private final Predicate< String > stringValidator ;

  private final boolean dedicatedValidator ;

  /**
   * Clumsy constructor deriving the {@link #stringValidator} from the {@link #converter}
   * (so it's less performant when validation fails, because of the exception thrown
   * by {@link ConverterBasedValidator}.
   *
   * @see #stringValidator
   */
  protected FileNaming( final Converter< String, VALUE > converter ) {
    this(
        converter,
        new ConverterBasedValidator( converter )
    ) ;
  }

  protected FileNaming(
      final Converter< String, VALUE > converter,
      final Predicate< String > stringValidator
  ) {
	  this.converter = checkNotNull( converter ) ;
	  this.stringValidator = checkNotNull( stringValidator ) ;
	  this.dedicatedValidator = ! ( stringValidator instanceof FileNaming.ConverterBasedValidator ) ;
  }

  /**
   * Tighter contract than {@link Converter}, with nullity check and checked {@code Exception}.
   */
  public final VALUE parse( final String string ) throws ParseException {
    checkNotNull( string ) ;
    try {
      if( dedicatedValidator && ! stringValidator.test( string ) ) {
        throw new ParseException( "Validation failed for '" + string + "'" ) ;
      }
      return converter.convert( string ) ;
    } catch( final RuntimeParseException e ) {
      throw new ParseException( e.getMessage() ) ;
    } catch( final Exception e ) {
      throw new ParseException( string, e ) ;
    }
  }

  public final VALUE parseOrNull( final String string ) {
    checkNotNull( string ) ;
    if( ! dedicatedValidator || stringValidator.test( string ) ) {
      try {
        return converter.convert( string ) ;
      } catch( final Exception e ) {
        return null ;
      }
    } else {
      return null ;
    }
  }


  /**
   * Tighter contract than {@link Converter}, with nullity check.
   */
  public final String render( final VALUE value ) {
    return converter.reverse().convert( checkNotNull( value ) ) ;
  }

  public final boolean valid( final String filename ) {
    checkNotNull( filename ) ;
    return stringValidator.test( filename ) ;
  }

  public final VALUE fromFile( final File file ) throws ParseException {
    return parse( file.getName() ) ;
  }

  public final VALUE fromFileOrNull( final File file ) {
    return parseOrNull( file.getName() ) ;
  }

  public final FileWithValue< VALUE > withValueFromFile( final File file ) throws ParseException {
    return new FileWithValue<>( file, parse( file.getName() ) ) ;
  }

  public final FileWithValue< VALUE > withValueFromDirectory(
      final File parentDirectory,
      final VALUE value
  ) {
    final String filename = render( value ) ;
    return new FileWithValue<>( new File( parentDirectory, filename ), value ) ;
  }

  /**
   * Extracts the {@link FileWithValue} of a {@code File} that has given suffix, excluding the
   * suffix from the conversion.
   */
  public final FileWithValue< VALUE > withValueFromFileOrNull( final File file ) {
    final String rawFileName = file.getName() ;
    final VALUE value = parseOrNull( rawFileName ) ;
    if( value == null ) {
      return null ;
    } else {
      return new FileWithValue<>( file, value ) ;
    }
  }

  public final File toFile( final File parentDirectory, final VALUE value ) {
    return new File( parentDirectory, render( value ) ) ;
  }

  public final FileNaming< VALUE > suffix( final String suffix ) {
    return new Enhancer<>(
        "",
        this.converter,
        this.stringValidator,
        suffix
    ) ;
  }



  /**
   * Sorts (with no side-effect) using given {@code Comparator}.
   * Unparseable values are silently ignored.
   */
  public final ImmutableList< File > sortParseableOnly(
      final Iterable< File > files,
      final Comparator< VALUE > comparator
  ) {
    try {
      return sort( files, comparator, false ) ;
    } catch( ParseException e ) {
      throw new Error( "Should not happen", e ) ;
    }
  }

  /**
   * Sorts (with no side-effect) using given {@code Comparator}.
   *
   * @throws ParseException if {@code failOnUnparseableValue} is {@code true} and there is
   *     an unparseable value.
   */
  public final ImmutableList< File > sort(
      final Iterable< File > files,
      final Comparator< VALUE > comparator,
      final boolean failOnUnparseableValue
  ) throws ParseException {

    final List< FileWithValue< VALUE > > slots = new ArrayList<>() ;
    for( final File file : files ) {
      try {
        slots.add( new FileWithValue<>( file, parse( file.getName() ) ) ) ;
      } catch( ParseException e ) {
        if( failOnUnparseableValue ) {
          throw e ;
        }
      }
    }

    slots.sort( new ComparatorTools.WithNull< FileWithValue< VALUE > >() {
      @Override
      protected int compareNoNulls(
          final FileWithValue< VALUE > first,
          final FileWithValue< VALUE > second
      ) {
        return comparator.compare( first.value, second.value ) ;
      }
    } ) ;

    return ImmutableList.copyOf( Iterables.transform( slots, FileWithValue::file ) ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + "{" +
        ExternalizablePattern.asPatternIfSupported( converter ) + "}" ;
  }


  /**
   * Keeps a {@link #file} and a {@link #value} together, guaranteeing they are consistent.
   */
  public static final class FileWithValue< VALUE > {
    private final File file ;
    private final VALUE value ;

    /**
     * Private visibility guarantees that only {@link FileNaming#withValueFromFile(File)}
     * could create it.
     */
    private FileWithValue( File file, VALUE value ) {
      this.file = checkNotNull( file ) ;
      this.value = checkNotNull( value ) ;
    }

    public File file() {
      return file ;
    }

    public VALUE value() {
      return value ;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "{" + file.getAbsolutePath() + ";" + value + "}" ;
    }

    @Override
    public boolean equals( Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final FileWithValue< ? > that = ( FileWithValue< ? > ) other ;
      return Objects.equals( file, that.file ) && Objects.equals( value, that.value ) ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( file, value ) ;
    }
  }

  public static final class ParseException extends Exception {
    private ParseException( final String message ) {
      super( message ) ;
    }
    private ParseException( final String unparseable, final Exception cause ) {
      super( "Can't parse " + "'" + unparseable + "'", cause ) ;
    }
  }

  /**
   * Thrown by implementations of {@link Converter}, which only throws unchecked exceptions.
   */
  public static final class RuntimeParseException extends RuntimeException {
    public RuntimeParseException( final String unparseable, final String message ) {
      super(
          "Can't parse " + "'" + unparseable + "'" +
          ( Strings.isNullOrEmpty( message ) ? "" : ": " + message )
      ) ;
    }
  }


  /**
   * Parses and renders a {@link VALUE} between a prefix and a suffix.
   */
  public static abstract class Sandwiched< VALUE > extends FileNaming< VALUE > {
    protected final String prefix ;
    protected final String suffix ;
    private final Converter< String, VALUE > nakedValueConverter ;

    public Sandwiched(
        final String prefix,
        final Converter< String, VALUE > nakedValueConverter,
        final Predicate< String > stringValidator,
        final String suffix
    ) {
      super(
          decorate( prefix, nakedValueConverter, suffix ),
          decorate( prefix, stringValidator, suffix )
      ) ;
      this.prefix = checkNotNull( prefix ) ;
      this.suffix = checkNotNull( suffix ) ;
      this.nakedValueConverter = checkNotNull( nakedValueConverter ) ;
    }

    public Sandwiched(
        final String prefix,
        final Converter< String, VALUE > nakedValueConverter,
        final String suffix
    ) {
      super( decorate( prefix, nakedValueConverter, suffix ) ) ;
      this.prefix = checkNotNull( prefix ) ;
      this.suffix = checkNotNull( suffix ) ;
      this.nakedValueConverter = checkNotNull( nakedValueConverter ) ;
    }

    private static < VALUE > Converter< String, VALUE > decorate(
        final String prefix,
        final Converter< String, VALUE > nakedValueConverter,
        final String suffix
    ) {
      return new Converter< String, VALUE >() {
        @Override
        protected String doBackward( @Nonnull final VALUE value ) {
          return prefix + nakedValueConverter.reverse().convert( value ) + suffix ;
        }

        @Override
        protected VALUE doForward( @Nonnull final String string ) {
          final String actualPrefix = string.substring( 0, prefix.length() ) ;
          if( ! prefix.equals( actualPrefix ) ) {
            throw new RuntimeParseException( string,
                "Bad prefix '" + actualPrefix + "' (expecting '" + prefix + "')" ) ;
          }
          final String actualSuffix = string.substring( string.length() - suffix.length() ) ;
          if( ! suffix.equals( actualSuffix ) ) {
            throw new RuntimeParseException( string,
                "Bad suffix '" + actualSuffix + "' (expecting '" + suffix + "')" ) ;
          }
          final String nakedValueAsString =
              string.substring( prefix.length(), string.length() - suffix.length() ) ;
          return nakedValueConverter.convert( nakedValueAsString ) ;
        }

      } ;
    }

    private static Predicate< String > decorate(
        final String prefix,
        final Predicate< String > nakedStringValidator,
        final String suffix
    ) {
      return string -> {
        if( ! string.startsWith( prefix ) || ! string.endsWith( suffix ) ) {
          return false ;
        }
        final String naked = string.substring(
            prefix.length(), string.length() - suffix.length() ) ;
        return nakedStringValidator.test( naked ) ;
      };
    }

    @Override
    public String toString() {
      return
          ToStringTools.getNiceClassName( this ) + "{" +
          "'" + prefix + "';" +
          ExternalizablePattern.asPatternIfSupported( nakedValueConverter ) + ";" +
          "'" + suffix + "'" +
          "}"
      ;
    }

  }

  /**
   * Avoids creating anonymous class in methods like {@link FileNaming#suffix(String)}.
   */
  private static final class Enhancer< VALUE > extends Sandwiched< VALUE > {

    /**
     * TODO: detect nested {@link Enhancer}s and flatten them.
     */
    public Enhancer(
        final String prefix,
        final Converter< String, VALUE > nakedValueConverter,
        final Predicate< String > stringValidator,
        final String suffix
    ) {
      super( prefix, nakedValueConverter, stringValidator, suffix ) ;
    }
  }

  /**
   * Parses and renders a {@link DateTime} between a prefix and a suffix.
   */
  public static abstract class Timestamped extends Sandwiched< DateTime > {

    /**
     * Creates a new instance with an UTC-enabled {@link DateTimeFormat}.
     *
     * @param dateTimeFormatterPatternUtcWillBeAdded passing a {@code String} makes nicer
     *     {@code toString}.
     */
    public Timestamped(
        final String prefix,
        final String dateTimeFormatterPatternUtcWillBeAdded,
        final String suffix
    ) {
      super(
          prefix,
          asConverter(
              DateTimeFormat.forPattern( dateTimeFormatterPatternUtcWillBeAdded ).withZoneUTC(),
              dateTimeFormatterPatternUtcWillBeAdded + "[withUTC]"
          ),
          suffix
      ) ;
    }

    public Timestamped(
        final String prefix,
        final DateTimeFormatter dateTimeFormatter,
        final String suffix
    ) {
      super(
          prefix,
          asConverter( dateTimeFormatter, null ),
          suffix
      ) ;
    }

    private static Converter< String, DateTime > asConverter(
        final DateTimeFormatter dateTimeFormatter,
        final String pattern
    ) {
      return new ConverterWithPattern< String, DateTime >( pattern ) {
        @Override
        protected DateTime doForward( @Nonnull final String string ) {
          try {
            return dateTimeFormatter.parseDateTime( string ).withZone( DateTimeZone.UTC ) ;
          } catch( Exception e ) {
            throw new RuntimeParseException(
                string, "Timestamp does not match pattern '" + pattern + "'" ) ;
          }
        }

        @Override
        protected String doBackward( @Nonnull final DateTime dateTime ) {
          return dateTimeFormatter.print( dateTime ) ;
        }
      } ;
    }

  }

  public static class StringValidator implements Predicate< String > {
    private final Pattern pattern ;

    public StringValidator( final Pattern pattern ) {
      this.pattern = checkNotNull( pattern ) ;
    }

    @Override
    public boolean test( final String s ) {
      return pattern.matcher( s ).matches() ;
    }
  }

  protected interface ExternalizablePattern {
    @Nullable String pattern() ;

    static Object asPatternIfSupported( final Object object ) {
      return object instanceof FileNaming.ExternalizablePattern ?
          ( ( ExternalizablePattern ) object ).pattern() : object ;
    }
  }

  protected static abstract class ConverterWithPattern< A, B >
      extends Converter< A, B >
      implements ExternalizablePattern
  {
    @Nullable
    private final String pattern ;

    protected ConverterWithPattern( @Nullable final String pattern ) {
      this.pattern = pattern ;
    }

    @Override
    public String pattern() {
      return pattern ;
    }
  }

  /**
   * The default {@link FileNaming} to use for timestamped files.
   * Milliseconds not proven useful yet.
   * {@link DateTime} objects are converted to UTC during rendering;
   * parsed {@link DateTime} objects are expected to be in UTC timezone.
   */
  public static class TimestampedCompactUtc extends Timestamped {

    public static final String PATTERN = "YYYY-MM-dd_HH.mm.ss" ;

    public TimestampedCompactUtc(
        final String prefix,
        final String suffix
    ) {
      super( prefix, PATTERN, suffix ) ;
    }
  }

  private static class ConverterBasedValidator implements Predicate< String > {
    private final Converter< String, ? > converter ;

    public ConverterBasedValidator( final Converter< String, ? > converter ) {
      this.converter = checkNotNull( converter ) ;
    }

    @Override
    public boolean test( final String string ) {
      try {
        converter.convert( string ) ;
        return true ;
      } catch( final Exception e ) {
        return false ;
      }
    }
  }

}