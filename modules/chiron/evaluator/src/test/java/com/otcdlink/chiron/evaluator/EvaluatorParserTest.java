package com.otcdlink.chiron.evaluator;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluatorParserTest {

  @Test
  void badFieldName() {
    final String asString = "does.not.exist == '1' " ;
    assertThatThrownBy( () -> new EvaluatorParser<>( empty ).parse( asString ) )
        .isInstanceOf( EvaluatorParser.ParseException.class )
        .hasMessageContaining( "does.not.exist" )
    ;
  }

  @Test
  void badOperatordName() {
    final String asString = "someInt ~= '1' " ;
    assertThatThrownBy( () -> new EvaluatorParser<>( empty ).parse( asString ) )
        .isInstanceOf( EvaluatorParser.ParseException.class )
        .hasMessageContaining( "~=" )
    ;
  }

  @ParameterizedTest
  @EnumSource( TestDefinition.class )
  void test( final TestDefinition testDefinition ) throws EvaluatorParser.ParseException {
    LOGGER.info( "Parsing \"" + testDefinition.query + "\" ..." ) ;

    final EvaluatorParser<
        EvaluatorFixture.MyEntity,
        EvaluatorFixture.MyEntitiyField< ?, ?, ? >
    > evaluatorParser = new EvaluatorParser<>( empty ) ;

    final Evaluator<
        EvaluatorFixture.MyEntity,
        EvaluatorFixture.MyEntitiyField< ?, ?, ? >
    >  evaluator1,  evaluator2 ;
    evaluator1 = evaluatorParser.parse( testDefinition.query ) ;
    final String reprinted = evaluator1.asString() ;
    LOGGER.info( "Also reprinted from " + evaluator1 + "." ) ;
    evaluator2 = evaluatorParser.parse( reprinted ) ;

    for( final TestDefinition.EntityAssertion entityAssertion : testDefinition.entityAssertions ) {
      final boolean evaluation1 = evaluator1.evaluate( entityAssertion.myEntity ) ;
      final boolean evaluation2 = evaluator2.evaluate( entityAssertion.myEntity ) ;
      assertThat( evaluation1 )
          .describedAs( "Evaluating " + entityAssertion.myEntity + " with " + evaluator1 )
          .isEqualTo( entityAssertion.evaluationResult )
      ;
      assertThat( evaluation2 )
          .describedAs( "Evaluating " + entityAssertion.myEntity + " with " + evaluator2 +
              " which was parsed from \"" + evaluator1 + "\"" )
          .isEqualTo( entityAssertion.evaluationResult )
      ;
    }
  }

  @SuppressWarnings( "unused" )
  enum TestDefinition {

    EMPTY( " ", $( 0, true ), $( "", true ) ),

    AND( "AND( someText ~= 'x', someInt == '0' )", $( "x", 0, true ), $( "x", 1, false ) ),

    NOT_AND( "NOT( AND( someText ~= 'x', someInt == '0' ) )",
        $( "x", 0, false ), $( "x", 1, true ) ),

    OR_AND_AND(
        "OR( " +
            "AND( someText ~= 'x', someInt == '0' ), " +
            "AND( someText ~= 'y', someInt == '1' ) " +
        ")",
        $( "x", 0, true ), $( "x", 1, false ), $( "y", 1, true ) ),

    NOT( "NOT( someInt == '0' )", $( 0, false ), $( 1, true ) ),

    NAKED( "someInt == '1' ", $( 0, false ), $( 1, true ) ),

    NULL( "someString == {null} ", $( null, true ) ),

    ESCAPED_QUOTE( "someText ~= '{quote}' ", $( "'", true ) ),

    ESCAPED_CURLY_BRACKETS( "someString == '{oa}quote{ca}' ", $( "{quote}", true ) ),

    ;
    public final String query ;
    public final ImmutableList< EntityAssertion > entityAssertions ;

    private static EntityAssertion $( final int i, final boolean result ) {
      return new EntityAssertion( new EvaluatorFixture.MyEntity( null, null, i ), result ) ;
    }
    private static EntityAssertion $( final String s, final boolean result ) {
      return new EntityAssertion( new EvaluatorFixture.MyEntity( null, s, 0 ), result ) ;
    }
    private static EntityAssertion $( final String s, final int i, final boolean result ) {
      return new EntityAssertion( new EvaluatorFixture.MyEntity( null, s, i ), result ) ;
    }

    TestDefinition(
        final String query,
        final EntityAssertion... entityAssertions ) {
      this.query = query ;
      this.entityAssertions = ImmutableList.copyOf( entityAssertions ) ;
    }

    static class EntityAssertion {
      final EvaluatorFixture.MyEntity myEntity ;
      final boolean evaluationResult ;

      public EntityAssertion(
          final EvaluatorFixture.MyEntity myEntity,
          final boolean evaluationResult
      ) {
        this.myEntity = myEntity ;
        this.evaluationResult = evaluationResult ;
      }
    }
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