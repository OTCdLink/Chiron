package com.otcdlink.chiron.configuration.source;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DashedCommandLineSourceTest {

  @Test
  public void simpleParse() throws Exception {
    final DashedCommandLineSource dashedCommandLineSource
        = new DashedCommandLineSource( ImmutableList.of(
            "--file-name", "my/file.txt",
            "--timeout", "2000"
        )
    ) ;
    assertThat( dashedCommandLineSource.map() ).isEqualTo( ImmutableMap.of(
        "file-name", "my/file.txt",
        "timeout", "2000"
    ) ) ;

  }
}
