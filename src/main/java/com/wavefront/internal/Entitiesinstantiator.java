package com.wavefront.internal;

import java.util.function.Supplier;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.DeltaCounter;
import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.Histogram;
import io.dropwizard.metrics5.Meter;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.Timer;
import io.dropwizard.metrics5.WavefrontHistogram;

/**
 * Entities instantiator that is responsible for instantiating various entities composed of
 * metrics and histograms.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public interface Entitiesinstantiator {

  /**
   * Returns a new counter
   *
   * @param metricName  entity composed of name and optional point tags
   * @return Raw Counter
   */
  Counter newCounter(MetricName metricName);

  /**
   * Returns a new Wavefront DeltaCounter
   *
   * @param metricName  entity composed of name and optional point tags
   * @return Wavefront DeltaCounter
   */
  DeltaCounter newDeltaCounter(MetricName metricName);

  /**
   * Returns a new Gauge
   *
   * @param metricName  entity composed of name and optional point tags
   * @param supplier
   * @return Gauge
   */
  Gauge<Double> newGauge(MetricName metricName, Supplier<Double> supplier);

  /**
   * Returns a histogram
   *
   * @param metricName  entity composed of name and optional point tags
   * @return Histogram
   */
  Histogram newHistogram(MetricName metricName);

  /**
   * Returns a new timer
   *
   * @param metricName  entity composed of name and optional point tags
   * @return Timer
   */
  Timer newTimer(MetricName metricName);

  /**
   * Returns a new meter
   *
   * @param metricName  entity composed of name and optional point tags
   * @return Meter
   */
  Meter newMeter(MetricName metricName);

  /**
   * Returns a new WavefrontHistogram
   *
   * @param metricName  entity composed of name and optional point tags
   * @return WavefrontHistogram
   */
  WavefrontHistogram newWavefrontHistogram(MetricName metricName);
}
