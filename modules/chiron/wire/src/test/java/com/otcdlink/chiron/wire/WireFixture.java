package com.otcdlink.chiron.wire;

import com.google.common.base.Converter;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.otcdlink.chiron.buffer.CrudeReader;
import com.otcdlink.chiron.buffer.CrudeWriter;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.toolbox.ToStringTools;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.of;

@SuppressWarnings( "unused" )
interface WireFixture {

  static XMLStreamReader newStreamReader( final String... xmlLines ) throws XMLStreamException {
    final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance() ;
    final XMLStreamReader xmlStreamReader =
        xmlInputFactory.createXMLStreamReader( new StringReader( Joiner.on( "\n" ).join( xmlLines ) ) ) ;
    return xmlStreamReader ;
  }
  static XMLStreamReader newStreamReader( final String xml ) throws XMLStreamException {
    final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance() ;
    final XMLStreamReader xmlStreamReader =
        xmlInputFactory.createXMLStreamReader( new StringReader( xml ) ) ;
    return xmlStreamReader ;
  }


  abstract class Composite< SUBITEM > {
    public final ImmutableList< SUBITEM > subitems ;

    public Composite( final ImmutableList< SUBITEM > subitems ) {
      this.subitems = checkNotNull( subitems ) ;
    }

    @Override
    public final String toString() {
      final StringBuilder stringBuilder = new StringBuilder() ;
      buildAdditionalToString( stringBuilder ) ;
      if( stringBuilder.length() > 0 ) {
        stringBuilder.append( ';' ) ;
      }
      stringBuilder.append( subitems.toString() ) ;
      return ToStringTools.getNiceClassName( this ) + "{" + stringBuilder.toString() + '}' ;
    }

    protected void buildAdditionalToString( final StringBuilder stringBuilder ) { }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final Composite that = ( Composite ) other ;
      return Objects.equals( subitems, that.subitems ) ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( subitems ) ;
    }
  }

  /**
   * Different from {@link Tree} because {@link Terminal} don't call
   * {@link com.otcdlink.chiron.wire.Wire.NodeReader#nodeSequence(Wire.NodeToken, Wire.NodeReader.ReadingAction)}.
   */
  class Rake extends Composite< Terminal > {
    public final String string ;

    public Rake( final String string, final ImmutableList< Terminal > children ) {
      super( children ) ;
      this.string = string ;
    }

    @Override
    protected void buildAdditionalToString( final StringBuilder stringBuilder ) {
      stringBuilder.append( "string=" ).append( string ) ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( ! super.equals( other ) ) {
        return false ;
      }
      final Rake that = ( Rake ) other ;
      return Objects.equals( string, that.string ) ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( super.hashCode(), string ) ;
    }

  }
  class Tree extends Composite< Branch > {
    public final String s;

    public Tree( final String s, final ImmutableList< Branch > children ) {
      super( children ) ;
      this.s = s ;
    }

    @Override
    protected void buildAdditionalToString( final StringBuilder stringBuilder ) {
      stringBuilder.append( "s=" ).append( s ) ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( ! super.equals( other ) ) {
        return false ;
      }
      final Tree that = ( Tree ) other ;
      return Objects.equals( s, that.s ) ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( super.hashCode(), s ) ;
    }

  }

  class Branch extends Composite< Branch > {
    public final int i ;

    public Branch( final int i, final ImmutableList< Branch > children ) {
      super( children ) ;
      this.i = i ;
    }

    @Override
    protected void buildAdditionalToString( final StringBuilder stringBuilder ) {
      stringBuilder.append( "i=" ).append( i ) ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( ! super.equals( other ) ) {
        return false ;
      }
      final Branch that = ( Branch ) other ;
      return Objects.equals( i, that.i ) ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( super.hashCode(), i ) ;
    }

  }

  class Terminal {
    public final Integer integer ;

    public Terminal( final Integer integer ) {
      this.integer = integer ;
    }


    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final Terminal that = ( Terminal ) other ;
      return Objects.equals( integer, that.integer ) ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( integer ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" +
          "integer=" + integer +
          '}'
      ;
    }
  }

  class MyNodeToken extends Wire.NodeToken.Auto <MyNodeToken, MyLeafToken> {

    private MyNodeToken(
        final ImmutableSet< MyNodeToken> myNodeTokens,
        final ImmutableSet<MyLeafToken> myLeaves
    ) {
      super( myNodeTokens, myLeaves ) ;
    }

    private MyNodeToken( final ImmutableSet<MyLeafToken> myLeaves ) {
      super( ImmutableSet.of(), myLeaves ) ;
    }

    public static final MyNodeToken TERMINAL = new MyNodeToken( of( MyLeafToken.I ) ) ;

    public static final MyNodeToken BRANCH = new MyNodeToken( of( MyLeafToken.I ) ) {
      @Override
      public ImmutableSet< MyNodeToken > subnodes() {
        return BRANCH_SUBNODES ;
      }
    } ;
    public static final MyNodeToken RAKE = new MyNodeToken( of( TERMINAL ), of( MyLeafToken.S ) ) ;
    public static final MyNodeToken TREE = new MyNodeToken( of( BRANCH ), of( MyLeafToken.S ) ) ;

    /**
     * Avoid illegal forward reference in static declarations.
     */
    private static final ImmutableSet< MyNodeToken > BRANCH_SUBNODES =
        ImmutableSet.of( BRANCH ) ;

    public static final ImmutableMap< String, MyNodeToken > MAP = valueMap( MyNodeToken.class ) ;

  }

  /**
   * Would be shorter using {@link Wire.LeafToken.Autoconvert}
   * and {@link Converter#identity()} and {@link Ints#stringConverter()} but this implementation
   * shows we get full control at low-level.
   */
  abstract class MyLeafToken< ITEM > extends Wire.LeafToken.Auto< ITEM > {
    public static final MyLeafToken< String > S = new MyLeafToken< String >() {
      @Override
      public void toWire( final String string, final CrudeWriter crudeWriter )
          throws WireException
      {
        crudeWriter.writeDelimitedString( string ) ;
      }

      @Override
      public String fromWire( final CrudeReader crudeReader ) throws DecodeException {
        return crudeReader.readDelimitedString() ;
      }
    } ;

    public static final MyLeafToken< Integer > I = new MyLeafToken< Integer >() {
      @Override
      public void toWire( final Integer integer, final CrudeWriter crudeWriter )
          throws WireException
      {
        crudeWriter.writeIntegerPrimitive( integer ) ;
      }

      @Override
      public Integer fromWire( final CrudeReader crudeReader ) throws DecodeException {
        return crudeReader.readIntegerPrimitive() ;
      }
    } ;

    public static final ImmutableMap< String, MyLeafToken> MAP = valueMap( MyLeafToken.class ) ;

    protected MyLeafToken() {
      super( null ) ;
    }

  }


// ====
// Rake
// ====

  static void writeRake(
      final Wire.NodeWriter< MyNodeToken, MyLeafToken > nodeWriter,
      final Rake parent
  ) throws WireException {
    nodeWriter.leaf( MyLeafToken.S, parent.string ) ;  // Type enforcement at Leaf level.
    nodeWriter.nodeSequence(
        MyNodeToken.TERMINAL,
        parent.subitems.size(),
        parent.subitems,
        WireFixture::writeTerminal  // Contract is method reference-friendly.
    ) ;
  }

  static void writeTerminal(
      final Wire.NodeWriter<MyNodeToken, MyLeafToken> nodeWriter,
      final Terminal child
  ) throws WireException {
    nodeWriter.leaf( MyLeafToken.I, child.integer ) ;
  }

  static Rake readRake( final Wire.NodeReader<MyNodeToken, MyLeafToken> nodeReader )
      throws WireException
  {
    final String string = nodeReader.leaf( MyLeafToken.S ) ;
    final List< Terminal > children = new ArrayList<>() ;
    nodeReader.nodeSequence(
        MyNodeToken.TERMINAL,
        ( nodeReader1 ) -> {
          final Terminal terminal = new Terminal( nodeReader1.leaf( MyLeafToken.I ) ) ;
          children.add( terminal ) ;
          return terminal ;
        }
    ) ;
    return new Rake( string, ImmutableList.copyOf( children ) ) ;
  }

// ====
// Tree
// ====

  static void writeTree(
      final Wire.NodeWriter<MyNodeToken, MyLeafToken> nodeWriter,
      final Tree tree
  ) throws WireException {
    nodeWriter.leaf( MyLeafToken.S, tree.s ) ;
    nodeWriter.nodeSequence(
        MyNodeToken.BRANCH,
        tree.subitems.size(),
        tree.subitems,
        WireFixture::writeBranch
    ) ;
  }

  static void writeBranch(
      final Wire.NodeWriter<MyNodeToken, MyLeafToken> nodeWriter,
      final Branch branch
  ) throws WireException {
    nodeWriter.leaf( MyLeafToken.I, branch.i ) ;
    nodeWriter.nodeSequence(
        MyNodeToken.BRANCH,
        branch.subitems.size(),
        branch.subitems,
        WireFixture::writeBranch
    ) ;
  }

  static Tree readTree( final Wire.NodeReader<MyNodeToken, MyLeafToken> nodeReader )
      throws WireException
  {
    final String string = nodeReader.leaf( MyLeafToken.S ) ;
    final List< Branch > subitems = new ArrayList<>() ;
    nodeReader.nodeSequence(
        MyNodeToken.BRANCH,
        ( nodeReader1 ) -> readBranch( nodeReader, subitems )
    ) ;
    return new Tree( string, ImmutableList.copyOf( subitems ) ) ;
  }

  static Branch readBranch(
      final Wire.NodeReader<MyNodeToken, MyLeafToken> nodeReader,
      final List< Branch > collector
  )
      throws WireException
  {
    final int i = nodeReader.leaf( MyLeafToken.I ) ;
    final List< Branch > subitems = new ArrayList<>() ;
    nodeReader.nodeSequence(
        MyNodeToken.BRANCH,
        ( nodeReader1 ) -> readBranch( nodeReader1, subitems )
    ) ;
    final Branch branch = new Branch( i, ImmutableList.copyOf( subitems ) ) ;
    collector.add( branch ) ;
    return branch;
  }

}
