package io.github.otcdlink.chiron.middle.tier;

import com.google.common.base.CaseFormat;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.collection.ConstantShelf;

public abstract class TierName extends ConstantShelf {

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
