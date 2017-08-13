package com.otcdlink.chiron.toolbox;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CombinedLatencyTest {

  @Test
  public void calculations() throws Exception {
    final LatencyEvaluator.MeasurementInProgress measurementInProgress =
        new LatencyEvaluator.MeasurementInProgress( 3500, 6000, 17, 1L, 4001L ) ;
    assertThat( measurementInProgress.occurenceCount() ).isEqualTo( 3500 ) ;
    assertThat( measurementInProgress.cumulatedDelayMilliseconds() ).isEqualTo( 6000 ) ;
    assertThat( measurementInProgress.overallMeasurementDuration() ).isEqualTo( 4000 ) ;
    assertThat( measurementInProgress.averageDelayMilliseconds() ).isEqualTo( 1 ) ;
    assertThat( measurementInProgress.throughputOccurencePerSecond() ).isEqualTo( 875f ) ;
  }

  @Test
  public void calculationsFromRealWorld() throws Exception {
    final LatencyEvaluator.MeasurementInProgress measurementInProgress =
        new LatencyEvaluator.MeasurementInProgress( 40_080, 2_432_437, 502, 1477420728494L, 1477420972361L ) ;
    assertThat( measurementInProgress.occurenceCount() ).isEqualTo( 40_080 ) ;
    assertThat( measurementInProgress.cumulatedDelayMilliseconds() ).isEqualTo( 2_432_437 ) ;
    assertThat( measurementInProgress.overallMeasurementDuration() ).isEqualTo( 243_867 ) ;
    assertThat( measurementInProgress.throughputOccurencePerSecond() ).isEqualTo( 164f ) ;
  }
}