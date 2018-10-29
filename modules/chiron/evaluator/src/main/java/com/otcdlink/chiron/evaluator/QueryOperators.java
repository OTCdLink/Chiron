package com.otcdlink.chiron.evaluator;

import com.google.common.base.Converter;
import com.google.common.primitives.Ints;
import com.otcdlink.chiron.toolbox.ComparatorTools;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.function.IntPredicate;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Pre-built instances of {@link Operator}.
 */
public interface QueryOperators {

  enum ScalarOrdering {

    STRICTLY_GREATER_THAN( ">", i -> i > 0 ),
    GREATER_THAN_OR_EQUAL_TO( ">=", i -> i >= 0 ),
    EQUAL_TO( "==", i -> i == 0 ),
    STRICTLY_LOWER_THAN( "<", i -> i < 0 ),
    LOWER_THAN_OR_EQUAL_TO( "<=", i -> i <= 0 ),
    ;
    public final String symbol ;
    public final IntPredicate comparisonResultEvaluator ;

    ScalarOrdering( final String symbol, final IntPredicate comparisonResultEvaluator ) {
      this.symbol = symbol ;
      this.comparisonResultEvaluator = comparisonResultEvaluator ;
    }

  }

  @SuppressWarnings( "unused" )
  enum IntegerOperator implements Operator< Integer, Integer > {
    STRICTLY_GREATER_THAN( ScalarOrdering.STRICTLY_GREATER_THAN ),
    GREATER_THAN_OR_EQUAL_TO( ScalarOrdering.GREATER_THAN_OR_EQUAL_TO ),
    EQUAL_TO( ScalarOrdering.EQUAL_TO ),
    STRICTLY_LOWER_THAN( ScalarOrdering.STRICTLY_LOWER_THAN ),
    LOWER_THAN_OR_EQUAL_TO( ScalarOrdering.LOWER_THAN_OR_EQUAL_TO ),
    ;

    private final ScalarOrdering scalarOrdering ;

    IntegerOperator( final ScalarOrdering scalarOrdering ) {
      this.scalarOrdering = scalarOrdering ;
    }

    @Override
    public String symbol() {
      return scalarOrdering.symbol ;
    }

    @Override
    public boolean apply( final Integer parameter, final Integer value ) {
      final int comparison = ComparatorTools.INTEGER_COMPARATOR.compare( value, parameter ) ;
      return scalarOrdering.comparisonResultEvaluator.test( comparison ) ;
    }

    public static final QueryField.Conversion< Integer, IntegerOperator, Integer > CONVERSION =
        new QueryField.Conversion<>(
            QueryField.Conversion.fromEnum( IntegerOperator.class ),
            Ints.stringConverter()
        )
    ;

  }

  @SuppressWarnings( "unused" )
  enum DateTimeOperator implements Operator< DateTime, DateTime > {
    STRICTLY_GREATER_THAN( ScalarOrdering.STRICTLY_GREATER_THAN ),
    GREATER_THAN_OR_EQUAL_TO( ScalarOrdering.GREATER_THAN_OR_EQUAL_TO ),
    EQUAL_TO( ScalarOrdering.EQUAL_TO ),
    STRICTLY_LOWER_THAN( ScalarOrdering.STRICTLY_LOWER_THAN ),
    LOWER_THAN_OR_EQUAL_TO( ScalarOrdering.LOWER_THAN_OR_EQUAL_TO ),
    ;

    private final ScalarOrdering scalarOrdering ;

    DateTimeOperator( final ScalarOrdering scalarOrdering ) {
      this.scalarOrdering = scalarOrdering ;
    }

    @Override
    public String symbol() {
      return scalarOrdering.symbol ;
    }

    @Override
    public boolean apply( final DateTime parameter, final DateTime value ) {
      final int comparison = ComparatorTools.DATETIME_COMPARATOR.compare( value, parameter ) ;
      return scalarOrdering.comparisonResultEvaluator.test( comparison ) ;
    }


    private static class ContextualizedDelegator extends Operator.Delegator< DateTime, DateTime > {
      private final DateTime now ;

      public ContextualizedDelegator(
          final Operator< DateTime, DateTime > delegate,
          final DateTime now
      ) {
        super( delegate ) ;
        this.now = checkNotNull( now ) ;
      }

      @Override
      public boolean apply( final DateTime parameter, final DateTime value ) {
        return delegate.apply( parameter == null ? now : parameter, value ) ;
      }
    }

    public static class NullAsMagicValue implements Contextualizer {
      private final DateTime meaning ;

      public NullAsMagicValue( final DateTime meaning ) {
        this.meaning = checkNotNull( meaning ) ;
      }

      @Override
      public Operator contextualize( final Operator operator ) {
        if( operator instanceof DateTimeOperator ) {
          return new ContextualizedDelegator( operator, meaning ) ;
        } else {
          return operator ;
        }
      }
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormat.forPattern( "YYYY-MM-dd_HH:mm:ss:SSS" ).withZoneUTC() ;

    private static final Converter< String, DateTime > DATE_TIME_CONVERTER = Converter.from(
        DATE_TIME_FORMATTER::parseDateTime,
        DATE_TIME_FORMATTER::print
    ) ;

    public static final QueryField.Conversion< DateTime, DateTimeOperator, DateTime > CONVERSION =
        new QueryField.Conversion<>(
            QueryField.Conversion.fromEnum( DateTimeOperator.class ),
            DATE_TIME_CONVERTER
        )
    ;

  }

  enum TextOperator implements Operator< Pattern, String > {
    MATCHES_PATTERN {
      @Override
      public boolean apply( final Pattern pattern, final String s ) {
        if( pattern == null ) {
          return s == null ;
        } else {
          return pattern.matcher( s ).matches() ;
        }
      }
      @Override
      public String symbol() {
        return "~=" ;
      }
    }
    ,
    ;

    private static final Converter< String, Pattern > PATTERN_CONVERTER = Converter.from(
        Pattern::compile,
        pattern -> pattern != null ? pattern.pattern() : null
    ) ;

    public static final QueryField.Conversion< Pattern, TextOperator, String > CONVERSION =
        new QueryField.Conversion<>(
            QueryField.Conversion.fromEnum( TextOperator.class ),
            PATTERN_CONVERTER
        )
    ;

  }

  enum StringOperator implements Operator< String, String > {
    EQUAL_TO {
      @Override
      public boolean apply( final String parameter, final String value ) {
        if( parameter == null ) {
          return value == null ;
        } else {
          return parameter.equals( value ) ;
        }
      }
      @Override
      public String symbol() {
        return "==" ;
      }
    }
    ,
    ;
    public static final QueryField.Conversion< String, StringOperator, String > CONVERSION =
        new QueryField.Conversion<>(
            QueryField.Conversion.fromEnum( StringOperator.class ),
            Converter.identity()
        )
    ;

  }
}
