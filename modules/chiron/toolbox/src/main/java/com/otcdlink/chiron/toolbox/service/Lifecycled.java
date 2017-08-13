package com.otcdlink.chiron.toolbox.service;

import java.util.concurrent.CompletableFuture;

public interface Lifecycled< COMPLETION > {

  CompletableFuture< Void > start() ;

  CompletableFuture< COMPLETION > stop() ;
}
