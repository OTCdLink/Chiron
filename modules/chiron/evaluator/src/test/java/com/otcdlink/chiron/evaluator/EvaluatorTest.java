package com.otcdlink.chiron.evaluator;

import org.joda.time.DateTime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluatorTest {

  @Test
  @Disabled( "Shows non-compilable code commented out" )
  void syntaxDemo() {
    // Would not compile:
    // empty.field(
    //     EvaluatorFixture2.MyEntitiyField.SOME_DATE,
    //     QueryOperators.DateTimeOperator.STRICTLY_GREATER_THAN,
    //     ""
    // ) ;

    // Would not compile:
    // empty.field(
    //     EvaluatorFixture2.MyEntitiyField.SOME_STRING,
    //     QueryOperators.DateTimeOperator.EQUAL_TO,
    //     ""
    // ) ;
  }

  @Test
  void not() {
    final Evaluator<
            EvaluatorFixture.MyEntity,
            EvaluatorFixture.MyContext,
            EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
        > negated = empty
        .field(
            EvaluatorFixture.MyEntitiyField.SOME_INT,
            QueryOperators.IntegerOperator.EQUAL_TO,
            1
        ).negate()
    ;
    printToLogger( negated ) ;
    assertThat( negated.evaluate( EvaluatorFixture.ENTITY_0_1 ) ).isFalse() ;
  }

  @Test
  void evaluatorToString() {
    final Evaluator<
            EvaluatorFixture.MyEntity,
            EvaluatorFixture.MyContext,
            EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
        > evaluator =
        empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_DATE,
            QueryOperators.DateTimeOperator.STRICTLY_GREATER_THAN,
            new DateTime( 999_999_999_999L )
        ).and(
            empty.field(
                EvaluatorFixture.MyEntitiyField.SOME_INT,
                QueryOperators.IntegerOperator.EQUAL_TO,
                22
            )
        )
        .or( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            QueryOperators.TextOperator.MATCHES,
            Pattern.compile( "F?o" )
        ) )
    ;
    LOGGER.info( "toString: \n" + evaluator ) ;
    assertThat( evaluator.toString() ).isEqualTo(
        "Evaluator{OR( AND( someDate > '2001-09-09_01:46:39:999', someInt == '22' ), " +
            "someText ~= 'F?o' )}"
    ) ;
  }

  @Test
  void parameterEscaping() {
    final Evaluator<
            EvaluatorFixture.MyEntity,
            EvaluatorFixture.MyContext,
            EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
        > evaluator =
        empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            QueryOperators.TextOperator.MATCHES,
            null
        ).or( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            QueryOperators.TextOperator.MATCHES,
            Pattern.compile( "\\p{Lower}" )
        ) )
    ;
    LOGGER.info( "toString: \n" + evaluator ) ;
    assertThat( evaluator.toString() ).isEqualTo(
        "Evaluator{OR( someText ~= {null}, someText ~= '\\p{oa}Lower{ca}' )}"
    ) ;
  }

  @Test
  void empty() {
    printToLogger( empty ) ;
    assertThat( empty.evaluate( EvaluatorFixture.ENTITY_0_1 ) ).isTrue() ;
    assertThat( empty.evaluate( null ) ).isTrue() ;
  }

  @Test
  void or() {
    final Evaluator<
            EvaluatorFixture.MyEntity,
            EvaluatorFixture.MyContext,
            EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
        > orEvaluator = empty
        .or( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_DATE,
            QueryOperators.DateTimeOperator.STRICTLY_GREATER_THAN,
            new DateTime( -1 )
        ) )
        .or( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            QueryOperators.TextOperator.MATCHES,
            Pattern.compile( "F?o" )
        ) )
    ;
    printToLogger( orEvaluator ) ;
    assertThat( orEvaluator.evaluate( EvaluatorFixture.ENTITY_0_1 ) ).isTrue() ;
  }

  @Test
  void and() {
    final Evaluator<
            EvaluatorFixture.MyEntity,
            EvaluatorFixture.MyContext,
            EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
        > andEvaluator = empty
        .field(
            EvaluatorFixture.MyEntitiyField.SOME_INT,
            QueryOperators.IntegerOperator.EQUAL_TO,
            1
        )
        .and( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            QueryOperators.TextOperator.MATCHES,
            Pattern.compile( ".*" )
        ) )
    ;
    printToLogger( andEvaluator ) ;
    assertThat( andEvaluator.evaluate( EvaluatorFixture.ENTITY_0_1 ) ).isTrue() ;
    assertThat( andEvaluator.evaluate( EvaluatorFixture.ENTITY_1_2 ) ).isFalse() ;
    assertThat( andEvaluator.negate().evaluate( EvaluatorFixture.ENTITY_1_2 ) ).isTrue() ;
  }

  @Test
  void nullParameter() {
    final Evaluator<
        EvaluatorFixture.MyEntity,
        EvaluatorFixture.MyContext,
        EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
    > orEvaluator = empty
        .field(
            EvaluatorFixture.MyEntitiyField.SOME_DATE,
            QueryOperators.DateTimeOperator.EQUAL_TO,
            null  // Treated as smaller than any non-null value by the Comparator in use.
        )
    ;
    printToLogger( orEvaluator ) ;
    assertThat( orEvaluator.evaluate( EvaluatorFixture.ENTITY_1_2 ) ).isFalse() ;
  }

  @Test
  void replaceField() {
    final EvaluatorFixture.MyEntity myEntity = EvaluatorFixture.ENTITY_1_2 ;
    assertThat( myEntity.someString ).describedAs( "Test consistency" ).isEqualTo( "Foo" ) ;

    final Evaluator<
        EvaluatorFixture.MyEntity,
        EvaluatorFixture.MyContext,
        EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
    > orEvaluator = empty
        .field(
            EvaluatorFixture.MyEntitiyField.SOME_STRING,
            QueryOperators.StringOperator.EQUAL_TO,
            "Fo"
        ).and( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_DATE,
            QueryOperators.DateTimeOperator.EQUAL_TO,
            myEntity.someDate
        ) )
    ;
    LOGGER.info( "Created: " + orEvaluator + ", will not match." ) ;
    assertThat( orEvaluator.evaluate( myEntity ) ).isFalse() ;

    final Evaluator<
        EvaluatorFixture.MyEntity,
        EvaluatorFixture.MyContext,
        EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
    > replaced = orEvaluator.replaceFields( ( evaluator, myEntitiyField, operator, parameter ) -> {
      if( myEntitiyField == EvaluatorFixture.MyEntitiyField.SOME_STRING ) {
        return evaluator.newEmpty().field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            QueryOperators.TextOperator.MATCHES,
            Pattern.compile( parameter + "*" )
        ) ;
      } else {
        return evaluator ;
      }
    } ) ;
    LOGGER.info( "After replacement: " + replaced + ", will match." ) ;

    assertThat( replaced.evaluate( myEntity ) ).isTrue() ;
  }

  /**
   * The effect of a {@code null} value with no {@link Evaluator#entityContext} can be seen
   * in {@link #nullParameter()}.
   */
  @Test
  void contextualize() {
    final DateTime now = EvaluatorFixture.ENTITY_1_2.someDate ;

    final Evaluator<
            EvaluatorFixture.MyEntity,
            EvaluatorFixture.MyContext,
            EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
        > orEvaluator = empty( new EvaluatorFixture.MyContext( now ) )
    .field(
        EvaluatorFixture.MyEntitiyField.SOME_DATE,
        QueryOperators.DateTimeOperator.EQUAL_TO,
        null  // Becomes magic, will resolve to 'now'.
    ) ;
    printToLogger( orEvaluator ) ;
    assertThat( orEvaluator.evaluate( EvaluatorFixture.ENTITY_1_2 ) ).isTrue() ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( EvaluatorTest.class ) ;

  /**
   * Just to save some characters in test methods without resorting to a static import.
   */
  private final Evaluator<
        EvaluatorFixture.MyEntity,
        EvaluatorFixture.MyContext,
        EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
    > empty = empty( null ) ;

  /**
   * Just to save some characters in test methods without resorting to a static import.
   */
  public static Evaluator<
        EvaluatorFixture.MyEntity,
        EvaluatorFixture.MyContext,
        EvaluatorFixture.MyEntitiyField< ?, ?, ?, ? >
    > empty( final EvaluatorFixture.MyContext myContext ) {
    return EvaluatorFixture.MyEntitiyField.empty( myContext ) ;
  }

  private static void printToLogger( final Evaluator evaluator ) {
    LOGGER.info( "Created:\n" + evaluator.asString( EvaluatorPrinter.Setup.MULTILINE ) ) ;
  }


}