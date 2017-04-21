package io.github.otcdlink.chiron.conductor;

import com.google.common.collect.ImmutableList;

import java.util.function.Predicate;

/**
 * Query recorded {@link INBOUND} objects.
 */
public interface Extractor< INBOUND > {

  ImmutableList< INBOUND > drainInbound() ;

  INBOUND waitForNextInbound() ;

  INBOUND waitForInboundMatching( Predicate< INBOUND > matcher ) ;
}
