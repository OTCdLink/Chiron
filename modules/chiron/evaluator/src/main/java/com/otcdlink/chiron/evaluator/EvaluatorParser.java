package com.otcdlink.chiron.evaluator;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.wire.XmlEscaping;
import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.SettableParser;
import org.petitparser.parser.primitive.CharacterParser;
import org.petitparser.parser.primitive.StringParser;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Creates an {@link Evaluator} from its stringified form, printed with {@link EvaluatorPrinter}.
 */
public final class EvaluatorParser<
    ENTITY,
    ENTITY_CONTEXT,
    QUERYFIELD extends QueryField< ENTITY, ENTITY_CONTEXT, ?, ?, ?, ? >
> {

  private final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > emptyEvaluator ;
  private final ImmutableBiMap< String, QUERYFIELD > queryFieldMap ;

  public EvaluatorParser(
      final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > emptyEvaluator
  ) {
    this( emptyEvaluator, EvaluatorPrinter.Setup.MULTILINE ) ;
  }

  public EvaluatorParser(
      final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > emptyEvaluator,
      final EvaluatorPrinter.Setup evaluatorPrinterSetup
  ) {
    checkArgument( emptyEvaluator.kind == Evaluator.Kind.EMPTY ) ;
    this.emptyEvaluator = emptyEvaluator ;
    queryFieldMap = QueryField.nameMap(
        emptyEvaluator.queryFields,
        evaluatorPrinterSetup.queryFieldNameExtractor
    ) ;
  }

  /** TODO: use definitions in {@link EvaluatorPrinter.Setup}. */
  private static final Parser COMMA = CharacterParser.of( ',' ) ;

  /** TODO: use definitions in {@link EvaluatorPrinter.Setup}. */
  private static final Parser LEFT_PARENTHESIS = CharacterParser.of( '(' ) ;

  /** TODO: use definitions in {@link EvaluatorPrinter.Setup}. */
  private static final Parser RIGHT_PARENTHESIS = CharacterParser.of( ')' ) ;

  static final Parser FIELD_NAME_TOKEN ;
  static {
    final Parser pureCharacter = CharacterParser.of(
        EvaluatorParser::isAsciiLetterOrDigit,
        "Character in [0-9a-zA-Z]"
    ) ;
    final Parser dot = CharacterParser.of( '.' ) ;
    FIELD_NAME_TOKEN = pureCharacter.seq(
        dot.optional().seq( pureCharacter ).plus().star()
    ).flatten().trim() ;
  }

  static final Parser PARAMETER_TOKEN ;
  static {
    final Parser singleQuoteParser = CharacterParser.of( '\'' ) ;
    final Parser magicNullParser = StringParser.of( XmlEscaping.MAGIC_NULL ) ;
    final Parser parameterValueParser = CharacterParser
        .any().starLazy( singleQuoteParser ).flatten() ;
    PARAMETER_TOKEN = magicNullParser
        .or( singleQuoteParser.seq( parameterValueParser, singleQuoteParser ) )
        .trim()
//        .map( ( Function< List< Object >, String> ) objects -> ( String ) objects.get( 1 ) )
        .map( object -> {
          if( object instanceof List ) {
            final String escapedString = ( String ) ( ( List ) object ).get( 1 ) ;
            return escapedString ;
          } else if( XmlEscaping.MAGIC_NULL.equals( object ) ) {
            return object ;
          } else {
            throw new ParseRuntimeException( "Unsupported: " + object ) ;
          }
        } )
    ;
  }

  private static String unescape( final String escapedString ) {
    if( XmlEscaping.MAGIC_NULL.equals( escapedString ) ) {
      return null ;
    }
    final StringBuilder stringBuilder = new StringBuilder() ;
    try {
      EvaluatorPrinter.UNESCAPER.transform( escapedString, stringBuilder ) ;
    } catch( IOException e ) {
      throw new ParseRuntimeException( "Failed to unescape '" + escapedString + "'", e ) ;
    }
    final String unescaped = stringBuilder.toString() ;
    return unescaped ;
  }


  private static final Parser COMBINATOR_TOKEN ;
  static {
    COMBINATOR_TOKEN = newOringParser(
        Evaluator.Kind.COMBINATING.stream()
            .map( Enum::name )
            .collect( ImmutableSet.toImmutableSet() )
    ).map( ( Function< String, Evaluator.Kind > ) Evaluator.Kind::valueOf ) ;
  }

  private Parser operatorToken() {
    final Set< String > operatorSymbols = new LinkedHashSet<>() ;
    final Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > typedEvaluator = this.emptyEvaluator ;
    for( final QueryField< ENTITY, ENTITY_CONTEXT, ?, ?, ?, ? > queryField :
        typedEvaluator.queryFields
    ) {
      operatorSymbols.addAll( queryField.operatorSymbolMap().keySet() ) ;
    }
    return newOringParser( operatorSymbols ) ;
  }

  private static Parser newOringParser( final Collection< String > tokens ) {
    final Iterator< String > iterator = tokens.iterator() ;
    checkState( iterator.hasNext(), "No tokens" ) ;
    Parser parser = StringParser.of( iterator.next() ) ;
    while( iterator.hasNext() ) {
      parser = parser.or( StringParser.of( iterator.next() ) ) ;
    }
    return parser.flatten().trim() ;
  }

  Parser singleFieldEvaluator( ) {
    return FIELD_NAME_TOKEN
        .seq( operatorToken(), PARAMETER_TOKEN )
        .trim()
        .map( ( Function< List< String >, Evaluator> ) tokens -> {
          final String fieldName = tokens.get( 0 ) ;
          final String operatorAsString = tokens.get( 1 ) ;
          final String escapedParameterAsString = tokens.get( 2 ) ;
          final String unescapedParameterAsString = unescape( escapedParameterAsString ) ;
          final QUERYFIELD queryField = queryFieldMap.get( fieldName ) ;
          if( queryField == null ) {
            throw new ParseRuntimeException(
                "Undeclared '" + fieldName + "' in " + queryFieldMap.keySet() ) ;
          }
          final Object parameter = queryField.parameterConverter().convert(
              unescapedParameterAsString ) ;
          final Operator operator = queryField.operatorSymbolMap().get( operatorAsString ) ;
          if( operator == null ) {
            throw new ParseRuntimeException( "Undeclared '" + operatorAsString + "' in " +
                queryField.operatorSymbolMap().keySet() ) ;
          }
          return ( ( Evaluator ) emptyEvaluator ).field( queryField, operator, parameter ) ;
        } )
    ;
  }

  private Parser fieldEvaluatorSequence() {
    return singleFieldEvaluator()
        .separatedBy( COMMA )
        .map( sanitize )
    ;
  }

  private Parser topParser() {
    final Parser fieldEvaluatorSequence = fieldEvaluatorSequence() ;
    final SettableParser topParser = SettableParser.undefined() ;
    final SettableParser combinatorParser = SettableParser.undefined() ;

    combinatorParser.set(
        COMBINATOR_TOKEN.seq(
            LEFT_PARENTHESIS,
            topParser.separatedBy( COMMA ).map( sanitize ),
            RIGHT_PARENTHESIS
        ).trim().map( sanitize ).optional()
    ) ;

    topParser.set( fieldEvaluatorSequence.or( combinatorParser ) ) ;
    return topParser.trim().map( sanitize ).end() ;
  }


  @SuppressWarnings( "unused" )
  public Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > parse( final String text )
      throws ParseException
  {
    try {
      final Result result = topParser().parse( text ) ;
      return toEvaluator( result ) ;
    } catch( Exception e ) {
      throw new ParseException( "Failed to parse \"" + text + "\n", e ) ;
    }
  }

  private Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > toEvaluator( final Result result ) {
    final List< Object > parsed = result.get() ;
    if( parsed == null || parsed.isEmpty() ) {
      return emptyEvaluator ;
    } else {
      return ( Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD > ) parsed.get( 0 ) ;
    }
  }


// =========
// Utilities
// =========

  /**
   * Avoids declaring the exact type in {@link Parser#map(Function)} call places.
   */
  private final Function< List< Object >, Object > sanitize = this::sanitize ;

  private Object sanitize( List< Object > tokens ) {
    if( tokens == null ) {
      return Collections.emptyList() ;
    } else if( tokens.size() == 1 && tokens.get( 0 ) instanceof List ) {
      return tokens.get( 0 ) ;
    } else {
      return retainEvaluatorObjects( tokens ) ;
    }
  }

  private List< Object > retainEvaluatorObjects( final List< Object > tokens ) {
    final List< Object > retained = tokens.stream()
        .filter( o -> o instanceof Evaluator.Kind || o instanceof Evaluator || o instanceof List )
        .collect( Collectors.toList() )
    ;
    if( retained.size() == 2 && retained.get( 0 ) instanceof Evaluator.Kind ) {
      final Evaluator.Kind combinatorKind = ( Evaluator.Kind ) retained.get( 0 ) ;
      final List< Object > subevaluatorList = ( List< Object > ) retained.get( 1 ) ;
      final ImmutableList.Builder<Evaluator< ENTITY, ENTITY_CONTEXT, QUERYFIELD >> builder =
          ImmutableList.builder() ;
      for( final Object subevaluatorOrList : subevaluatorList ) {
        if( subevaluatorOrList instanceof List ) {
          final List wrapper = ( List ) subevaluatorOrList ;
          checkArgument( wrapper.size() == 1 ) ;
          builder.add( ( Evaluator ) wrapper.get( 0 ) ) ;
        } else {
          builder.add( ( Evaluator ) subevaluatorOrList ) ;
        }
      }
      final Evaluator combined = emptyEvaluator.combine( combinatorKind, builder.build() ) ;
      return ImmutableList.of( combined ) ;
    }
    return retained ;
  }

  private static boolean isAsciiLetterOrDigit( char c ) {
    return
        ( c >= '0' && c <= '9' ) ||
        ( c >= 'a' && c <= 'z' ) ||
        ( c >= 'A' && c <= 'Z' )
    ;
  }

  public static class ParseRuntimeException extends RuntimeException {
    public ParseRuntimeException( final String message ) {
      super( message ) ;
    }

    public ParseRuntimeException( String message, Throwable cause ) {
      super( message, cause );
    }
  }

  public static class ParseException extends Exception {
    public ParseException( final String message ) {
      super( message ) ;
    }

    public ParseException( final String message, final Throwable cause ) {
      super( message, cause ) ;
    }
  }
}
