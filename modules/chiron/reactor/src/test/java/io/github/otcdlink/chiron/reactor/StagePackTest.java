package io.github.otcdlink.chiron.reactor;

import io.github.otcdlink.chiron.toolbox.catcher.Catcher;
import mockit.Injectable;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StagePackTest {

  @Test
  public void stratumsNoHttp( @Injectable Catcher catcher ) throws Exception {
    final FakeStage.Pack fakeStagePack = FakeStage.newCasting( catcher, FakeStage.Setup.FASTEST ) ;
    final FakeStage.HttpUpward httpUpward = fakeStagePack.httpUpward() ;
    final FakeStage.HttpDownward httpDownward = fakeStagePack.httpDownward() ;
    assertThat( fakeStagePack.stratums ).contains( httpUpward ) ;
    assertThat( fakeStagePack.stratums ).contains( httpDownward ) ;
    assertThat( fakeStagePack.stratumsNoHttp ).doesNotContain( httpUpward ) ;
    assertThat( fakeStagePack.stratumsNoHttp ).doesNotContain( httpDownward ) ;
  }
}