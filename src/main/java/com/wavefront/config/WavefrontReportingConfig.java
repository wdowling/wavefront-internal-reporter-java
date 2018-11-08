package com.wavefront.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nonnull;

/**
 * Configuration for reporting telemetry to wavefront either through direct ingestion or through
 * wavefront proxy.
 *
 * @author Srujan Narkedamalli (snarkedamall@wavefront.com).
 */
public class WavefrontReportingConfig {

  public final static String proxyReporting = "proxy";
  public final static String directReporting = "direct";

  /**
   * Reporting mechanism to be used to send telemetry to Wavefront.
   */
  @JsonProperty
  @Nonnull
  private String reportingMechanism;

  /**
   * Wavefront server to be used for direct ingestion.
   */
  @JsonProperty
  private String server;

  /**
   *  Wavefront API token for direct ingestion.
   */
  @JsonProperty
  private String token;

  /**
   * Proxy host for proxy ingestion.
   */
  @JsonProperty
  private String proxyHost;

  /**
   * Proxy port for metrics ingestion.
   */
  @JsonProperty
  private int proxyMetricsPort;

  /**
   * Proxy port for distributions ingestion.
   */
  @JsonProperty
  private int proxyDistributionsPort;

  /**
   * Proxy port for trace/span ingestion.
   */
  @JsonProperty
  private int proxyTracingPort;

  /**
   * Source field that needs to be emitted when you report metrics, histograms and tracing spans
   * to Wavefront.
   */
  @JsonProperty
  private String source;

  /**
   * Set to true/false depending on whether you want to instrument your application to emit traces
   * to Wavefront. If true, traces will be reported, else traces won't be reported to Wavefront.
   * Defaults to false if not set.
   */
  @JsonProperty
  private Boolean reportTraces;

  @Nonnull
  public String getReportingMechanism() {
    return reportingMechanism;
  }

  public String getServer() {
    return server;
  }

  public String getToken() {
    return token;
  }

  public String getProxyHost() {
    return proxyHost;
  }

  public int getProxyMetricsPort() {
    return proxyMetricsPort;
  }

  public int getProxyDistributionsPort() {
    return proxyDistributionsPort;
  }

  public int getProxyTracingPort() {
    return proxyTracingPort;
  }

  public String getSource() {
    return source;
  }

  public Boolean getReportTraces() {
    return reportTraces;
  }

  public void setReportingMechanism(@Nonnull String reportingMechanism) {
    this.reportingMechanism = reportingMechanism;
  }

  public void setServer(String server) {
    this.server = server;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public void setProxyHost(String proxyHost) {
    this.proxyHost = proxyHost;
  }

  public void setProxyMetricsPort(int proxyMetricsPort) {
    this.proxyMetricsPort = proxyMetricsPort;
  }

  public void setProxyDistributionsPort(int proxyDistributionsPort) {
    this.proxyDistributionsPort = proxyDistributionsPort;
  }

  public void setProxyTracingPort(int proxyTracingPort) {
    this.proxyTracingPort = proxyTracingPort;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public void setReportTraces(Boolean reportTraces) {
    this.reportTraces = reportTraces;
  }
}
