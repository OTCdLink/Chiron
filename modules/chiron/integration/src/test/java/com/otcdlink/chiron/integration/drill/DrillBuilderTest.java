package com.otcdlink.chiron.integration.drill;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DrillBuilderTest {

  @Test
  public void fakeDownend() {
    assertThatThrownBy( () ->
        ConnectorDrill.newBuilder().withProxy( true ).fakeDownend()
    ).isInstanceOf( FeatureConflictException.class ) ;
    assertThatThrownBy( () ->
        ConnectorDrill.newBuilder().withTls( true ).fakeDownend()
    ).isInstanceOf( FeatureConflictException.class ) ;
    assertThatThrownBy( () ->
        ConnectorDrill.newBuilder().withTls( true ).fakeUpend()
    ).isInstanceOf( FeatureConflictException.class ) ;
  }

  @Test
  public void justBuild() {
    final DrillBuilder drillBuilder = ConnectorDrill.newBuilder()
        .forCommandTransceiver()
            .withCommandInterceptor( null )
            .automaticLifecycle( ConnectorDrill.AutomaticLifecycle.NONE )
            .done()
        .forUpendConnector()
            .withAuthentication( ConnectorDrill.Authentication.ONE_FACTOR )
            .withCommandInterceptor( new CommandInterceptor.Chain( ImmutableList.of() ) )
            .done()
    ;
    assertThat( drillBuilder.forDownend ).isInstanceOf( DrillBuilder.ForDownendConnector.class ) ;
    assertThat( drillBuilder.forUpend ).isInstanceOf( DrillBuilder.ForUpendConnector.class ) ;
    final DrillBuilder.ForUpendConnector forUpendConnector = ( DrillBuilder.ForUpendConnector )
        drillBuilder.forUpend ;
    assertThat( forUpendConnector.commandInterceptor ).isNotNull() ;
    assertThat( forUpendConnector.authentication )
        .isEqualTo( ConnectorDrill.Authentication.ONE_FACTOR ) ;
  }

  @Test
  public void fakes() {
    final DrillBuilder drillBuilder = ConnectorDrill.newBuilder()
        .fakeUpend().done()
        .fakeDownend().done()
    ;
    assertThat( drillBuilder.forDownend ).isInstanceOf( DrillBuilder.ForFakeDownend.class ) ;
    assertThat( drillBuilder.forUpend ).isInstanceOf( DrillBuilder.ForFakeUpend.class ) ;
  }

}

