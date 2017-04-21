package io.github.otcdlink.chiron.reactor;

/**
 * Rename to "Flowgraph". "Pipeline" conflicts with Netty's Pipeline.
 */
public interface Flowgraph< COMMAND > extends CanonicalFlowgraph< COMMAND > {

  /**
   * Call this after replaying Intraday file.
   */
  void upgrade() throws Exception ;

  /**
   * @deprecated use {@link StagePack#errorTranslator}.
   */
  void injectAtExit( COMMAND command ) ;

  Backdoor< COMMAND > backdoor() ;

  /**
   * Only for tests.
   */
  interface Backdoor< COMMAND > {
    void justProcessImmediately( final COMMAND command ) ;
    Stage.Transformer< COMMAND > throttler() ;
  }
}
