package com.wavefront.internal.reporter;

import java.util.concurrent.TimeUnit;

/**
 * A reporter to report metrics/histograms to periodically Wavefront
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public interface Reporter {
  /**
   * Start Wavefront Internal Reporter
   *
   * @param period    How often you want to send metrics/histograms to Wavefront
   * @param unit      period time unit
   */
  void start(long period, TimeUnit unit);

  /**
   * Stop the Wavefront Internal Reporter
   */
  void stop();
}
