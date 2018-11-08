package com.wavefront.config;

import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.proxy.WavefrontProxyClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.wavefront.sdk.direct.ingestion.WavefrontDirectIngestionClient;

import java.io.File;
import java.io.IOException;

public class ReportingUtils {
  public static WavefrontSender constructWavefrontSender(
      WavefrontReportingConfig wfReportingConfig) {
    String reportingMechanism = wfReportingConfig.getReportingMechanism();
    switch (reportingMechanism) {
      case WavefrontReportingConfig.proxyReporting:
        return new WavefrontProxyClient.Builder(wfReportingConfig.getProxyHost()).
            metricsPort(wfReportingConfig.getProxyMetricsPort()).
            distributionPort(wfReportingConfig.getProxyDistributionsPort()).
            tracingPort(wfReportingConfig.getProxyTracingPort()).build();
      case WavefrontReportingConfig.directReporting:
        return new WavefrontDirectIngestionClient.Builder(
            wfReportingConfig.getServer(), wfReportingConfig.getToken()).build();
      default:
        throw new RuntimeException("Invalid reporting mechanism:" + reportingMechanism);
    }
  }

  public static ApplicationTags constructApplicationTags(String applicationTagsYamlFile) {
    YAMLFactory factory = new YAMLFactory(new ObjectMapper());
    YAMLParser parser;
    try {
      parser = factory.createParser(new File(applicationTagsYamlFile));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    ApplicationTagsConfig applicationTagsConfig;
    try {
      applicationTagsConfig = parser.readValueAs(ApplicationTagsConfig.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    ApplicationTags.Builder applicationTagsBuilder = new ApplicationTags.Builder(
        applicationTagsConfig.getApplication(), applicationTagsConfig.getService());

    if (applicationTagsConfig.getCluster() != null) {
      applicationTagsBuilder.cluster(applicationTagsConfig.getCluster());
    }

    if (applicationTagsConfig.getShard() != null) {
      applicationTagsBuilder.shard(applicationTagsConfig.getShard());
    }

    if (applicationTagsConfig.getCustomTags() != null) {
      applicationTagsBuilder.customTags(applicationTagsConfig.getCustomTags());
    }

    return applicationTagsBuilder.build();
  }

  public static WavefrontReportingConfig constructWavefrontReportingConfig(
      String wfReportingConfigYamlFile) {
    YAMLFactory factory = new YAMLFactory(new ObjectMapper());
    YAMLParser parser;
    try {
      parser = factory.createParser(new File(wfReportingConfigYamlFile));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    try {
      return parser.readValueAs(WavefrontReportingConfig.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
