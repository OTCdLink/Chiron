package io.github.otcdlink.chiron.toolbox;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.github.otcdlink.chiron.toolbox.SafeSystemProperty.forBoolean;
import static io.github.otcdlink.chiron.toolbox.SafeSystemProperty.forInteger;
import static io.github.otcdlink.chiron.toolbox.SafeSystemProperty.forUnvalued;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class SafeSystemPropertyTest {

  @Test
  public void logDefaultSystemProperties() throws Exception {
    assert SafeSystemProperty.Standard.JAVA_CLASS_PATH.defined ;
  }

  @Test
  public void wellFormedInteger() throws Exception {
    System.setProperty( INTEGER_KEY, "12" ) ;
    final SafeSystemProperty.IntegerType integerProperty = forInteger( INTEGER_KEY ) ;

    assertThat( integerProperty.defined ).isTrue() ;
    assertThat( integerProperty.wellFormed ).isTrue() ;
    assertThat( integerProperty.key ).isEqualTo( INTEGER_KEY ) ;
    assertThat( integerProperty.value ).isEqualTo( 12 ) ;
    assertThat( integerProperty.intValue( 3 ) ).isEqualTo( 12 ) ;
  }

  @Test
  public void valuedStringList() throws Exception {
    System.setProperty( STRINGLIST_KEY, "foo:bar" ) ;
    final SafeSystemProperty.StringListType stringListProperty =
        SafeSystemProperty.forStringList( STRINGLIST_KEY, ":" ) ;
    assertThat( stringListProperty.defined ).isTrue() ;
    assertThat( stringListProperty.wellFormed ).isTrue() ;
    assertThat( stringListProperty.key ).isEqualTo( STRINGLIST_KEY ) ;
    assertThat( stringListProperty.value ).isEqualTo( ImmutableList.of( "foo", "bar" ) ) ;
  }



  @Test
  public void reloadInteger() throws Exception {
    System.setProperty( INTEGER_KEY, "12" ) ;
    final SafeSystemProperty.IntegerType integerProperty = forInteger( INTEGER_KEY ) ;
    System.setProperty( INTEGER_KEY, "13" ) ;
    final SafeSystemProperty.IntegerType reloaded = integerProperty.reload() ;

    assertThat( reloaded.defined ).isTrue() ;
    assertThat( reloaded.wellFormed ).isTrue() ;
    assertThat( reloaded.key ).isEqualTo( INTEGER_KEY ) ;
    assertThat( reloaded.value ).isEqualTo( 13 ) ;
    assertThat( reloaded.intValue( 3 ) ).isEqualTo( 13 ) ;
  }

  @Test
  public void malformedInteger() throws Exception {
    System.setProperty( INTEGER_KEY, "x" ) ;
    final SafeSystemProperty.IntegerType integerProperty = forInteger( INTEGER_KEY ) ;
    assertThat( integerProperty.defined ).isTrue() ;
    assertThat( integerProperty.wellFormed ).isFalse() ;
    assertThat( integerProperty.key ).isEqualTo( INTEGER_KEY ) ;
    assertThat( integerProperty.value ).isNull() ;
    assertThat( integerProperty.intValue( 1 ) ).isEqualTo( 1 ) ;

  }

  @Test
  public void valuedBoolean() throws Exception {
    System.setProperty( BOOLEAN_KEY, "true" ) ;
    final SafeSystemProperty.BooleanType booleanProperty = forBoolean( BOOLEAN_KEY ) ;

    assertThat( booleanProperty.defined ).isTrue() ;
    assertThat( booleanProperty.wellFormed ).isTrue() ;
    assertThat( booleanProperty.key ).isEqualTo( BOOLEAN_KEY ) ;
    assertThat( booleanProperty.value ).isTrue() ;
    assertThat( booleanProperty.explicitelyTrue() ).isTrue() ;

  }

  @Test
  public void unvalued() throws Exception {
    System.setProperty( UNVALUED_KEY, "" ) ;
    final SafeSystemProperty.Unvalued unvaluedProperty = forUnvalued( UNVALUED_KEY ) ;

    assertThat( unvaluedProperty.defined ).isTrue() ;
    assertThat( unvaluedProperty.wellFormed ).isTrue() ;
    assertThat( unvaluedProperty.key ).isEqualTo( UNVALUED_KEY ) ;
    assertThat( unvaluedProperty.value ).isNull() ;
    assertThat( unvaluedProperty.isSet() ).isTrue() ;
  }

  @Test
  public void reloadUnvalued() throws Exception {
    final SafeSystemProperty.Unvalued unvaluedProperty = forUnvalued( UNVALUED_KEY ) ;
    System.setProperty( UNVALUED_KEY, "" ) ;
    final SafeSystemProperty.Unvalued reloaded = unvaluedProperty.reload() ;

    assertThat( reloaded.defined ).isTrue() ;
    assertThat( reloaded.wellFormed ).isTrue() ;
    assertThat( reloaded.key ).isEqualTo( UNVALUED_KEY ) ;
    assertThat( reloaded.value ).isNull() ;
  }

  @Test
  public void unvaluedMalformed() throws Exception {
    System.setProperty( UNVALUED_KEY, "unexpected" ) ;
    final SafeSystemProperty.Unvalued unvaluedProperty = forUnvalued( UNVALUED_KEY ) ;

    assertThat( unvaluedProperty.defined ).isTrue() ;
    assertThat( unvaluedProperty.wellFormed ).isFalse() ;
    assertThat( unvaluedProperty.key ).isEqualTo( UNVALUED_KEY ) ;
    assertThat( unvaluedProperty.value ).isNull() ;
  }

  @Test
  public void unvaluedUndefined() throws Exception {
    final SafeSystemProperty.Unvalued unvaluedProperty = forUnvalued( UNVALUED_KEY ) ;

    assertThat( unvaluedProperty.defined ).isFalse() ;
    assertThat( unvaluedProperty.wellFormed ).isTrue() ;
    assertThat( unvaluedProperty.key ).isEqualTo( UNVALUED_KEY ) ;
    assertThat( unvaluedProperty.value ).isNull() ;
    assertThat( unvaluedProperty.isSet()).isFalse() ;
  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( SafeSystemPropertyTest.class ) ;

  private static final String INTEGER_KEY = SafeSystemPropertyTest.class.getName() + ".integer" ;

  private static final String STRINGLIST_KEY =
      SafeSystemPropertyTest.class.getName() + ".stringList" ;

  private static final String BOOLEAN_KEY = SafeSystemPropertyTest.class.getName() + ".boolean" ;

  private static final String UNVALUED_KEY = SafeSystemPropertyTest.class.getName() + ".unvalued" ;

  private static final ImmutableList< String > KEYS = ImmutableList.of(
      INTEGER_KEY,
      BOOLEAN_KEY,
      UNVALUED_KEY
  ) ;

  @Before
  public void setUp() throws Exception {
    for( final String key : KEYS ) {
      final String value = System.getProperty( key ) ;
      if( value != null ) {
        fail( "Property '" + key + "' already set to " +
            value == null ? "null" : "'" + value + "'" ) ;
      }
    }
  }

  @After
  public void tearDown() throws Exception {
    KEYS.forEach( System::clearProperty ) ;
  }

}