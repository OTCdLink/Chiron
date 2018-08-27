package com.otcdlink.chiron.toolbox.collection;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AutoconstantTest {

  @Test
  public void names() throws Exception {
    assertThat( Enumeration.A.name() ).isEqualTo( "A" ) ;
    assertThat( Enumeration.B.name() ).isEqualTo( "B" ) ;
    assertThat( Enumeration.MAP.values() )
        .containsExactly( Enumeration.A, Enumeration.B, Enumeration.INPLACE_CHILD ) ;

    LOGGER.info( "Values: " + Enumeration.MAP ) ;
  }

  @Test
  public void initializeOutOfStaticDefinition() throws Exception {

    assertThatThrownBy( () -> new LetItBreak().name() )
        .isInstanceOf( IllegalStateException.class )
    ;
  }

  @Test
  public void reuseByReference() throws Exception {
    assertThat( ReuseByReference.A.name() ).isEqualTo( "A" ) ;
    assertThat( ReuseByReference.C.name() ).isEqualTo( "C" ) ;
    assertThat( ReuseByReference.MAP.values() )
        .containsExactly( ReuseByReference.A, ReuseByReference.C )
    ;
  }

  @Test
  public void reuseByInheritance() throws Exception {
    assertThat( EnumerationChild.A.name() ).isEqualTo( "A" ) ;
    assertThat( EnumerationChild.B.name() ).isEqualTo( "B" ) ;
    assertThat( EnumerationChild.CHILD.name() ).isEqualTo( "CHILD" ) ;
    assertThat( EnumerationChild.INPLACE_CHILD.name() ).isEqualTo( "INPLACE_CHILD" ) ;
    assertThat( EnumerationChild.MAP.values() )
        .containsExactly(
            EnumerationChild.A,
            EnumerationChild.B,
            EnumerationChild.CHILD,
            EnumerationChild.INPLACE_CHILD
        )
    ;
  }

  @Test
  public void badReuse() throws Exception {
    assertThatThrownBy(  () ->
    BadReuse.valueMap( BadReuse.class, Enumeration.class ) )
        .isInstanceOf( Autoconstant.DeclarationException.class )
    ;
  }

  @Test
  public void noMap() throws Exception {
    assertThatThrownBy( NoMap.A::name )
        .isInstanceOf( IllegalStateException.class )
    ;
  }

  @Test
  public void descendantOfEmptyParent() throws Exception {
    assertThat( DescendantOfEmptyParent.ITEM ).isNotNull() ;
    assertThat( DescendantOfEmptyParent.MAP ).hasSize( 1 ) ;
  }

  @Test
  public void parameterizedType() throws Exception {
    assertThat( SelfTypedConstant.INTEGER.typeParameter() ).isEqualTo( Integer.class ) ;
  }

  @Test
  public void overridingBehaviors() {
    assertThat( Behavior.I1.something() ).isEqualTo( 1 ) ;
    assertThat( Behavior.I1.genericSomething( null ) ).isEqualTo( 1 ) ;
    assertThat( Behavior.L2.something( 2L, 4L ) ).isEqualTo( 3L ) ;
    assertThat( Behavior.L2.genericSomething( 3L ) ).isEqualTo( 3L ) ;
    assertThat( Behavior.MAP.keySet() ).containsExactly( Behavior.I1.name(), Behavior.L2.name() ) ;
    assertThat( Behavior.MAP.values() ).containsExactly( Behavior.I1, Behavior.L2 ) ;
  }

  // =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( AutoconstantTest.class ) ;

  public static class Enumeration extends Autoconstant {

    private Enumeration() { }

    public static final Enumeration A = new Enumeration() ;
    public static final Enumeration B = new Enumeration() ;

    /**
     * It is dangerous to set an instance of a subclass in a static member but let's say it's OK
     * for the test.
     */
    @SuppressWarnings( { "unused", "StaticInitializerReferencesSubClass" } )
    public static final EnumerationEmptyChild INPLACE_CHILD = new EnumerationEmptyChild() ;

    public static final ImmutableMap< String, Enumeration > MAP = valueMap( Enumeration.class ) ;

  }

  public static class ReuseByReference extends Autoconstant {
    public static final Enumeration A = Enumeration.A ;
    public static final Enumeration C = new Enumeration() ;

    public static final ImmutableMap< String, Enumeration > MAP =
        valueMap( ReuseByReference.class, Enumeration.class ) ;
  }


  public static class EnumerationEmptyChild extends Enumeration { }


  public static class EnumerationChild extends Enumeration {

    public static final EnumerationChild CHILD = new EnumerationChild() ;

    public static final ImmutableMap< String, Enumeration > MAP =
        valueMap( EnumerationChild.class, Enumeration.class ) ;

  }


  public static class BadReuse extends Autoconstant {
    @SuppressWarnings( "unused" )
    public static final Enumeration NAME_MISMATCH = Enumeration.A ;
  }

  public static class NoMap extends Autoconstant {
    public static final NoMap A = new NoMap() ;
    public static final NoMap B = new NoMap() ;
  }


  /**
   * Need a class apart, calling {@link Enumeration()} with no static member assignment
   * wrecks further calls.
   */
  public static class LetItBreak extends Autoconstant {

    /**
     * A production implementation should use a more retrictive visibility.
     */
    @SuppressWarnings( "RedundantNoArgConstructor" )
    public LetItBreak() { }
  }


  /**
   * There has been a doubt one time about the validity of this construct.
   */
  public static class EmptyParent extends Autoconstant { }

  public static class DescendantOfEmptyParent extends EmptyParent {
    public static final DescendantOfEmptyParent ITEM = new DescendantOfEmptyParent() ;
    public static final Map< String, DescendantOfEmptyParent > MAP =
        valueMap( DescendantOfEmptyParent.class ) ;
  }

  public static class SelfTypedConstant< T > extends Autoconstant< T > {
    public static final SelfTypedConstant< Integer > INTEGER = new SelfTypedConstant< Integer >() {};
    @SuppressWarnings( "unused" )
    public static final Map< String, SelfTypedConstant > MAP = valueMap( SelfTypedConstant.class ) ;
  }


  /**
   * Subclasses define specific behaviors stemming from one generic method, and they appear
   * as constants in the parent class. This is much more powerful than method overriding in
   * enum instances, because we can exhibit the refined behavior through a specific method.
   */
  static abstract class Behavior< T > extends Autoconstant< T > {

    protected abstract T genericSomething( T t ) ;

    public static final WithInteger I1 = new WithInteger() {
      @Override
      public Integer something() {
        return 1 ;
      }
    } ;

    public static final WithLong L2 = new WithLong() {
      @Override
      public Long something( final Long l1, final Long l2 ) {
        return ( l1 + l2 ) / 2 ;
      }
    } ;

    @SuppressWarnings( "unused" )
    public static final Map< String, Behavior > MAP = valueMap( Behavior.class ) ;

    public static abstract class WithInteger extends Behavior< Integer > {
      protected final Integer genericSomething( final Integer i ) {
        return something() ;
      }
      public abstract Integer something() ;
    }

    public static abstract class WithLong extends Behavior< Long > {
      protected final Long genericSomething( final Long l ) {
        return something( l, l ) ;
      }
      public abstract Long something( final Long l1, final Long l2 ) ;
    }


  }

}