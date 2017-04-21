package io.github.otcdlink.chiron.middle;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.toolbox.EnumTools;

/**
 * Describes why the server didn't want to process some {@link Command}.
 * This class supports extension through reuse of {@link Kind} symbols (with compatibility
 * checked using {@link EnumTools#checkEnumExtender(Enum[], Enum[])}.
 */
public class TechnicalFailureNotice
    extends CommandFailureNotice< TechnicalFailureNotice.Kind >
{

  public TechnicalFailureNotice( final Kind kind ) {
    super( kind ) ;
  }

  public TechnicalFailureNotice( final Kind kind, final String message ) {
    super( kind, message ) ;
  }

  public enum Kind implements EnumeratedMessageKind {
    SERVER_ERROR( "Server error" ),
    THROTTING_APPLIED( "Exceeding submission rate (Throttling)" )
    ;

    private final String description ;

    Kind( final String description ) {
      this.description = description ;
    }

    @Override
    public String description() {
      return description ;
    }

    public TechnicalFailureNotice appending( final String message ) {
      return new TechnicalFailureNotice( this, this.description() + ": " + message ) ;
    }
    public static TechnicalFailureNotice.Kind safeValueOf( final int ordinal ) {
      return EnumTools.fromOrdinalSafe( values(), ordinal ) ;
    }

  }


}
