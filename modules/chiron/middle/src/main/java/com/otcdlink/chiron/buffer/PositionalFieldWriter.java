package com.otcdlink.chiron.buffer;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.LocalDate;

import java.math.BigDecimal;

public interface PositionalFieldWriter extends CrudeWriter {

  /**
   * Writes a nullity marker if {@code writeIt} is {@code true}, and nothing otherwise.
   * If {@code writeIt} is {@code false} there must be some valued field immediately after.
   *
   * @see PositionalFieldReader#readNullityMarker()
   */
  boolean writeNullityMarkerMaybe( boolean writeIt ) ;

  /**
   * Useful to force a non-null value in a structure where the first substructure may be null.
   * The nullity of the substructure would then "poison" the containing one by making it appear
   * null. A non-null value resolves this ambiguity.
   */
  void writeExistenceMark() ;

  void writeBigDecimal( final BigDecimal bigDecimal ) ;

  void writeDateTime( final DateTime dateTime ) ;

  void writeLocalDate( final LocalDate localDate ) ;

  void writeDuration( final Duration duration ) ;


    /**
     * Wrecks any delimiter, and requires every character to be ASCII.
     */
  void writeAsciiUnsafe( final CharSequence charSequence ) ;

}
