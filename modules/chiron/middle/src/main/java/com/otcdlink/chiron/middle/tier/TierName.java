package com.otcdlink.chiron.middle.tier;

import com.google.common.base.CaseFormat;
import com.otcdlink.chiron.toolbox.ToStringTools;
import com.otcdlink.chiron.toolbox.collection.Autoconstant;

public abstract class TierName extends Autoconstant {

  public final String tierName() {
    return CaseFormat.UPPER_UNDERSCORE.to( CaseFormat.LOWER_HYPHEN, name() ) ;
  }

  public final String javaName() {
    return CaseFormat.LOWER_HYPHEN.to( CaseFormat.UPPER_UNDERSCORE, name() ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.getNiceClassName( this ) + "{" + tierName() + "}" ;
  }
}
