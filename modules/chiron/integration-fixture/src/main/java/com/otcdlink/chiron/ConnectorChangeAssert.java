package com.otcdlink.chiron;

import com.otcdlink.chiron.downend.CommandInFlightStatus;
import com.otcdlink.chiron.downend.DownendConnector;
import org.assertj.core.api.AbstractAssert;


public class ConnectorChangeAssert
    extends AbstractAssert< ConnectorChangeAssert, DownendConnector.Change>
{

  public static ConnectorChangeAssert assertThat( final DownendConnector.Change change ) {
    return new ConnectorChangeAssert( change ) ;
  }

  protected ConnectorChangeAssert( final DownendConnector.Change actual ) {
    super( actual, ConnectorChangeAssert.class ) ;
  }

  public ConnectorChangeAssert hasKind(
      final DownendConnector.ChangeDescriptor changeDescriptorKind
  ) {
    isNotNull() ;
    if( actual.kind != changeDescriptorKind ) {
      failWithMessage( "Expected kind to be <%s> but is <%s> within an instance of <%s>",
          changeDescriptorKind, actual.kind, actual ) ;
    }
    return this ;
  }

  public CommandInFlightStatusStateChangeAssert isInFlightStatusStateChange() {
    isNotNull() ;
    isInstanceOf( ExtendedChange.CommandInFlightStatusChange.class ) ;
    return new CommandInFlightStatusStateChangeAssert(
        ( ExtendedChange.CommandInFlightStatusChange ) actual ) ;
  }

  public static class CommandInFlightStatusStateChangeAssert
      extends AbstractAssert<
          CommandInFlightStatusStateChangeAssert,
      ExtendedChange.CommandInFlightStatusChange
      >
  {
    protected CommandInFlightStatusStateChangeAssert(
        final ExtendedChange.CommandInFlightStatusChange actual
    ) {
      super( actual, CommandInFlightStatusStateChangeAssert.class ) ;
    }

    public CommandInFlightStatusStateChangeAssert is(
        final CommandInFlightStatus commandInFlightStatus
    ) {
      isNotNull() ;
      if( actual.commandInFlightStatus != commandInFlightStatus ) {
        failWithMessage( "Expected status to be <%s> but is <%s>",
            commandInFlightStatus, actual.commandInFlightStatus );
      }
      return this ;
    }
  }


}
