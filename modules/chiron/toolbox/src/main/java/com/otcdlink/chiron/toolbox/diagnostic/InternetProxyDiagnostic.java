package com.otcdlink.chiron.toolbox.diagnostic;

import com.google.common.collect.ImmutableMultimap;
import com.otcdlink.chiron.toolbox.internet.InternetProxyAccess;

public class InternetProxyDiagnostic extends BaseDiagnostic {

  public InternetProxyDiagnostic( final InternetProxyAccess internetProxyAccess ) {
    super(
        ImmutableMultimap.of(
            NO_KEY,
            internetProxyAccess == null ? "[No Internet proxy]" : internetProxyAccess.asString()
        )
    ) ;
  }
}
