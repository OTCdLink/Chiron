package com.otcdlink.chiron.evaluator;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;
import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Java6Assertions.assertThat;

class EvaluatorSubparserTest {

  @Test
  void fieldNameToken() {
    final Parser token = EvaluatorParser.FIELD_NAME_TOKEN.end() ;
    assertThat( token.parse( "Foo.12" ).< String >get() ).isEqualTo( "Foo.12" ) ;
    assertThat( token.parse( " Foo.12  " ).< String >get() ).isEqualTo( "Foo.12" ) ;
    assertThat( token.parse( "Foo.1.2" ).< String >get() ).isEqualTo( "Foo.1.2" ) ;
    assertThat( token.parse( ".Foo.1.2" ).isFailure() ).isTrue() ;
    assertThat( token.parse( "Foo." ).isFailure() ).isTrue() ;
    assertThat( token.parse( "Fo..o" ).isFailure() ).isTrue() ;
  }

  @Test
  void parameterToken() {
    final Parser token = EvaluatorParser.PARAMETER_TOKEN.end() ;
    final Result result = token.parse( " 'whatever' " ) ;
    assertThat( result.< String >get() ).isEqualTo( "whatever" ) ;
  }

  @Test
  void nullParameterToken() {
    final Parser token = EvaluatorParser.PARAMETER_TOKEN.end() ;
    final Result result = token.parse( " {null} " ) ;
    assertThat( result.< String >get() ).isEqualTo( "{null}" ) ;
  }

  @Test
  void escapeQuoteInParameterToken() {
    final Parser token = EvaluatorParser.PARAMETER_TOKEN.end() ;
    final Result result = token.parse( "'{quote}'" ) ;
    assertThat( result.< String >get() ).isEqualTo( "{quote}" ) ;
  }

  @Test
  void escapeAccoladesInParameterToken() {
    final Parser token = EvaluatorParser.PARAMETER_TOKEN.end() ;
    final Result result = token.parse( "'{oa}{ca}'" ) ;
    assertThat( result.< String >get() ).isEqualTo( "{oa}{ca}" ) ;
  }


  @Test
  void singleFieldEvaluatorWithMapping() {
    final Parser token = evaluatorParser.singleFieldEvaluator().end() ;
    final Result result = token.parse( " someText ~= 'whatever'  " ) ;
    LOGGER.info( "Result: " + result ) ;
    final Evaluator evaluator = result.get() ;
    assertThat( evaluator.asString() ).isEqualTo( "someText ~= 'whatever'" ) ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( EvaluatorSubparserTest.class ) ;

  private static final ImmutableSet< EvaluatorFixture.MyEntitiyField< ?, ?, ? > > QUERY_FIELDS_SET =
      EvaluatorFixture.MyEntitiyField.SET ;

  private EvaluatorParser< EvaluatorFixture.MyEntity, EvaluatorFixture.MyEntitiyField< ?, ?, ? > >
  evaluatorParser = new EvaluatorParser<>( QUERY_FIELDS_SET, Operator.Contextualizer.NULL ) ;


}