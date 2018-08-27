package com.otcdlink.chiron.wire;

import com.google.common.base.Converter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.otcdlink.chiron.toolbox.StringWrapper;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.wire.Wire.NodeReader;
import com.otcdlink.chiron.wire.Wire.NodeToken;
import com.otcdlink.chiron.wire.Wire.NodeWriter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Defines a {@link NodeReader.ReadingAction} and a {@link NodeWriter.WritingAction}
 * from a mini language so we don't have to create a lots of boilerplate to test implementors of
 * {@link NodeReader} and {@link NodeWriter}.
 * The objects to be read or written all go in a {@link PivotNode} which is a stripped-down DOM
 * object.
 *
 * <h1>Naming choices</h1>
 * <p>
 * {@link PlasticNodeToken} and {@link PlasticLeafToken} are "plastic" because they can take
 * a form defined by the {@link Molder}.
 * <p>
 * A {@link PivotNode} is a generic "pivot" format between {@link NodeReader} and {@link NodeWriter}
 * flavors and the serialized form they support.
 */
public interface WireMill {

  static NodeDeclarationStep newBuilder() {
    return new RealMolder() ;
  }

  final class PlasticNodeToken
      extends StringWrapper< PlasticNodeToken >
      implements NodeToken< PlasticNodeToken, PlasticLeafToken >
  {

    /**
     * Mutable because we may create a {@link PlasticNodeToken} as
     * {@link SubnodeDeclarationStep#subnode(java.lang.String) subnode}
     * before defining its
     * {@link LeafDeclarationStep#leaf(java.lang.String, com.google.common.base.Converter) leaves}.
     */
    private final Set< PlasticLeafToken > subleaves = new HashSet<>() ;

    /**
     * Mutable because there can be cyclic dependencies among {@link NodeToken}s.
     */
    private final Set< PlasticNodeToken > subnodes = new HashSet<>() ;

    public PlasticNodeToken( final String xmlName ) {
      super( xmlName ) ;
      checkArgument( ! Strings.isNullOrEmpty( xmlName ) ) ;
    }

    @Override
    public String xmlName() {
      return wrapped ;
    }

    @Override
    public ImmutableSet< PlasticNodeToken > subnodes() {
      return ImmutableSet.copyOf( subnodes ) ;
    }

    @Override
    public ImmutableSet< PlasticLeafToken > subleaves() {
      return ImmutableSet.copyOf( subleaves ) ;
    }
  }

  final class PlasticLeafToken< OBJECT >
      extends StringWrapper< PlasticLeafToken< OBJECT >>
      implements Wire.LeafToken< OBJECT >
  {

    private final Converter< OBJECT, String > converter ;

    protected PlasticLeafToken(
        final String xmlName,
        final Converter< OBJECT, String > converter
    ) {
      super( xmlName ) ;
      this.converter = checkNotNull( converter ) ;
      checkArgument( ! Strings.isNullOrEmpty( xmlName ) ) ;
    }

    @Override
    public String xmlName() {
      return wrapped ;
    }

    @Override
    public void toWire( OBJECT object, Wire.WireWriter wireWriter ) throws WireException {
      wireWriter.writeDelimitedString( converter.convert( object ) ) ;
    }

    @Override
    public OBJECT fromWire( Wire.WireReader wireReader ) throws WireException {
      return converter.reverse().convert( wireReader.readDelimitedString() ) ;
    }
  }

  final class MutablePivotNode {
    public final PlasticNodeToken nodeDeclaration ;
    public final Map< PlasticLeafToken, Object > valuedLeaves = new HashMap<>() ;
    public final Multimap< PlasticNodeToken, MutablePivotNode > subnodes =
        Multimaps.newListMultimap( new HashMap<>(), ArrayList::new ) ;

    public MutablePivotNode( final PlasticNodeToken nodeDeclaration ) {
      this.nodeDeclaration = checkNotNull( nodeDeclaration ) ;
    }

    public PivotNode freeze() {
      final ImmutableListMultimap.Builder< PlasticNodeToken, PivotNode > builder =
          ImmutableListMultimap.builder() ;
      for( final MutablePivotNode mutablePivotNode : subnodes.values() ) {
        builder.put( mutablePivotNode.nodeDeclaration, mutablePivotNode.freeze() ) ;
      }
      return new PivotNode(
          nodeDeclaration,
          ImmutableMap.copyOf( valuedLeaves ),
          builder.build()
      ) ;
    }
  }

  class ImmutableEntry< K, V > implements Map.Entry< K, V > {
    private final K key ;
    private final V value ;

    ImmutableEntry( final @Nullable K key, final @Nullable V value) {
      this.key = key ;
      this.value = value ;
    }

    @Nullable
    @Override
    public final K getKey() {
      return key ;
    }

    @Nullable
    @Override
    public final V getValue() {
      return value ;
    }

    @Override
    public final V setValue( final V value ) {
      throw new UnsupportedOperationException() ;
    }

  }


  final class PivotNode {
    public final PlasticNodeToken plasticNodeDeclaration ;
    public final ImmutableMap< PlasticLeafToken, Object > valuedLeaves ;
    public final ImmutableMultimap< PlasticNodeToken, PivotNode > subnodes ;

    public PivotNode(
        final PlasticNodeToken plasticNodeDeclaration,
        final ImmutableMap< PlasticLeafToken, Object > valuedLeaves,
        final ImmutableMultimap< PlasticNodeToken, PivotNode > subnodes
    ) {
      this.plasticNodeDeclaration = checkNotNull( plasticNodeDeclaration ) ;
      this.valuedLeaves = checkNotNull( valuedLeaves ) ;
      this.subnodes = checkNotNull( subnodes ) ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" +
          "xmlName='" + plasticNodeDeclaration.xmlName() + "';" +
          "valuedLeaves=[" + valuedLeaves.entrySet().stream()
              .map( e -> "'" + e.getKey().xmlName() + "'->" + e.getValue() )
              .collect( Collectors.joining( "," ) ) +
              "];" +
          "subnodes=" + subnodes.values() +
          "}"
      ;
    }

    @Override
    public boolean equals( final Object other ) {
      if( this == other ) {
        return true ;
      }
      if( other == null || getClass() != other.getClass() ) {
        return false ;
      }
      final PivotNode pivotNode = ( PivotNode ) other ;
      return Objects.equals( valuedLeaves, pivotNode.valuedLeaves ) &&
          Objects.equals( subnodes, pivotNode.subnodes ) ;
    }

    @Override
    public int hashCode() {
      return Objects.hash( valuedLeaves, subnodes ) ;
    }
  }


// ======
// Molder
// ======

  interface Molder {

    ImmutableMap< String, PlasticLeafToken > leafDeclarations() ;

    default PlasticNodeToken nodeByName( final String xmlName ) {
      return nodeDeclarations().get( xmlName ) ;
    }

    default PlasticLeafToken leafByName( final String xmlName ) {
      return leafDeclarations().get( xmlName ) ;
    }

    ImmutableMap< String, PlasticNodeToken > nodeDeclarations() ;

    PlasticNodeToken rootNodeDeclaration() ;

    NodeReader.ReadingAction<
        PlasticNodeToken,
        PlasticLeafToken,
        PivotNode
    > rootReadingAction() ;

    NodeWriter.WritingAction<
        PlasticNodeToken,
        PlasticLeafToken,
        PivotNode
    > rootWritingAction() ;
  }

  interface NodeDeclarationStep {
    PreliminaryDeclarationStep node( final String xmlName ) ;
  }

  interface LeafDeclarationStep {

    PreliminaryDeclarationStep leaf(
        final String xmlName,
        final Converter< ?, String > converter
    ) ;
  }

  interface SubnodeDeclarationStep {
    PreliminaryDeclarationStep subnode( final String xmlName ) ;
  }

  interface ReusableBlockStep {
    ReusableBlockBodyStep beginReusableBlock( final String blockName ) ;
  }

  interface ReusableBlockBodyStep {
    ReusableBlockBodyStep single( final String nodeName ) ;
    ReusableBlockBodyStep shallowSequence( final String nodeName ) ;
    ReusableBlockBodyStep reuseAsSingle( final String nodeName, final String blockName ) ;
    ReusableBlockBodyStep reuseAsSequence( final String nodeName, final String blockName ) ;
    ReusableBlockOrRootStep endReusableBlock() ;
  }

  interface ReusableBlockOrRootStep extends ReusableBlockStep {
    StructureStep root( final String rootNodeName, final String blockName ) ;
  }

  interface PreliminaryDeclarationStep
      extends
          NodeDeclarationStep,
          LeafDeclarationStep,
          SubnodeDeclarationStep,
          ReusableBlockOrRootStep
  { }

  interface StructureStep {
    Molder build() ;
  }

  class RealMolder
      implements
          PreliminaryDeclarationStep,
          ReusableBlockBodyStep,
          ReusableBlockOrRootStep,
          StructureStep,
          Molder
  {

    private static abstract class Tread {

      private enum Kind {
        SINGLE, SEQUENCE, BLOCK_SINGLE, BLOCK_SEQUENCE
      }
      final Kind kind ;

      public Tread( final Kind kind ) {
        this.kind = checkNotNull( kind ) ;
      }

      public abstract void applyTo(
          final NodeReader< PlasticNodeToken, PlasticLeafToken > nodeReader,
          final MutablePivotNode receiver
      ) throws WireException ;

      public abstract void applyTo(
          final PivotNode pivotNode,
          final NodeWriter< PlasticNodeToken, PlasticLeafToken > nodeWriter
      ) throws WireException ;

      public abstract static class ActingTread extends Tread {
        public final PlasticNodeToken nodeDeclaration ;

        public ActingTread( final Kind kind, final PlasticNodeToken nodeDeclaration ) {
          super( kind ) ;
          this.nodeDeclaration = checkNotNull( nodeDeclaration ) ;
        }
      }

      public static final class SingleNodeTread extends ActingTread {
        public SingleNodeTread( final PlasticNodeToken node ) {
          super( Kind.SINGLE, node ) ;
        }

        @Override
        public void applyTo(
            final NodeReader< PlasticNodeToken, PlasticLeafToken > nodeReader,
            final MutablePivotNode receiver
        ) throws WireException {
          nodeReader.singleNode( nodeDeclaration, singleNodeReader -> {
            readLeaves( nodeDeclaration, nodeReader, receiver ) ;
            return receiver ;
          } ) ;
        }

        @Override
        public void applyTo(
            final PivotNode pivotNode,
            final NodeWriter< PlasticNodeToken, PlasticLeafToken > nodeWriter
        ) throws WireException {
          nodeWriter.singleNode( nodeDeclaration, pivotNode, ( nodeWriter1, pivotNode1 ) ->
              writeLeaves( nodeDeclaration, pivotNode, nodeWriter ) ) ;
        }

      }

      private static void writeLeaves(
          final PlasticNodeToken nodeDeclaration,
          final PivotNode pivotNode,
          final NodeWriter< PlasticNodeToken, PlasticLeafToken > nodeWriter
      ) throws WireException {
        for( final PlasticLeafToken leafDeclaration : nodeDeclaration.subleaves ) {
          nodeWriter.leaf( leafDeclaration, pivotNode.valuedLeaves.get( leafDeclaration ) ) ;
        }
      }

      private static void readLeaves(
          final PlasticNodeToken node,
          final NodeReader< PlasticNodeToken, PlasticLeafToken > nodeReader,
          final MutablePivotNode receiver
      ) throws WireException {
        for( final PlasticLeafToken plasticLeafDeclaration : node.subleaves() ) {
          receiver.valuedLeaves.put(
              plasticLeafDeclaration,
              nodeReader.leaf( plasticLeafDeclaration )
          ) ;
        }
      }

      public static final class NodeSequenceTread extends ActingTread {
        public NodeSequenceTread( final PlasticNodeToken node ) {
          super( Kind.SEQUENCE, node ) ;
        }

        @Override
        public void applyTo(
            final NodeReader< PlasticNodeToken, PlasticLeafToken > nodeReader,
            final MutablePivotNode receiver
        ) throws WireException {
          nodeReader.nodeSequence( nodeDeclaration, ( sequenceReader ) -> {
            final MutablePivotNode item = new MutablePivotNode( nodeDeclaration ) ;
            Tread.readLeaves( nodeDeclaration, sequenceReader, item ) ;
            receiver.subnodes.put( nodeDeclaration, item ) ;
            return item ;
          } ) ;
        }

        @Override
        public void applyTo(
            final PivotNode pivotNode,
            final NodeWriter< PlasticNodeToken, PlasticLeafToken > nodeWriter
        ) throws WireException {
          nodeWriter.nodeSequence(
              nodeDeclaration,
              pivotNode.subnodes.values(),
              ( nodeWriter1, pivotNode1 ) -> writeLeaves( nodeDeclaration, pivotNode1, nodeWriter1 )
          ) ;
        }
      }

      public static class BlockDefinition {
        private final String identifier ;
        private final List< ActingTread > actingTreads = new ArrayList<>() ;
        private BlockDefinition( final String identifier ) {
          this.identifier = checkNotNull( identifier ) ;
        }

        public Block reuseAs(
            final Kind kind,
            final PlasticNodeToken nodeDeclaration
        ) {
          final Block block = new Block(
              kind, nodeDeclaration, ImmutableList.copyOf( actingTreads ) ) ;
          return block ;
        }
      }

      public static class Block extends ActingTread {
        private final ImmutableList< ActingTread > actingTreads ;
        private Block(
            final Kind kind,
            final PlasticNodeToken nodeDeclaration,
            final ImmutableList< ActingTread > actingTreads
        ) {
          super( kind, nodeDeclaration ) ;
          checkArgument( kind == Kind.BLOCK_SEQUENCE || kind == Kind.BLOCK_SINGLE ) ;
          this.actingTreads = checkNotNull( actingTreads ) ;
        }

        @Override
        public void applyTo(
            final NodeReader< PlasticNodeToken, PlasticLeafToken > nodeReader,
            final MutablePivotNode receiver
        ) throws WireException {
          switch( kind ) {
            case BLOCK_SINGLE :
              Tread.readLeaves( nodeDeclaration, nodeReader, receiver ) ;
            case BLOCK_SEQUENCE :
              for( final ActingTread actingTread : actingTreads ) {
                final MutablePivotNode pivotNode =
                    new MutablePivotNode( actingTread.nodeDeclaration ) ;
                actingTread.applyTo( nodeReader, pivotNode ) ;
                receiver.subnodes.put( nodeDeclaration, pivotNode ) ;
              }
              break ;
            default :
              throw new IllegalStateException( "Unsupported: " + kind ) ;
          }
        }

        @Override
        public void applyTo(
            final PivotNode pivotNode,
            final NodeWriter< PlasticNodeToken, PlasticLeafToken > nodeWriter
        ) throws WireException {
          switch( kind ) {
            case BLOCK_SINGLE :
              Tread.writeLeaves( nodeDeclaration, pivotNode, nodeWriter ) ;
            case BLOCK_SEQUENCE :
              for( final ActingTread actingTread : actingTreads ) {
                final ImmutableCollection< PivotNode > subnodesForThisDeclaration =
                    pivotNode.subnodes.get( actingTread.nodeDeclaration ) ;
                for( final PivotNode subnode : subnodesForThisDeclaration ) {
                  actingTread.applyTo( subnode, nodeWriter ) ;
                }
              }
              break ;
            default :
              throw new IllegalStateException( "Unsupported: " + kind ) ;
          }
        }
      }

    }

    /**
     * The "official" {@link PlasticNodeToken}s for which there is an explicit
     * {@link #subnode(String)} call.
     */
    private final Map< String, PlasticNodeToken > nodeDeclarations = new HashMap<>() ;

    /**
     * Retain every {@link PlasticNodeToken}s, including those which appear as
     * {@link #subnode(String)}s before their official declaration with {@link #node(String)}.
     */
    private final Map< String, PlasticNodeToken > nodeDeclarationPool = new HashMap<>() ;

    private PlasticNodeToken nodeDeclarationBeingBuilt = null ;
    private Map< String, Tread.BlockDefinition > reusableBlocks = new HashMap<>() ;
    private Tread.Block rootTread = null ;
    private Tread.BlockDefinition currentBlockDefinition = null ;

    @Override
    public PreliminaryDeclarationStep node( final String xmlName ) {
      checkState( ! nodeDeclarations.containsKey( xmlName ),
          "Already declared: '" + xmlName + "'" ) ;
      PlasticNodeToken plasticNodeDeclaration = nodeDeclarationPool.get( xmlName ) ;
      if( plasticNodeDeclaration == null ) {
        plasticNodeDeclaration = new PlasticNodeToken( xmlName ) ;
        nodeDeclarationPool.put( xmlName, plasticNodeDeclaration ) ;
      }
      nodeDeclarationBeingBuilt = plasticNodeDeclaration ;
      nodeDeclarations.put( xmlName, plasticNodeDeclaration ) ;
      return this ;
    }

    @Override
    public PreliminaryDeclarationStep leaf(
        final String xmlName,
        final Converter< ?, String > converter
    ) {
      nodeDeclarationBeingBuilt.subleaves.add(
          new PlasticLeafToken<>( xmlName, converter ) ) ;
      return this ;
    }

    @Override
    public PreliminaryDeclarationStep subnode( final String xmlName ) {
      final PlasticNodeToken plasticNodeDeclaration =
          nodeDeclarationPool.computeIfAbsent( xmlName, PlasticNodeToken::new ) ;
      nodeDeclarationBeingBuilt.subnodes.add( plasticNodeDeclaration ) ;
      return this ;
    }

    @Override
    public ReusableBlockBodyStep single( final String nodeName ) {
      RealMolder.this.currentBlockDefinition.actingTreads.add(
          new Tread.SingleNodeTread( getNodeDeclarationSafely( nodeName ) ) ) ;
      return this ;
    }

    @Override
    public ReusableBlockBodyStep shallowSequence( final String nodeName ) {
      RealMolder.this.currentBlockDefinition.actingTreads.add(
          new Tread.NodeSequenceTread( getNodeDeclarationSafely( nodeName ) ) ) ;
      return this ;
    }

    private PlasticNodeToken getNodeDeclarationSafely( final String nodeName ) {
      final PlasticNodeToken plasticNodeDeclaration = nodeDeclarations.get( nodeName ) ;
      checkNotNull( plasticNodeDeclaration, "Unknown: '" + nodeName + "'" ) ;
      return plasticNodeDeclaration ;
    }

    @Override
    public ReusableBlockBodyStep reuseAsSingle( final String nodeName, final String blockName ) {
      final Tread.BlockDefinition blockDefinition = getBlockDefinitionSafely( blockName ) ;
      final Tread.Block tread = blockDefinition.reuseAs(
          Tread.Kind.BLOCK_SINGLE, getNodeDeclarationSafely( nodeName ) ) ;
      RealMolder.this.currentBlockDefinition.actingTreads.add( tread ) ;
      return this ;
    }

    @Override
    public ReusableBlockBodyStep reuseAsSequence( final String nodeName, final String blockName ) {
      final Tread.BlockDefinition blockDefinition = getBlockDefinitionSafely( blockName ) ;
      final Tread.Block tread = blockDefinition.reuseAs(
          Tread.Kind.BLOCK_SEQUENCE, getNodeDeclarationSafely( nodeName ) ) ;
      RealMolder.this.currentBlockDefinition.actingTreads.add( tread ) ;
      return this ;
    }

    @Override
    public ReusableBlockOrRootStep endReusableBlock() {
      RealMolder.this.currentBlockDefinition = null ;
      return RealMolder.this ;
    }

    public Tread.BlockDefinition getBlockDefinitionSafely( String blockName ) {
      final Tread.BlockDefinition blockDefinition = reusableBlocks.get( blockName ) ;
      checkArgument( blockDefinition != null, "Unknown block name: '" + blockName + "'" ) ;
      return blockDefinition;
    }

    @Override
    public ReusableBlockBodyStep beginReusableBlock( final String blockName ) {
      checkState( currentBlockDefinition == null ) ;
      this.currentBlockDefinition = new Tread.BlockDefinition( blockName ) ;

      // Reference it now so it can reference itself.
      reusableBlocks.put( currentBlockDefinition.identifier, currentBlockDefinition ) ;

      return this ;
    }

    @Override
    public StructureStep root( final String rootNodeName, final String blockName ) {
      final Tread.BlockDefinition blockDefinition = getBlockDefinitionSafely( blockName ) ;
      final Tread.Block tread = blockDefinition.reuseAs(
          Tread.Kind.BLOCK_SINGLE, getNodeDeclarationSafely( rootNodeName ) ) ;
      RealMolder.this.rootTread = tread ;
      return this ;

    }

    @Override
    public ImmutableMap< String, PlasticLeafToken > leafDeclarations() {
      return nodeDeclarations.values().stream().flatMap( node -> node.subleaves.stream() )
          .collect( ImmutableMap.toImmutableMap( PlasticLeafToken::xmlName, leaf -> leaf ) ) ;
    }

    @Override
    public PlasticNodeToken nodeByName( String xmlName ) {
      return nodeDeclarations.get( xmlName ) ;  // Superfluous optimisation.
    }

    @Override
    public ImmutableMap< String, PlasticNodeToken > nodeDeclarations() {
      return ImmutableMap.copyOf( nodeDeclarations ) ;
    }

    @Override
    public PlasticNodeToken rootNodeDeclaration() {
      return rootTread.nodeDeclaration ;
    }

    @Override
    public NodeReader.ReadingAction< PlasticNodeToken, PlasticLeafToken, PivotNode >
    rootReadingAction() {
      final MutablePivotNode mutablePivotNode = new MutablePivotNode( rootTread.nodeDeclaration ) ;
      return nodeReader -> {
        rootTread.applyTo( nodeReader, mutablePivotNode ) ;
        return mutablePivotNode.freeze() ;
      } ;
    }

    @Override
    public NodeWriter.WritingAction< PlasticNodeToken, PlasticLeafToken, PivotNode >
    rootWritingAction() {
      return ( nodeWriter, pivotNode ) -> rootTread.applyTo( pivotNode, nodeWriter ) ;
    }

    @Override
    public Molder build() {
      return this ;
    }
  }




}
