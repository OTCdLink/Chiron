package com.otcdlink.chiron.evaluator;

import com.google.common.base.Converter;
import com.google.common.primitives.Ints;
import com.otcdlink.chiron.toolbox.ComparatorTools;
import org.joda.time.DateTime;
import org.joda.time.DateTimeComparator;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.function.IntPredicate;
import java.util.regex.Pattern;

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
  enum DateTimeOperator implements Operator< DateTime, DateTime, DateTime > {

    STRICTLY_LOWER_THAN( QueryOperators.ScalarOrdering.STRICTLY_LOWER_THAN ),
    LOWER_THAN_OR_EQUAL_TO( QueryOperators.ScalarOrdering.LOWER_THAN_OR_EQUAL_TO ),
    EQUAL_TO( QueryOperators.ScalarOrdering.EQUAL_TO ),
    GREATER_THAN_OR_EQUAL_TO( QueryOperators.ScalarOrdering.GREATER_THAN_OR_EQUAL_TO ),
    STRICTLY_GREATER_THAN( QueryOperators.ScalarOrdering.STRICTLY_GREATER_THAN ),
    ;

    private final QueryOperators.ScalarOrdering scalarOrdering ;

    DateTimeOperator( final QueryOperators.ScalarOrdering scalarOrdering ) {
      this.scalarOrdering = scalarOrdering ;
    }

    @Override
    public String symbol() {
      return scalarOrdering.symbol ;
    }

    @Override
    public boolean apply(
        final DateTime context,
        final DateTime parameter,
        final DateTime value
    ) {
      final DateTime dateTime = parameter == null ? context : parameter ;
      return scalarOrdering.comparisonResultEvaluator.test(
          DateTimeComparator.getInstance().compare( dateTime, value ) ) ;
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormat.forPattern( "YYYY-MM-dd_HH:mm:ss:SSS" ).withZoneUTC() ;

    public static final Converter< String, DateTime > DATE_TIME_CONVERTER = Converter.from(
        DATE_TIME_FORMATTER::parseDateTime,
        DATE_TIME_FORMATTER::print
    ) ;

    public static final OperatorCare< DateTimeOperator, DateTime, DateTime, DateTime > CARE =
        OperatorCare.newFromEnum(
            DateTimeOperator.class,
            DATE_TIME_CONVERTER
        )
    ;

  }

  enum TextOperator implements Operator< Void, Pattern, String > {

    MATCHES( "~=" ),
    ;

    private final String symbol ;

    TextOperator( final String symbol ) {
      this.symbol = symbol ;
    }

    @Override
    public String symbol() {
      return symbol ;
    }

    @Override
    public boolean apply( final Void context, final Pattern parameter, final String value ) {
      return parameter.matcher( value ).matches() ;
    }

    public static final Converter< String, Pattern > PATTERN_CONVERTER = Converter.from(
        Pattern::compile,
        pattern -> pattern != null ? pattern.pattern() : null
    ) ;


    public static final OperatorCare< TextOperator, Void, Pattern, String > CARE =
        OperatorCare.newFromEnum(
            TextOperator.class,
            QueryOperators.TextOperator.PATTERN_CONVERTER
        )
        ;
  }

  enum StringOperator implements Operator< Void, String, String > {

    EQUAL_TO( "==" ),
    ;

    private final String symbol ;

    StringOperator( final String symbol ) {
      this.symbol = symbol ;
    }

    @Override
    public String symbol() {
      return symbol ;
    }

    @Override
    public boolean apply( final Void context, final String parameter, final String value ) {
      return ComparatorTools.STRING_COMPARATOR.compare( parameter, value ) == 0 ;
    }

    public static final OperatorCare< StringOperator, Void, String, String > CARE =
        OperatorCare.newFromEnum(
            StringOperator.class,
            Converter.identity()
        )
        ;
  }
  enum IntegerOperator implements Operator< Void, Integer, Integer > {

    STRICTLY_LOWER_THAN( QueryOperators.ScalarOrdering.STRICTLY_LOWER_THAN ),
    LOWER_THAN_OR_EQUAL_TO( QueryOperators.ScalarOrdering.LOWER_THAN_OR_EQUAL_TO ),
    EQUAL_TO( QueryOperators.ScalarOrdering.EQUAL_TO ),
    GREATER_THAN_OR_EQUAL_TO( QueryOperators.ScalarOrdering.GREATER_THAN_OR_EQUAL_TO ),
    STRICTLY_GREATER_THAN( QueryOperators.ScalarOrdering.STRICTLY_GREATER_THAN ),
    ;

    private final QueryOperators.ScalarOrdering scalarOrdering ;

    IntegerOperator( final QueryOperators.ScalarOrdering scalarOrdering ) {
      this.scalarOrdering = scalarOrdering ;
    }

    @Override
    public String symbol() {
      return scalarOrdering.symbol ;
    }

    @Override
    public boolean apply( final Void context, final Integer parameter, final Integer value ) {
      return scalarOrdering.comparisonResultEvaluator.test(
          ComparatorTools.INTEGER_COMPARATOR.compare( parameter, value ) ) ;
    }

    public static final OperatorCare<IntegerOperator, Void, Integer, Integer > CARE =
        OperatorCare.newFromEnum(
            IntegerOperator.class,
            Ints.stringConverter()
        )
        ;
  }

}
