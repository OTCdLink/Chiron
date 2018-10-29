package com.otcdlink.chiron.evaluator;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.collect.ImmutableBiMap;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import com.otcdlink.chiron.wire.XmlEscaping;

import java.io.IOException;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stateless printer for {@link Evaluator}.
 */
public final class EvaluatorPrinter {

  private final Setup printSetup ;

  public EvaluatorPrinter( final Setup setup ) {
    this.printSetup = checkNotNull( setup ) ;
  }

  public void print( final Evaluator evaluator, final Appendable appendable ) throws IOException {
    print( evaluator, 0, true, appendable ) ;
  }

  private void print(
      final Evaluator evaluator,
      final int depth,
      final boolean lastInCollection,
      final Appendable appendable
  ) throws IOException {
    switch( evaluator.kind ) {

      case EMPTY :
        return ;

      case FIELD :
        Evaluator.Just< ?, ?, ?, ?, ? > forField = ( Evaluator.Just ) evaluator ;
        printSetup.indent( depth, appendable ) ;
        printSetup.prettyName( forField.queryfield, appendable ) ;
        appendable.append( ' ' ) ;
        appendable.append( forField.operator.symbol() ) ;
        final Converter stringConverter = forField.queryfield
            .parameterConverterFromString.reverse() ;
        final String parameterAsString = ( String ) stringConverter.convert( forField.parameter ) ;
        if( parameterAsString == null ) {
          appendable.append( ' ' ) ;
          appendable.append( XmlEscaping.MAGIC_NULL ) ;
        } else {
          appendable.append( " '" ) ;
          ESCAPER.transform( parameterAsString, appendable ) ;
          appendable.append( '\'' ) ;
        }
        printSetup.collectionItemSeparator( lastInCollection, appendable ) ;
        printSetup.endOfLine( appendable ) ;
        break ;

      case AND :
      case OR :
      case NOT :
        printSetup.indent( depth, appendable ) ;
        appendable.append( evaluator.kind.name() ) ;
        printSetup.openParenthesis( appendable ) ;
        printSetup.endOfLine( appendable ) ;
        final Evaluator.Combinator< ?, ? > forCombinator = ( Evaluator.Combinator ) evaluator ;
        final int lastIndex = forCombinator.children.size() - 1 ;
        for( int i = 0 ; i <= lastIndex ; i ++ ) {
          final Evaluator child = forCombinator.children.get( i ) ;
          print( child, depth + 1, i == lastIndex, appendable ) ;
        }
        printSetup.indent( depth, appendable ) ;
        printSetup.closeParenthesis( appendable ) ;
        printSetup.collectionItemSeparator( lastInCollection, appendable ) ;
        printSetup.endOfLine( appendable ) ;
        break ;

      default :
        throw new IllegalArgumentException( "Unsupported: " + evaluator.kind ) ;
    }
  }

  /**
   * Formatting hints for {@link Evaluator#print(Setup, Appendable)}.
   */
  public static final class Setup {
    private final String indent ;
    public final Function< QueryField, String > queryFieldNameExtractor ;
    private final String openingParenthesis ;
    private final String closingParenthesis ;
    private final String collectionItemSeparator ;
    private final String lineBreak ;

    public Setup(
        final String indent,
        final Function< QueryField, String > queryFieldNameExtractor,
        final String openingParenthesis,
        final String closingParenthesis,
        final String collectionItemSeparator,
        final String lineBreak
    ) {
      this.indent = checkNotNull( indent ) ;
      this.queryFieldNameExtractor = checkNotNull( queryFieldNameExtractor ) ;
      this.openingParenthesis = checkNotNull( openingParenthesis ) ;
      this.closingParenthesis = checkNotNull( closingParenthesis ) ;
      this.collectionItemSeparator = checkNotNull( collectionItemSeparator ) ;
      this.lineBreak = checkNotNull( lineBreak ) ;
    }

    void indent( final int depth, final Appendable appendable ) throws IOException {
      for( int i = 0 ; i < depth ; i ++ ) {
        appendable.append( indent ) ;
      }
    }

    void prettyName( final QueryField queryField, final Appendable appendable ) throws IOException {
      appendable.append( queryFieldNameExtractor.apply( queryField ) ) ;
    }

    void openParenthesis( final Appendable appendable ) throws IOException {
      appendable.append( openingParenthesis ) ;
    }

    void closeParenthesis( final Appendable appendable ) throws IOException {
      appendable.append( closingParenthesis ) ;
    }

    void collectionItemSeparator(
        final boolean last,
        final Appendable appendable
    ) throws IOException {
      if( ! last ) {
        appendable.append( collectionItemSeparator ) ;
      }
    }

    void endOfLine( final Appendable appendable ) throws IOException {
      appendable.append( lineBreak ) ;
    }

    public static final Setup MULTILINE = new Setup(
        "  ",
        EvaluatorPrinter::defaultFieldNameEmbellisher,
        "(", ")",
        ", ",
        LineBreak.DEFAULT.asString
    ) ;

    public static final Setup SINGLE_LINE = new Setup(
        "", EvaluatorPrinter::defaultFieldNameEmbellisher, "( ", " )", ", ", "" ) ;

  }

  static String defaultFieldNameEmbellisher( final QueryField queryField ) {
    final String withDots = queryField.name().replace( "__", "." ) ;
    return CaseFormat.UPPER_UNDERSCORE.to( CaseFormat.LOWER_CAMEL, withDots ) ;
  }


  private static final ImmutableBiMap< Character, String > ESCAPE_TABLE =
      ImmutableBiMap.< Character, String >builder()
          .put( XmlEscaping.OPENING_ACCOLADE, "oa" )
          .put( XmlEscaping.CLOSING_ACCOLADE, "ca" )
          .put( '\'', "quote" )
          .build()
      ;

  public static final XmlEscaping.Transformer ESCAPER = new XmlEscaping.SimpleEscaper(
      XmlEscaping.OPENING_ACCOLADE, XmlEscaping.CLOSING_ACCOLADE, ESCAPE_TABLE ) ;

  public static final XmlEscaping.Transformer UNESCAPER = new XmlEscaping.SimpleUnescaper(
      XmlEscaping.OPENING_ACCOLADE, XmlEscaping.CLOSING_ACCOLADE, ESCAPE_TABLE ) ;


}
