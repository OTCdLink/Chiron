package com.otcdlink.chiron.toolbox.collection;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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




}