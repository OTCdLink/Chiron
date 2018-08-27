package com.otcdlink.chiron.wire;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.collection.Autoconstant;
import io.netty.buffer.ByteBuf;

import java.util.Collection;
import java.util.stream.Collector;

import static com.google.common.base.Preconditions.checkNotNull;

@SuppressWarnings( "unused" )
public interface Wire {

  abstract class Autonamed< ITEM > extends Autoconstant< ITEM > {
    private final String customXmlName ;

    protected Autonamed() {
      this( null ) ;
    }

    protected Autonamed( final String customXmlName ) {
      this.customXmlName = customXmlName ;
    }

    public final String xmlName() {
      return customXmlName == null ?
          CaseFormat.UPPER_UNDERSCORE.to( CaseFormat.LOWER_HYPHEN, name() ) :
          customXmlName
      ;
    }

  }

  interface Token {
    String xmlName() ;

    /**
     * Useful with {@link Autoconstant}.
     */
    static < VALUE extends Token > ImmutableMap< String, VALUE > rekeyWithXmlNames(
        final ImmutableCollection< VALUE > values
    ) {
      final ImmutableMap.Builder< String, VALUE > builder = ImmutableMap.builder() ;
      for( final VALUE value : values ) {
        builder.put( value.xmlName(), value ) ;
      }
      return builder.build() ;
    }
  }

  interface NodeToken< NODE extends NodeToken, LEAF extends LeafToken>
    extends Token
  {
    ImmutableSet< NODE > subnodes() ;
    ImmutableSet< LEAF > subleaves() ;

    class Auto< NODE extends NodeToken, LEAF extends LeafToken>
        extends Autonamed
        implements NodeToken< NODE, LEAF >
    {
      private final ImmutableSet< NODE > nodes ;
      private final ImmutableSet< LEAF > leaves ;

      public Auto( final ImmutableSet < NODE > nodes, final ImmutableSet< LEAF > leaves ) {
        super( null ) ;
        this.nodes = checkNotNull( nodes ) ;
        this.leaves = checkNotNull( leaves ) ;
      }

      @Override
      public ImmutableSet< NODE > subnodes() {
        return nodes ;
      }

      @Override
      public final ImmutableSet< LEAF > subleaves() {
        return leaves ;
      }
    }
  }

  interface LeafToken< OBJECT > extends Token {
    void toWire( OBJECT object, WireWriter wireWriter ) throws WireException ;
    OBJECT fromWire( WireReader wireReader ) throws WireException ;

    abstract class Auto< ITEM > extends Autonamed< ITEM > implements LeafToken< ITEM > {
      public Auto() {
        this( null ) ;
      }
      public Auto( final String customXmlName ) {
        super( customXmlName ) ;
      }
    }

    class Autoconvert< ITEM > extends Autonamed< ITEM > implements LeafToken< ITEM > {

      private final Converter< ITEM, String > converter ;

      public Autoconvert( final Converter< ITEM, String > converter ) {
        this( null, converter ) ;
      }

      public Autoconvert(
          final String customXmlName,
          final Converter< ITEM, String > converter
      ) {
        super( customXmlName ) ;
        this.converter = checkNotNull( converter ) ;
      }

      @Override
      public void toWire( final ITEM item, final WireWriter wireWriter ) throws WireException {
        wireWriter.writeDelimitedString( converter.convert( item ) ) ;
      }

      @Override
      public ITEM fromWire( final WireReader wireReader ) throws WireException {
        return converter.reverse().convert( wireReader.readDelimitedString() ) ;
      }
    }
  }


  interface WireWriter {
    void writeIntegerPrimitive( final int i ) throws WireException ;
    void writeIntegerObject( final Integer integer ) throws WireException ;
    void writeDelimitedString( final String string ) throws WireException ;
    // ...
  }

  interface NodeWriter<
      NODE extends NodeToken< NODE, LEAF >,
      LEAF extends LeafToken
  > {

    /**
     * Sadly we can't enforce {@link LEAF} and {@link ITEM_LEAF} at the same time.
     */
    < ITEM_LEAF extends LeafToken< ITEM >, ITEM > void leaf(
        ITEM_LEAF leaf,
        ITEM item
    ) throws WireException ;

    < ITEM > void singleNode(
        NODE node,
        ITEM item,
        WritingAction< NODE, LEAF, ITEM > writingAction
    ) throws WireException ;

    /**
     * We could also support an unbounded sequence using a termination marker.
     * Then an {@code Iterator} would do the job.
     * A default method could also accept a {@code Stream}.
     */
    < ITEM > void nodeSequence(
        NODE node,
        int count,
        Iterable< ITEM > items,
        WritingAction< NODE, LEAF, ITEM > writingAction
    ) throws WireException ;


    /**
     * Candidate for {@link com.otcdlink.chiron.command.codec.Encoder} replacement.
     * Implementors of this interface don't set the containing {@link NODE}, instead they let
     * the "containing" code chose it.
     */
    interface WritingAction<
        NODE extends NodeToken< NODE, LEAF >,
        LEAF extends LeafToken,
        ITEM
    > {
      void write( NodeWriter< NODE, LEAF > nodeWriter, ITEM item ) throws WireException ;
    }

    default < ITEM > void nodeSequence(
        NODE node,
        Collection< ITEM > items,
        WritingAction< NODE, LEAF, ITEM > nodeSequenceWriter
    ) throws WireException {
      nodeSequence( node, items.size(), items, nodeSequenceWriter ) ;
    }

    interface NodeSequenceWriter<
        NODE extends NodeToken< NODE, LEAF >,
        LEAF extends LeafToken,
        ITEM
    > {
      void write( NodeWriter< NODE, LEAF > nodeWriter, ITEM item ) throws WireException ;
    }
  }

  interface WireReader {
    int readIntegerPrimitive() throws WireException ;
    Integer readIntegerObject() throws WireException ;
    String readDelimitedString() throws WireException;
    // ...
  }


  interface NodeReader< NODE extends NodeToken< NODE, LEAF >, LEAF extends LeafToken> {

    /**
     * Sadly we can't enforce {@link LEAF} and {@link ITEM_LEAF} at the same time.
     */
    < ITEM_LEAF extends LeafToken< ITEM >, ITEM > ITEM leaf( ITEM_LEAF leafWithTypedItem )
        throws WireException ;

    /**
     * @return an {@link ITEM} that will propagate through
     *     {@link #singleNode(NodeToken, ReadingAction)}, or {@code null}.
     *     The choice to return a non-{@code null} value is left to implementor, who might
     *     prefer a side-effect.
     */
    < ITEM > ITEM singleNode(
        NODE node,
        ReadingAction< NODE, LEAF, ITEM > readingAction
    ) throws WireException;

    /**
     * Candidate for {@link com.otcdlink.chiron.command.codec.Decoder} replacement.
     * When reading XML, the {@link #read(NodeReader)} method is called after the
     * parser recognized the {@link NODE}. Implementors don't set the {@link NODE}.
     */
    interface ReadingAction<
        NODE extends NodeToken< NODE, LEAF >,
        LEAF extends LeafToken,
        ITEM
    > {

      /**
       * @return an {@link ITEM} that will propagate through
       *     {@link #singleNode(NodeToken, ReadingAction)}, or {@code null}.
       */
      ITEM read( NodeReader< NODE, LEAF > nodeReader ) throws WireException ;
    }

    < ITEM > void nodeSequence(
        NODE node,
        ReadingAction< NODE, LEAF, ITEM > readingAction
    ) throws WireException ;

    < ITEM, COLLECTION > COLLECTION nodeSequence(
        NODE node,
        Collector< ITEM, ?, COLLECTION > collector,
        ReadingAction< NODE, LEAF, ITEM > readingAction
    ) throws WireException ;

    <
        NODE2 extends NodeToken< NODE2, LEAF2 >,
        LEAF2 extends LeafToken
    > XmlNodeReader< NODE2, LEAF2 > redefineWith(
        ImmutableMap< String, NODE2 > nodes,
        ImmutableMap< String, LEAF2 > leaves
    ) ;



  }

  /**
   * Same contract as {@link javax.xml.stream.Location} but we don't want to exhibit XML API
   * when dealing with {@link ByteBuf}.
   */
  final class Location {
    public final String provenance;
    public final int lineNumber ;
    public final int columnNumber ;
    public final int characterOffset ;
    public final String breadcrumbs ;

    public Location(
        final String provenance,
        final int lineNumber,
        final int columnNumber,
        final int characterOffset,
        final String breadcrumbs
    ) {
      this.provenance = provenance;
      this.lineNumber = lineNumber ;
      this.columnNumber = columnNumber ;
      this.characterOffset = characterOffset ;
      this.breadcrumbs = breadcrumbs ;
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder() ;
      appendMeaningfulFields( builder, ", " ) ;
      builder.insert( 0, ToStringTools.getNiceClassName( this ) + "{" ) ;
      builder.append( '}' ) ;
      return builder.toString() ;
    }

    private void appendMeaningfulFields( final StringBuilder builder, final String separator ) {
      if( provenance != null ) {
        builder.append( "id=" ).append( provenance ) ;
      }
      if( lineNumber >= 0 ) {
        appendSeparatorIfNeeded( builder, separator ) ;
        builder.append( "row=" ).append( lineNumber ) ;
      }
      if( columnNumber >= 0 ) {
        appendSeparatorIfNeeded( builder, separator ) ;
        builder.append( "col=" ).append( columnNumber ) ;
      }
      if( characterOffset >= 0 ) {
        appendSeparatorIfNeeded( builder, separator ) ;
        builder.append( "offset=" ).append( characterOffset ) ;
      }
      if( breadcrumbs != null ) {
        appendSeparatorIfNeeded( builder, separator ) ;
        builder.append( "breadcrumbs=" ).append( breadcrumbs ) ;
      }
    }

    private static void appendSeparatorIfNeeded(
        final StringBuilder stringBuilder,
        final String separator
    ) {
      if( stringBuilder.length() > 0 ) {
        stringBuilder.append( separator ) ;
      }
    }

    public String asString() {
      final StringBuilder stringBuilder = new StringBuilder() ;
      appendMeaningfulFields( stringBuilder, ", " ) ;
      return stringBuilder.toString() ;
    }

    public boolean meaningful() {
      return provenance != null || lineNumber >= 0 || columnNumber >= 0 || characterOffset >= 0 ;
    }
  }

}
