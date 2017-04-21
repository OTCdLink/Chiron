package io.github.otcdlink.chiron.downend;

import io.github.otcdlink.chiron.command.Command;

import java.util.concurrent.CompletableFuture;

public interface Downend< ENDPOINT_SPECIFIC, UPWARD_DUTY > {

  void send( Command< ENDPOINT_SPECIFIC, UPWARD_DUTY > command ) ;

  DownendConnector.State state() ;

  CompletableFuture< ? > start() ;

  CompletableFuture< ? > stop() ;
}
