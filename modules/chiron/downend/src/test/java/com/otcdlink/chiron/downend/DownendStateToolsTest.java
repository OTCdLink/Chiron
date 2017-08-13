package com.otcdlink.chiron.downend;

import com.otcdlink.chiron.middle.tier.ConnectionDescriptor;
import com.otcdlink.chiron.middle.tier.TimeBoundary;
import com.otcdlink.chiron.toolbox.catcher.Catcher;
import mockit.Injectable;
import mockit.VerificationsInOrder;
import org.junit.Test;

public class DownendStateToolsTest {

  @Test
  public void asChangeWatcher(
      @Injectable final Catcher catcher,
      @Injectable final DownendStateTools.ConnectionAvailabilityWatcher watcher
  ) throws Exception {
    final CommandTransceiver.ChangeWatcher changeWatcher =
        DownendStateTools.asChangeWatcher( catcher, null, watcher ) ;

    changeWatcher.stateChanged( new DownendConnector.Change<>(
        DownendConnector.State.CONNECTING ) ) ;

    new VerificationsInOrder() {{ }} ;

    changeWatcher.stateChanged( SUCCESSFUL_CONNECTION ) ;

    new VerificationsInOrder() {{ }} ;

    changeWatcher.stateChanged( new DownendConnector.Change<>(
        DownendConnector.State.SIGNED_IN ) ) ;

    new VerificationsInOrder() {{
      watcher.onSignonSuccess( "" ) ;
    }} ;

    changeWatcher.stateChanged( new DownendConnector.Change<>(
        DownendConnector.State.CONNECTING ) ) ;

    new VerificationsInOrder() {{
      watcher.onConnectionUnavailable() ;
    }} ;

    changeWatcher.stateChanged( new DownendConnector.Change<>(
        DownendConnector.State.SIGNED_IN ) ) ;

    new VerificationsInOrder() {{
      watcher.onSignonSuccess( "" ) ;
    }} ;

  }

// =======
// Fixture
// =======

  private static final DownendConnector.Change.SuccessfulConnection SUCCESSFUL_CONNECTION =
      new DownendConnector.Change.SuccessfulConnection(
          new ConnectionDescriptor( "version", true, TimeBoundary.DEFAULT ) )
      ;



}