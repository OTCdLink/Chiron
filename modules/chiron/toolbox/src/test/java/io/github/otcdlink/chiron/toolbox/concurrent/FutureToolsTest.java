package io.github.otcdlink.chiron.toolbox.concurrent;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static io.github.otcdlink.chiron.toolbox.concurrent.FutureTools.applyToAll;
import static io.github.otcdlink.chiron.toolbox.concurrent.FutureTools.applyToAllAndGatherValues;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FutureToolsTest {

  @Test
  public void noOperation() throws Exception {
    applyToAll( ImmutableList.of(), Ø -> null ).get() ;
  }

  @Test
  public void oneOperation() throws Exception {
    final CompletableFuture< Void > resultFuture = new CompletableFuture<>() ;
    final CompletableFuture< Void > completionFuture = applyToAll(
        ImmutableList.of( 1 ), i -> resultFuture
    ) ;
    assertThat( completionFuture.isDone() ).isFalse() ;
    resultFuture.complete( null ) ;
    completionFuture.get() ;
  }

  @Test
  public void failedOperation() throws Exception {
    final CompletableFuture< Void > resultFuture = new CompletableFuture<>() ;
    final CompletableFuture< Void > completionFuture = applyToAll(
        ImmutableList.of( 1 ), i -> resultFuture
    ) ;
    assertThat( completionFuture.isDone() ).isFalse() ;
    resultFuture.completeExceptionally( new Exception( "Boom") ) ;
    assertThatThrownBy( completionFuture::get ).hasMessageContaining( "Boom" ) ;
  }


  @Test
  public void noResult() throws Exception {
    assertThat( applyToAllAndGatherValues( ImmutableList.of(), Ø -> null ).get() ).isEmpty() ;
  }

  @Test
  public void oneResult() throws Exception {
    assertThat( applyToAllAndGatherValues(
        ImmutableList.of( 1 ), i -> completedFuture( "" + i )
    ).get() )
        .containsExactly( "1" )
    ;
  }

  @Test
  public void twoResults() throws Exception {
    assertThat( applyToAllAndGatherValues(
        ImmutableList.of( 1, 2 ), i -> completedFuture( "" + i )
    ).get() )
        .containsExactly( "1", "2" )
    ;
  }

  @Test
  public void blockUntilsResults() throws Exception {
    final CompletableFuture< String > calculationFuture = new CompletableFuture<>() ;
    final CompletableFuture< ImmutableList< String > > listFuture = applyToAllAndGatherValues(
        ImmutableList.of( 1 ), i -> calculationFuture
    ) ;
    assertThat( listFuture.isDone() ).isFalse() ;
    calculationFuture.complete( "1" ) ;
    assertThat( listFuture.get() ).containsExactly( "1" ) ;
  }

  @Test
  public void failedResult() throws Exception {
    final CompletableFuture< String > calculationFuture = new CompletableFuture<>() ;
    final CompletableFuture< ImmutableList< String > > listFuture = applyToAllAndGatherValues(
        ImmutableList.of( 1 ), i -> calculationFuture
    ) ;
    assertThat( listFuture.isDone() ).isFalse() ;
    calculationFuture.completeExceptionally( new Exception( "Boom" ) ) ;
    assertThatThrownBy( listFuture::get ).hasMessageContaining( "Boom" ) ;
  }


}