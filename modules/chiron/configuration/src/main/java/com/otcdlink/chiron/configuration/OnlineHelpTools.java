package com.otcdlink.chiron.configuration;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.text.TextWrapTools;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public final class OnlineHelpTools {

  private OnlineHelpTools() { }

  private static final int INDENT = 2 ;
  private static final int LINE_LENGTH = 80 ;

  public static String helpAsString( final Configuration.Factory< ? > factory ) {
    return helpAsString( factory, INDENT, LINE_LENGTH ) ;
  }
  public static String helpAsString(
      final Configuration.Factory< ? > factory,
      final int indent,
      final int lineLength
  ) {
    final StringWriter stringWriter = new StringWriter() ;
    writeHelp( stringWriter, factory, indent, lineLength ) ;
    return stringWriter.toString() ;
  }

  public static String errorMessageAndHelpAsString(
      final DeclarationException declarationException
  ) {
    return errorMessageAndHelpAsString( declarationException, INDENT, LINE_LENGTH ) ;
  }

  public static String errorMessageAndHelpAsString(
      final DeclarationException declarationException,
      final int indent,
      final int lineLength
  ) {
    final StringWriter stringWriter = new StringWriter() ;
    try {
      writeErrorMessageAndHelp( stringWriter, declarationException, indent, lineLength ) ;
    } catch ( final IOException e ) {
      throw new RuntimeException( "Can't happen", e ) ;
    }
    return stringWriter.toString() ;
  }

  public static void writeErrorMessageAndHelp(
      final Writer writer,
      final DeclarationException declarationException
  ) throws IOException {
    writeErrorMessageAndHelp( writer, declarationException, INDENT, LINE_LENGTH ) ;
  }

  public static void writeErrorMessageAndHelp(
      final Writer writer,
      final DeclarationException declarationException,
      final int indent,
      final int lineLength
  ) throws IOException {
    TextWrapTools.writeWrapped(
        writer,
        "Could not create a " + Configuration.class.getSimpleName() + " from "
            + ConfigurationTools.getNiceName( declarationException.factory.configurationClass() ),
        0,
        lineLength
    ) ;
    writeExceptionOnly( writer, declarationException, indent, lineLength ) ;
    TextWrapTools.writeWrapped( writer, "\nUsage:", 0, lineLength ) ;
    writeHelp( writer, declarationException.factory, indent, lineLength ) ;
  }

  public static String exceptionAsMultilineString(
      final DeclarationException declarationException
  ) {
    final StringWriter writer = new StringWriter() ;
    try {
      writeExceptionOnly( writer, declarationException, 0, LINE_LENGTH ) ;
    } catch ( IOException e ) {
      throw new RuntimeException( "Should not happen", e ) ;
    }
    return writer.toString() ;
  }

  public static String causesAsMultilineString( final ImmutableList< Validation.Bad > causes ) {
    final StringWriter writer = new StringWriter() ;
    try {
      writeWrapped( writer, causes, 0, Integer.MAX_VALUE ) ;
    } catch ( IOException e ) {
      throw new RuntimeException( "Should not happen", e ) ;
    }
    return writer.toString().replaceAll( "\\n$", "" ) ;
  }

  private static void writeExceptionOnly(
      final Writer writer,
      final DeclarationException declarationException,
      final int indent,
      final int lineLength
  ) throws IOException {
    if( declarationException.causes.isEmpty() ) {
      TextWrapTools.writeWrapped( writer, declarationException.getMessage(), indent, lineLength ) ;
    } else {
      final ImmutableList< Validation.Bad > causes = declarationException.causes ;
      writeWrapped( writer, causes, indent, lineLength ) ;
    }
  }

  private static void writeWrapped(
      final Writer writer,
      final ImmutableList< Validation.Bad > causes,
      final int indent,
      final int lineLength
  ) throws IOException {
    final Map< Configuration.Property, Configuration.Source > valuedPropertiesWithSource
        = new HashMap<>() ;
    for( final Validation.Bad bad : causes ) {
      final StringBuilder lineBuilder = new StringBuilder() ;
      for( final ValuedProperty valuedProperty : bad.properties ) {
        if( valuedProperty.source != Sources.UNDEFINED  ) {
          valuedPropertiesWithSource.put( valuedProperty.property, valuedProperty.source ) ;
        }
        lineBuilder.append( "[ " ) ;
        lineBuilder.append( valuedProperty.property.name() ) ;
        if( valuedProperty.resolvedValue != ValuedProperty.NO_VALUE ) {
          lineBuilder.append( " = " ) ;
          lineBuilder.append( valuedProperty.resolvedValue == ValuedProperty.NULL_VALUE
              ? "null" : valuedProperty.stringValue ) ;
        }
        lineBuilder.append( " ] " ) ;
      }
      lineBuilder.append( bad.message ) ;
      TextWrapTools.writeWrapped( writer, lineBuilder.toString(), indent, lineLength ) ;

    }
    if( ! valuedPropertiesWithSource.isEmpty() ) {
      TextWrapTools.writeWrapped(
          writer,
          "\nSource" + ( valuedPropertiesWithSource.entrySet().size() > 1 ? "s:" : ":" ),
          indent,
          lineLength
      ) ;
      for( final Map.Entry< Configuration.Property, Configuration.Source > entries
          : valuedPropertiesWithSource.entrySet()
          ) {
        TextWrapTools.writeWrapped(
            writer,
            entries.getKey().name() + " <- " + entries.getValue().sourceName(),
            indent * 2,
            lineLength
        ) ;
      }
    }
    writer.flush() ;
  }

  public static void writeHelp(
      final Writer writer,
      final Configuration.Factory< ? > factory
  ) {
    writeHelp( writer, factory, INDENT, LINE_LENGTH ) ;
  }

  public static void writeHelp(
      final Writer writer,
      final Configuration.Factory< ? > factory,
      final int indent,
      final int lineLength
  ) {
    final String leftPadding1 = Strings.repeat( " ", indent ) ;
    final String leftPadding2 = Strings.repeat( " ", indent * 2 ) ;

    try {
      for( final Configuration.Property< ? > property : factory.properties().values() ) {
        writer.append( "\n" ) ;
        writer.append( leftPadding1 ) ;
        writer.append( property.name() ) ;
        writer.append( "\n" ) ;
        final String defaultValueAsString = property.defaultValueAsString() ;
        if( defaultValueAsString != null ) {
          writer.append( leftPadding2 ) ;
          writer.append( "Default value: '" ) ;
          writer.append( defaultValueAsString ) ;
          writer.append( "'\n" ) ;
        }
        if( defaultValueAsString == null && ! property.maybeNull() ) {
          writer.append( leftPadding2 ) ;
          writer.append( "(Requires explicit value)" ) ;
          writer.append( "\n" ) ;
        }
        final String documentation = property.documentation() ;
        if( ! Strings.isNullOrEmpty( documentation ) ) {
          TextWrapTools.writeWrapped( writer, documentation, indent * 2, lineLength ) ;
        }
      }
      writer.flush() ;
    } catch( final IOException e ) {
      throw new RuntimeException( e ) ;
    }
  }


}
