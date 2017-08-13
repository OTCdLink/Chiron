package com.otcdlink.chiron.upend.tier;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UpendTierNameTest {

  @Test
  public void map() throws Exception {
    assertThat( ! UpendTierName.MAP.isEmpty() ) ;
  }
}