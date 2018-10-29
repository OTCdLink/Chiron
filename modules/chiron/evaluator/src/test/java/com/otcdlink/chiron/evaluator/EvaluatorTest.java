package com.otcdlink.chiron.evaluator;

import com.otcdlink.chiron.evaluator.QueryOperators.DateTimeOperator;
import com.otcdlink.chiron.evaluator.QueryOperators.IntegerOperator;
import com.otcdlink.chiron.evaluator.QueryOperators.TextOperator;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

import static com.otcdlink.chiron.evaluator.Operator.Contextualizer.Composite.newComposite;
import static org.assertj.core.api.Assertions.assertThat;

class EvaluatorTest {

  @Test
  @Disabled( "Shows non-compilable code commented out" )
  void syntaxDemo() {
    // Would not compile:
    // empty.field( EvaluatorFixture.MyEntitiyField.SOME_DATE, DateTimeOperator.STRICTLY_GREATER_THAN, "" ) ;

    // Would not compile:
    // empty.field( EvaluatorFixture.MyEntitiyField.SOME_STRING, DateTimeOperator.STRICTLY_GREATER_THAN, new DateTime() ) ;
  }

  @Test
  void not() {
    final Evaluator<
        EvaluatorFixture.MyEntity,
        EvaluatorFixture.MyEntitiyField< ?, ?, ? >
    > negated = empty
        .field( EvaluatorFixture.MyEntitiyField.SOME_INT, IntegerOperator.EQUAL_TO, 1 ).negate()
    ;
    printToLogger( negated ) ;
    assertThat( negated.evaluate( EvaluatorFixture.ENTITY_0_1 ) ).isFalse() ;
  }

  @Test
  void evaluatorToString() {
    final Evaluator<
        EvaluatorFixture.MyEntity,
        EvaluatorFixture.MyEntitiyField< ?, ?, ? >
    > evaluator =
        empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_DATE,
            DateTimeOperator.STRICTLY_GREATER_THAN,
            new DateTime( 999_999_999_999L )
        ).and(
            empty.field(
                EvaluatorFixture.MyEntitiyField.SOME_INT,
                IntegerOperator.EQUAL_TO,
                22
            )
        )
        .or( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            TextOperator.MATCHES_PATTERN,
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
        EvaluatorFixture.MyEntitiyField< ?, ?, ? >
    > evaluator =
        empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            TextOperator.MATCHES_PATTERN,
            null
        ).or( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            TextOperator.MATCHES_PATTERN,
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
        EvaluatorFixture.MyEntitiyField< ?, ?, ? >
    > orEvaluator = empty
        .or( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_DATE,
            DateTimeOperator.STRICTLY_GREATER_THAN,
            new DateTime( -1 )
        ) )
        .or( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            TextOperator.MATCHES_PATTERN,
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
        EvaluatorFixture.MyEntitiyField< ?, ?, ? >
    > andEvaluator = empty
        .field(
            EvaluatorFixture.MyEntitiyField.SOME_INT,
            IntegerOperator.EQUAL_TO,
            1
        )
        .and( empty.field(
            EvaluatorFixture.MyEntitiyField.SOME_TEXT,
            TextOperator.MATCHES_PATTERN,
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
        EvaluatorFixture.MyEntitiyField< ?, ?, ? >
    > orEvaluator = empty
        .field(
            EvaluatorFixture.MyEntitiyField.SOME_DATE,
            DateTimeOperator.EQUAL_TO,
            null  // Treated as smaller than any non-null value by the Comparator in use.
        )
    ;
    printToLogger( orEvaluator ) ;
    assertThat( orEvaluator.evaluate( EvaluatorFixture.ENTITY_1_2 ) ).isFalse() ;
  }

  /**
   * The effect of a {@code null} value with no {@link Operator.Contextualizer} can be seen
   * in {@link #nullParameter()}.
   */
  @Test
  void contextualize() {
    final DateTime now = EvaluatorFixture.ENTITY_1_2.someDate ;

    final Evaluator<
        EvaluatorFixture.MyEntity,
        EvaluatorFixture.MyEntitiyField< ?, ?, ? >
    > orEvaluator = Evaluator.empty(
        EvaluatorFixture.MyEntitiyField.SET,
        newComposite( new DateTimeOperator.NullAsMagicValue( now ) )
    ).field(
        EvaluatorFixture.MyEntitiyField.SOME_DATE,
        DateTimeOperator.EQUAL_TO,
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
      EvaluatorFixture.MyEntitiyField< ?, ?, ? >
  > empty = EvaluatorFixture.MyEntitiyField.EMPTY ;

  private static void printToLogger( final Evaluator evaluator ) {
    LOGGER.info( "Created:\n" + evaluator.asString( EvaluatorPrinter.Setup.MULTILINE ) ) ;
  }


}