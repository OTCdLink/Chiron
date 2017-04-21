package io.github.otcdlink.chiron;

import org.junit.Test;

/**
 * This test always succeeds. It is useful when running many Test Configurations from
 * IntelliJ IDEA as "before launch" tasks. If there is no test in the containing Test
 * Configuration then IDEA complains. Running as "before launch" tasks opens as many
 * run tabs as there are Run Configurations; the Compound Test Configuration only opens
 * 3 tabs so we don't see failed tests.
 */
public class HappyTest {

  @Test
  public void happy() throws Exception { }
}
