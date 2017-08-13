package com.otcdlink.chiron.toolbox;


import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class StateHolderTest {

  @Test
  public void intialValue() throws Exception {
    assertThat( stateHolder.get() ).isEqualTo( Wxyz.X ) ;
    assertThat( stateHolder.toString() ).isEqualTo( "StateHolder{StateHolderTest$Wxyz.X}" ) ;
  }

  @Test
  public void allowedUpdateSucceeds() throws Exception {
    stateHolder.update( Wxyz.Z, Wxyz.Y, Wxyz.X ) ;
    assertThat( stateHolder.get() ).isEqualTo( Wxyz.Z ) ;
  }

  @Test( expected = IllegalStateException.class )
  public void unallowedUpdateFails() throws Exception {
    assertThat( stateHolder.updateOrFail( Wxyz.Z, Wxyz.Y, Wxyz.W ) ).isEqualTo( Wxyz.X ) ;
  }

  @Test
  public void updateWithBlackhole() throws Exception {
    assertThat( stateHolder.updateOrFail( Wxyz.Z, Predicate.isEqual( Wxyz.X ), Wxyz.W ) )
        .isEqualTo( Wxyz.X ) ;
  }

  @Test
  public void updateMaybeSucceeds() throws Exception {
    assertThat( stateHolder.updateMaybe( Wxyz.Z, Wxyz.Y, Wxyz.X ) ).isTrue() ;
  }

  @Test
  public void updateMaybeFails() throws Exception {
    assertThat( stateHolder.updateMaybe( Wxyz.Z, Wxyz.Y ) ).isFalse() ;
  }

  @Test
  public void stateUpdateObject() throws Exception {
    final StateHolder.StateUpdate< Wxyz > update =
        stateHolder.update( Wxyz.Y, Wxyz.X, Wxyz.Z ) ;
    assertThat( update.previous ).isEqualTo( Wxyz.X ) ;
    assertThat( update.current ).isEqualTo( Wxyz.Y ) ;
    assertThat( update.happened() ).isTrue() ;
  }

  @Test
  public void checkStateInSetOk() throws Exception {
    stateHolder.checkOneOf( Wxyz.X, Wxyz.Y, Wxyz.Z ) ;
    stateHolder.checkIn( this, Wxyz.X ) ;
    stateHolder.checkIn( this, Wxyz.X, Wxyz.Y ) ;
    stateHolder.checkIn( this, Wxyz.X, Wxyz.Y, Wxyz.Z ) ;
  }

  @Test( expected = IllegalStateException.class )
  public void checkState1Fails() throws Exception {
    stateHolder.checkOneOf( Wxyz.Y ) ;
  }

  @Test( expected = IllegalStateException.class )
  public void checkState2Fails() throws Exception {
    stateHolder.checkOneOf( Wxyz.Y, Wxyz.Z ) ;
  }

  @Test( expected = IllegalStateException.class )
  public void checkState3Fails() throws Exception {
    stateHolder.checkOneOf( Wxyz.W, Wxyz.Y, Wxyz.Z ) ;
  }

  // =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( StateHolderTest.class ) ;
  private enum Wxyz { W, X, Y, Z }

  private final StateHolder< Wxyz > stateHolder = new StateHolder<>(
      Wxyz.X, LOGGER, this::toString ) ;

}