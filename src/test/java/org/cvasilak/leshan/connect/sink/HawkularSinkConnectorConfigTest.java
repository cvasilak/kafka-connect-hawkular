package org.cvasilak.leshan.connect.sink;

import org.cvasilak.hawkular.kafka.connect.sink.HawkularSinkConnectorConfig;
import org.junit.Test;

public class HawkularSinkConnectorConfigTest {
  @Test
  public void doc() {
    System.out.println(HawkularSinkConnectorConfig.CONFIG.toRst());
  }
}
