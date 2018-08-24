package com.wavefront.internal.reporter;

import com.wavefront.internal.Entitiesinstantiator;
import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.direct_ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;
import com.wavefront.sdk.proxy.WavefrontProxyClient;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import io.dropwizard.metrics5.Clock;
import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.DeltaCounter;
import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.Histogram;
import io.dropwizard.metrics5.Meter;
import io.dropwizard.metrics5.Metered;
import io.dropwizard.metrics5.MetricAttribute;
import io.dropwizard.metrics5.MetricFilter;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.ScheduledReporter;
import io.dropwizard.metrics5.Snapshot;
import io.dropwizard.metrics5.Timer;
import io.dropwizard.metrics5.WavefrontHistogram;

/**
 * Wavefront Internal Reporter that reports metrics and histograms to Wavefront via proxy or
 * direct ingestion. This internal reporter supports reporter level as well as metric/histogram
 * level point tags.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontInternalReporter implements Reporter, Entitiesinstantiator {
  private static final Logger LOGGER =
      Logger.getLogger(WavefrontInternalReporter.class.getCanonicalName());

  private final ScheduledReporter scheduledReporter;
  private final MetricRegistry internalRegistry;

  /**
   * A builder for {@link WavefrontInternalReporter} instances. Defaults to not using a prefix,
   * using the default clock, a host named "unknown", no point Tags, and not filtering any metrics.
   */
  public static class Builder {
    private String prefix;
    private String source;
    private final Map<String, String> reporterPointTags;
    private final Set<HistogramGranularity> histogramGranularities;

    public Builder() {
      this.prefix = null;
      this.source = "wavefront-internal-reporter";
      this.reporterPointTags = new HashMap<>();
      this.histogramGranularities = new HashSet<>();
    }

    /**
     * Prefix all metric names with the given string. Defaults to null.
     *
     * @param prefix the prefix for all metric names
     * @return {@code this}
     */
    public Builder prefixedWith(String prefix) {
      this.prefix = prefix;
      return this;
    }

    /**
     * Set the source for this reporter. This is equivalent to withHost.
     *
     * @param source the host for all metrics
     * @return {@code this}
     */
    public Builder withSource(String source) {
      this.source = source;
      return this;
    }

    /**
     * Set the Point Tags for this reporter.
     *
     * @param reporterPointTags the pointTags Map for all metrics
     * @return {@code this}
     */
    public Builder withReporterPointTags(Map<String, String> reporterPointTags) {
      this.reporterPointTags.putAll(reporterPointTags);
      return this;
    }

    /**
     * Set a point tag for this reporter.
     *
     * @param tagKey the key of the Point Tag
     * @param tagVal the value of the Point Tag
     * @return {@code this}
     */
    public Builder withReporterPointTag(String tagKey, String tagVal) {
      this.reporterPointTags.put(tagKey, tagVal);
      return this;
    }

    /**
     * Report histogram distributions aggregated into minute intervals
     *
     * @return {@code this}
     */
    public Builder reportMinuteDistribution() {
      this.histogramGranularities.add(HistogramGranularity.MINUTE);
      return this;
    }

    /**
     * Report histogram distributions aggregated into hour intervals
     *
     * @return {@code this}
     */
    public Builder reportHourDistribution() {
      this.histogramGranularities.add(HistogramGranularity.HOUR);
      return this;
    }

    /**
     * Report histogram distributions aggregated into day intervals
     *
     * @return {@code this}
     */
    public Builder reportDayDistribution() {
      this.histogramGranularities.add(HistogramGranularity.DAY);
      return this;
    }

    /**
     * Builds a {@link WavefrontInternalReporter} with the given properties,
     * sending metrics directly to a given Wavefront server using direct ingestion APIs.
     *
     * @param wavefrontDirectIngestionClient Wavefront direct ingestion client
     *                                       to send various Wavefront atoms.
     * @return a {@link WavefrontInternalReporter}
     */
    public WavefrontInternalReporter build(
        WavefrontDirectIngestionClient wavefrontDirectIngestionClient) {
      return new WavefrontInternalReporter(new MetricRegistry(), wavefrontDirectIngestionClient,
          prefix, source, reporterPointTags, histogramGranularities);
    }

    /**
     * Builds a {@link WavefrontInternalReporter} with the given properties,
     * sending metrics using the given {@link WavefrontSender}.
     *
     * @param wavefrontProxyClient Wavefront proxy client to send various Wavefront atoms.
     * @return a {@link WavefrontInternalReporter}
     */
    public WavefrontInternalReporter build(WavefrontProxyClient wavefrontProxyClient) {
      return new WavefrontInternalReporter(new MetricRegistry(), wavefrontProxyClient,
          prefix, source, reporterPointTags, histogramGranularities);
    }
  }

  private final WavefrontSender wavefrontSender;
  private final Clock clock = Clock.defaultClock();
  private final String prefix;
  private final String source;
  private final Map<String, String> reporterPointTags;
  private final Set<HistogramGranularity> histogramGranularities;

  private WavefrontInternalReporter(MetricRegistry registry,
                                    WavefrontSender wavefrontSender,
                                    String prefix,
                                    String source,
                                    Map<String, String> reporterPointTags,
                                    Set<HistogramGranularity> histogramGranularities) {
    internalRegistry = registry;
    scheduledReporter = new ScheduledReporter(registry, "wavefront-reporter", MetricFilter.ALL,
        TimeUnit.SECONDS, TimeUnit.MILLISECONDS, Executors.newSingleThreadScheduledExecutor(),
        true, Collections.emptySet()) {

      /**
       * Called periodically by the polling thread. Subclasses should report all the given metrics.
       *
       * @param gauges     all of the gauges in the registry
       * @param counters   all of the counters in the registry
       * @param histograms all of the histograms in the registry
       * @param meters     all of the meters in the registry
       * @param timers     all of the timers in the registry
       */
      @Override
      @SuppressWarnings("rawtypes")
      public void report(SortedMap<MetricName, Gauge> gauges,
                         SortedMap<MetricName, Counter> counters,
                         SortedMap<MetricName, Histogram> histograms,
                         SortedMap<MetricName, Meter> meters,
                         SortedMap<MetricName, Timer> timers) {
        try {
          for (Map.Entry<MetricName, Gauge> entry : gauges.entrySet()) {
            if (entry.getValue().getValue() instanceof Number) {
              reportGauge(entry.getKey(), entry.getValue());
            }
          }

          for (Map.Entry<MetricName, Counter> entry : counters.entrySet()) {
            reportCounter(entry.getKey(), entry.getValue());
          }

          for (Map.Entry<MetricName, Histogram> entry : histograms.entrySet()) {
            reportHistogram(entry.getKey(), entry.getValue());
          }

          for (Map.Entry<MetricName, Meter> entry : meters.entrySet()) {
            reportMetered(entry.getKey(), entry.getValue());
          }

          for (Map.Entry<MetricName, Timer> entry : timers.entrySet()) {
            reportTimer(entry.getKey(), entry.getValue());
          }
        } catch (IOException e) {
          LOGGER.log(Level.WARNING, "Unable to report to Wavefront", e);
          try {
            wavefrontSender.close();
          } catch (IOException e1) {
            LOGGER.log(Level.WARNING, "Error closing Wavefront", e1);
          }
        }
      }

      @Override
      public void stop() {
        try {
          super.stop();
        } finally {
          try {
            wavefrontSender.close();
          } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error disconnecting from Wavefront", e);
          }
        }
      }
    };

    this.wavefrontSender = wavefrontSender;
    this.prefix = prefix;
    this.source = source;
    this.reporterPointTags = reporterPointTags;
    this.histogramGranularities = histogramGranularities;
  }

  private void reportTimer(MetricName metricName, Timer timer) throws IOException {
    final Snapshot snapshot = timer.getSnapshot();
    final long time = clock.getTime() / 1000;
    sendIfEnabled(MetricAttribute.MAX, metricName,
        scheduledReporter.convertDuration(snapshot.getMax()), time);
    sendIfEnabled(MetricAttribute.MEAN, metricName,
        scheduledReporter.convertDuration(snapshot.getMean()), time);
    sendIfEnabled(MetricAttribute.MIN, metricName,
        scheduledReporter.convertDuration(snapshot.getMin()), time);
    sendIfEnabled(MetricAttribute.STDDEV, metricName,
        scheduledReporter.convertDuration(snapshot.getStdDev()), time);
    sendIfEnabled(MetricAttribute.P50, metricName,
        scheduledReporter.convertDuration(snapshot.getMedian()), time);
    sendIfEnabled(MetricAttribute.P75, metricName,
        scheduledReporter.convertDuration(snapshot.get75thPercentile()), time);
    sendIfEnabled(MetricAttribute.P95, metricName,
        scheduledReporter.convertDuration(snapshot.get95thPercentile()), time);
    sendIfEnabled(MetricAttribute.P98, metricName,
        scheduledReporter.convertDuration(snapshot.get98thPercentile()), time);
    sendIfEnabled(MetricAttribute.P99, metricName,
        scheduledReporter.convertDuration(snapshot.get99thPercentile()), time);
    sendIfEnabled(MetricAttribute.P999, metricName,
        scheduledReporter.convertDuration(snapshot.get999thPercentile()), time);

    reportMetered(metricName, timer);
  }

  private void reportMetered(MetricName metricName, Metered meter) throws IOException {
    final long time = clock.getTime() / 1000;
    sendIfEnabled(MetricAttribute.COUNT, metricName, meter.getCount(), time);
    sendIfEnabled(MetricAttribute.M1_RATE, metricName,
        scheduledReporter.convertRate(meter.getOneMinuteRate()), time);
    sendIfEnabled(MetricAttribute.M5_RATE, metricName,
        scheduledReporter.convertRate(meter.getFiveMinuteRate()), time);
    sendIfEnabled(MetricAttribute.M15_RATE, metricName,
        scheduledReporter.convertRate(meter.getFifteenMinuteRate()), time);
    sendIfEnabled(MetricAttribute.MEAN_RATE, metricName,
        scheduledReporter.convertRate(meter.getMeanRate()), time);
  }

  private void reportHistogram(MetricName metricName, Histogram histogram) throws IOException {
    if (histogram instanceof WavefrontHistogram) {
      String histogramName = prefixAndSanitize(metricName.getKey());
      for (WavefrontHistogramImpl.Distribution distribution :
          ((WavefrontHistogram) histogram).flushDistributions()) {
        wavefrontSender.sendDistribution(histogramName, distribution.centroids,
            histogramGranularities, distribution.timestamp, source, getMetricTags(metricName));
      }
    } else {
      final Snapshot snapshot = histogram.getSnapshot();
      final long time = clock.getTime() / 1000;
      sendIfEnabled(MetricAttribute.COUNT, metricName, histogram.getCount(), time);
      sendIfEnabled(MetricAttribute.MAX, metricName, snapshot.getMax(), time);
      sendIfEnabled(MetricAttribute.MEAN, metricName, snapshot.getMean(), time);
      sendIfEnabled(MetricAttribute.MIN, metricName, snapshot.getMin(), time);
      sendIfEnabled(MetricAttribute.STDDEV, metricName, snapshot.getStdDev(), time);
      sendIfEnabled(MetricAttribute.P50, metricName, snapshot.getMedian(), time);
      sendIfEnabled(MetricAttribute.P75, metricName, snapshot.get75thPercentile(), time);
      sendIfEnabled(MetricAttribute.P95, metricName, snapshot.get95thPercentile(), time);
      sendIfEnabled(MetricAttribute.P98, metricName, snapshot.get98thPercentile(), time);
      sendIfEnabled(MetricAttribute.P99, metricName, snapshot.get99thPercentile(), time);
      sendIfEnabled(MetricAttribute.P999, metricName, snapshot.get999thPercentile(), time);
    }
  }

  private void reportCounter(MetricName metricName, Counter counter) throws IOException {
    if (counter instanceof DeltaCounter) {
      long count = counter.getCount();
      String name = Constants.DELTA_PREFIX +
          prefixAndSanitize(metricName.getKey().substring(1), "count");
      wavefrontSender.sendDeltaCounter(name, count, source, getMetricTags(metricName));
      counter.dec(count);
    } else {
      wavefrontSender.sendMetric(prefixAndSanitize(metricName.getKey(), "count"),
          counter.getCount(), clock.getTime() / 1000, source, getMetricTags(metricName));
    }
  }

  private void reportGauge(MetricName metricName, Gauge<Number> gauge) throws IOException {
    wavefrontSender.sendMetric(prefixAndSanitize(metricName.getKey()),
        gauge.getValue().doubleValue(), clock.getTime() / 1000,
        source, getMetricTags(metricName));
  }

  private void sendIfEnabled(MetricAttribute type, MetricName metricName, double value,
                             long timestamp) throws IOException {
    if (!scheduledReporter.getDisabledMetricAttributes().contains(type)) {
      wavefrontSender.sendMetric(prefixAndSanitize(metricName.getKey(), type.getCode()), value,
          timestamp, source, getMetricTags(metricName));
    }
  }

  private Map<String, String> getMetricTags(MetricName metricName) {
    int tagCount = reporterPointTags.size() + metricName.getTags().size();
    // If there are no tags(point tag(s) or global return an empty map
    if (tagCount == 0) {
      return Collections.emptyMap();
    }

    // NOTE: If the individual metric share the same key as the global point tag key, the
    // metric level value will override global level value for that point tag.
    // Example: Global point tag is    <"Key1", "Value-Global">
    // and metric level point tag is:  <"Key1", "Value-Metric1">
    // the point tag sent to Wavefront will be <"Key1", "Value-Metric1">
    HashMap<String, String> metricTags = new HashMap<>();
    metricTags.putAll(reporterPointTags);
    metricName.getTags().forEach((k, v) -> metricTags.putIfAbsent(k, v));
    return metricTags;
  }

  private String prefixAndSanitize(String... components) {
    return sanitize(MetricRegistry.name(prefix, components).getKey());
  }

  private static String sanitize(String name) {
    return SIMPLE_NAMES.matcher(name).replaceAll("_");
  }

  private static final Pattern SIMPLE_NAMES = Pattern.compile("[^a-zA-Z0-9_.\\-~]");

  @Override
  public void start(long period, TimeUnit unit) {
    scheduledReporter.start(period, unit);
  }

  @Override
  public void stop() {
    scheduledReporter.stop();
  }

  @Override
  public Counter newCounter(MetricName metricName) {
    return internalRegistry.counter(metricName);
  }

  @Override
  public DeltaCounter newDeltaCounter(MetricName metricName) {
    return DeltaCounter.get(internalRegistry, metricName);
  }

  @Override
  public Gauge<Double> newGauge(MetricName metricName, Supplier<Double> supplier) {
    return internalRegistry.register(metricName, () -> supplier.get());
  }

  @Override
  public Histogram newHistogram(MetricName metricName) {
    return internalRegistry.histogram(metricName);
  }

  @Override
  public Timer newTimer(MetricName metricName) {
    return internalRegistry.timer(metricName);
  }

  @Override
  public Meter newMeter(MetricName metricName) {
    return internalRegistry.meter(metricName);
  }

  @Override
  public WavefrontHistogram newWavefrontHistogram(MetricName metricName) {
    return WavefrontHistogram.get(internalRegistry, metricName);
  }
}
