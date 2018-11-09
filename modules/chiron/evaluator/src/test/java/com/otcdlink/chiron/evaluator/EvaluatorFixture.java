package com.otcdlink.chiron.evaluator;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;

import java.util.function.Function;
import java.util.regex.Pattern;

public final class EvaluatorFixture {

  static final MyEntity ENTITY_0_1 = new MyEntity( new DateTime( 0 ), "Foo", 1 ) ;
  static final MyEntity ENTITY_1_2 = new MyEntity( new DateTime( 1 ), "Foo", 2 ) ;

  private EvaluatorFixture() { }

  @SuppressWarnings( { "unused", "WeakerAccess" } )
  public static final class MyEntitiyField<
      OPERATOR extends Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
      OPERATOR_CONTEXT,
      PARAMETER,
      VALUE
  > extends AutoQueryField< MyEntity, MyContext, OPERATOR, OPERATOR_CONTEXT, PARAMETER, VALUE > {

    MyEntitiyField(
        final OperatorCare< OPERATOR, OPERATOR_CONTEXT, PARAMETER, VALUE > operatorCare,
        final Function< MyContext, OPERATOR_CONTEXT > evaluatorContextExtractor,
        final Function< MyEntity, VALUE > valueExtractor
    ) {
      super( operatorCare, evaluatorContextExtractor, valueExtractor ) ;
    }

    MyEntitiyField(
        final OperatorCare< OPERATOR, OPERATOR_CONTEXT, PARAMETER, VALUE > operatorCare,
        final Function< MyEntity, VALUE > valueExtractor
    ) {
      super( operatorCare, anyEntityContext -> null, valueExtractor ) ;
    }

    public static final MyEntitiyField<
        QueryOperators.DateTimeOperator,
        DateTime,
        DateTime,
        DateTime
    > SOME_DATE = new MyEntitiyField<>(
        QueryOperators.DateTimeOperator.CARE,
        myEntityContext -> myEntityContext == null ? null : myEntityContext.now,
        myEntity -> myEntity.someDate
    ) ;

    public static final MyEntitiyField< QueryOperators.TextOperator, Void, Pattern, String >
        SOME_TEXT = new MyEntitiyField<>(
            QueryOperators.TextOperator.CARE,
            myEntity -> myEntity.someString
        )
    ;

    public static final MyEntitiyField< QueryOperators.StringOperator, Void, String, String >
        SOME_STRING = new MyEntitiyField<>(
            QueryOperators.StringOperator.CARE,
            myEntity -> myEntity.someString
        )
    ;

    public static final MyEntitiyField< QueryOperators.IntegerOperator, Void, Integer, Integer >
        SOME_INT = new MyEntitiyField<>(
            QueryOperators.IntegerOperator.CARE,
            myEntity -> myEntity.someInt
        )
    ;

    public static final ImmutableMap< String, EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? > > MAP =
        valueMapQuiet( EvaluatorFixture.MyEntitiyField.class ) ;

    public static final ImmutableSet< EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? > > SET =
        ImmutableSet.copyOf( MAP.values() ) ;

    public static Evaluator<
            MyEntity,
            MyContext,
            MyEntitiyField< ?, ?, ?, ? >
        > empty( final MyContext myContext ) {
      return Evaluator.empty( myContext, SET ) ;
    }

  }

  public static class MyContext {
    public final DateTime now ;

    public MyContext( final DateTime now ) {
      this.now = now ;
    }
  }

  public static class MyEntity {
    public final DateTime someDate ;
    final String someString ;
    final int someInt ;

    MyEntity( final DateTime someDate, final String someString, final int someInt ) {
      this.someDate = someDate ;
      this.someString = someString ;
      this.someInt = someInt ;
    }
  }
}
