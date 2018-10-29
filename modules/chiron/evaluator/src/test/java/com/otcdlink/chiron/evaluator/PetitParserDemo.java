package com.otcdlink.chiron.evaluator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.SettableParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

import static org.petitparser.parser.primitive.CharacterParser.digit;
import static org.petitparser.parser.primitive.CharacterParser.letter;
import static org.petitparser.parser.primitive.CharacterParser.of;

/**
 * Samples from https://github.com/petitparser/java-petitparser
 */
@Disabled
class PetitParserDemo {

  @Test
  void sample1fromDocumentation() {
    final Parser id = letter().seq( letter().or( digit() ).star() ) ;
    final Result id1 = id.parse( "yeah" ) ;
    final Result id2 = id.parse( "f12" ) ;
    final String message = "" + id1.get();
    // ['y', ['e', 'a', 'h']]
    LOGGER.info( message ) ;
    // ['f', ['1', '2']]
    LOGGER.info( "" + id2.get() ) ;
  }

  @Test
  void sample2fromDocumentation() {
    final Parser number = digit()
        .plus()
        .flatten()
        .trim()
        .map( ( Function< String, Integer > ) Integer::parseInt )
    ;

    final SettableParser term = SettableParser.undefined() ;
    final SettableParser prod = SettableParser.undefined() ;
    final SettableParser prim = SettableParser.undefined() ;

    term.set(
        prod
        .seq( of( '+' ).trim() )
        .seq( term )
        .map( ( List< Integer > values ) -> values.get( 0 ) + values.get( 2 ) )
        .or( prod )
    ) ;
    prod.set(
        prim
        .seq( of( '*' ).trim() )
        .seq( prod )
        .map( ( List< Integer > values ) -> values.get( 0 ) * values.get( 2 ) )
        .or( prim )
    ) ;
    prim.set(
        ( of( '(' ).trim().seq( term ).seq( of( ')' ).trim() ) )
        .map( ( List< Integer > values) -> values.get( 1 ) )
        .or( number )
    ) ;
    final Parser start = term.end() ;

    LOGGER.info( "" + start.parse( "1 + 2 * 3" ).get() ) ;       // 7
    LOGGER.info( "" + start.parse( "( 1 + 2 ) * 3 " ).get() ) ;  // 9
    LOGGER.info( "" + start.parse( "(2 * 3) + 5" ).get() ) ;

  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( PetitParserDemo.class ) ;


}