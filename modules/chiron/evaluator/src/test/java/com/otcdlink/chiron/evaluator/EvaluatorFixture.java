package com.otcdlink.chiron.evaluator;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;

import java.util.regex.Pattern;

public final class EvaluatorFixture {

  static final MyEntity ENTITY_0_1 = new MyEntity( new DateTime( 0 ), "Foo", 1 ) ;
  static final MyEntity ENTITY_1_2 = new MyEntity( new DateTime( 1 ), "Foo", 2 ) ;

  private EvaluatorFixture() { }


  @SuppressWarnings( { "unused", "WeakerAccess" } )
  public static final class MyEntitiyField<
      PARAMETER,
      OPERATOR extends Operator< PARAMETER, VALUE >,
      VALUE
  > extends QueryField< MyEntity, PARAMETER, OPERATOR, VALUE > {

    MyEntitiyField(
        final Extractor<MyEntity, VALUE> extractor,
        final Conversion<PARAMETER, OPERATOR, VALUE> conversion
    ) {
      super( extractor, conversion ) ;
    }

    public static final MyEntitiyField< DateTime, QueryOperators.DateTimeOperator, DateTime >
        SOME_DATE = new MyEntitiyField<>(
            myEntity -> myEntity.someDate,
            QueryOperators.DateTimeOperator.CONVERSION
        )
    ;

    public static final MyEntitiyField< Pattern, QueryOperators.TextOperator, String >
        SOME_TEXT = new MyEntitiyField<>(
            myEntity -> myEntity.someString,
            QueryOperators.TextOperator.CONVERSION
        )
    ;

    public static final MyEntitiyField< String, QueryOperators.StringOperator, String >
        SOME_STRING = new MyEntitiyField<>(
            myEntity -> myEntity.someString,
            QueryOperators.StringOperator.CONVERSION
        )
    ;

    public static final MyEntitiyField< Integer, QueryOperators.IntegerOperator, Integer >
        SOME_INT = new MyEntitiyField<>(
            myEntity -> myEntity.someInt,
            QueryOperators.IntegerOperator.CONVERSION
        )
    ;


    public static final ImmutableMap< String, MyEntitiyField< ?, ?, ? > > MAP =
        valueMapQuiet( MyEntitiyField.class ) ;

    static final ImmutableSet< MyEntitiyField< ?, ?, ? > > SET =
        ImmutableSet.copyOf( MAP.values() ) ;

    static Evaluator< MyEntity, MyEntitiyField< ?, ?, ? > > EMPTY = Evaluator.empty( SET ) ;

  }

  public static class MyEntity {
    final DateTime someDate ;
    final String someString ;
    final int someInt ;

    MyEntity( final DateTime someDate, final String someString, final int someInt ) {
      this.someDate = someDate ;
      this.someString = someString ;
      this.someInt = someInt ;
    }
  }
}
