/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.sink;

import org.apache.iotdb.db.sink.alertmanager.AlertManagerConfiguration;
import org.apache.iotdb.db.sink.alertmanager.AlertManagerEvent;
import org.apache.iotdb.db.sink.alertmanager.AlertManagerHandler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class AlertManagerTest {

  static class TestHandler implements HttpHandler {

    private final String correctRequest;

    public TestHandler(String correctRequest) {
      this.correctRequest = correctRequest;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      InputStreamReader isr =
          new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
      BufferedReader br = new BufferedReader(isr);
      String query = br.readLine();

      assertEquals(correctRequest, query);

      byte[] response = "{\"success\": true}".getBytes();
      exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    }
  }

  @Test
  public void alertManagerTest0() throws Exception {

    HttpServer httpServer = HttpServer.create(new InetSocketAddress(9093), 0);
    httpServer.createContext(
        "/api/v2/alerts", new TestHandler("[{\"labels\":{\"alertname\":\"test0\"}}]"));

    httpServer.start();

    AlertManagerConfiguration alertManagerConfiguration =
        new AlertManagerConfiguration("http://127.0.0.1:9093/api/v2/alerts");
    AlertManagerHandler alertManagerHandler = new AlertManagerHandler();

    alertManagerHandler.open(alertManagerConfiguration);

    String alertName = "test0";

    AlertManagerEvent alertManagerEvent = new AlertManagerEvent(alertName);

    alertManagerHandler.onEvent(alertManagerEvent);

    assertEquals("test0", alertManagerEvent.getLabels().get("alertname"));

    alertManagerHandler.close();

    httpServer.stop(0);
  }

  @Test
  public void alertManagerTest1() throws Exception {

    HttpServer httpServer = HttpServer.create(new InetSocketAddress(9094), 0);
    httpServer.createContext(
        "/api/v2/alerts",
        new TestHandler(
            "[{\"labels\":"
                + "{\"severity\":\"critical\","
                + "\"series\":\"root.ln.wt01.wf01.temperature\","
                + "\"alertname\":\"test1\","
                + "\"value\":\"100.0\"}}]"));

    httpServer.start();

    AlertManagerConfiguration alertManagerConfiguration =
        new AlertManagerConfiguration("http://127.0.0.1:9094/api/v2/alerts");
    AlertManagerHandler alertManagerHandler = new AlertManagerHandler();

    alertManagerHandler.open(alertManagerConfiguration);

    String alertName = "test1";

    HashMap<String, String> extraLabels = new HashMap<>();
    extraLabels.put("severity", "critical");
    extraLabels.put("series", "root.ln.wt01.wf01.temperature");
    extraLabels.put("value", String.valueOf(100.0));

    AlertManagerEvent alertManagerEvent = new AlertManagerEvent(alertName, extraLabels);

    alertManagerHandler.onEvent(alertManagerEvent);

    assertEquals("test1", alertManagerEvent.getLabels().get("alertname"));
    assertEquals("critical", alertManagerEvent.getLabels().get("severity"));
    assertEquals("root.ln.wt01.wf01.temperature", alertManagerEvent.getLabels().get("series"));
    assertEquals(String.valueOf(100.0), alertManagerEvent.getLabels().get("value"));

    alertManagerHandler.close();

    httpServer.stop(0);
  }

  @Test
  public void alertManagerTest2() throws Exception {

    HttpServer httpServer = HttpServer.create(new InetSocketAddress(9095), 0);
    httpServer.createContext(
        "/api/v2/alerts",
        new TestHandler(
            "[{\"labels\":"
                + "{\"severity\":\"critical\","
                + "\"series\":\"root.ln.wt01.wf01.temperature\","
                + "\"alertname\":\"test2\","
                + "\"value\":\"100.0\"},"
                + "\"annotations\":"
                + "{\"summary\":\"high temperature\","
                + "\"description\":\"test2: root.ln.wt01.wf01.temperature is 100.0\"}}]"));

    httpServer.start();

    AlertManagerConfiguration alertManagerConfiguration =
        new AlertManagerConfiguration("http://127.0.0.1:9095/api/v2/alerts");
    AlertManagerHandler alertManagerHandler = new AlertManagerHandler();

    alertManagerHandler.open(alertManagerConfiguration);

    String alertName = "test2";

    HashMap<String, String> extraLabels = new HashMap<>();
    extraLabels.put("severity", "critical");
    extraLabels.put("series", "root.ln.wt01.wf01.temperature");
    extraLabels.put("value", String.valueOf(100.0));

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("summary", "high temperature");
    annotations.put("description", "{{.alertname}}: {{.series}} is {{.value}}");

    AlertManagerEvent alertManagerEvent =
        new AlertManagerEvent(alertName, extraLabels, annotations);

    alertManagerHandler.onEvent(alertManagerEvent);

    assertEquals("test2", alertManagerEvent.getLabels().get("alertname"));
    assertEquals("critical", alertManagerEvent.getLabels().get("severity"));
    assertEquals("root.ln.wt01.wf01.temperature", alertManagerEvent.getLabels().get("series"));
    assertEquals(String.valueOf(100.0), alertManagerEvent.getLabels().get("value"));

    assertEquals("high temperature", alertManagerEvent.getAnnotations().get("summary"));
    assertEquals(
        "test2: root.ln.wt01.wf01.temperature is 100.0",
        alertManagerEvent.getAnnotations().get("description"));

    alertManagerHandler.close();

    httpServer.stop(0);
  }

  @Test
  @SuppressWarnings("squid:S2699")
  public void multiAlertManagerReopenTest() throws Exception {

    HttpServer httpServer = HttpServer.create(new InetSocketAddress(9096), 0);
    httpServer.createContext(
        "/api/v2/alerts",
        new TestHandler(
            "[{\"labels\":"
                + "{\"severity\":\"critical\","
                + "\"series\":\"root.ln.wt01.wf01.temperature\","
                + "\"alertname\":\"test1\","
                + "\"value\":\"100.0\"}}]"));

    httpServer.start();

    HttpServer httpServer2 = HttpServer.create(new InetSocketAddress(9097), 0);
    httpServer2.createContext(
        "/api/v2/alerts",
        new TestHandler(
            "[{\"labels\":"
                + "{\"severity\":\"critical\","
                + "\"series\":\"root.ln.wt01.wf01.temperature\","
                + "\"alertname\":\"test1\","
                + "\"value\":\"100.0\"}}]"));

    httpServer2.start();

    HttpServer httpServer3 = HttpServer.create(new InetSocketAddress(9098), 0);
    httpServer3.createContext(
        "/api/v2/alerts",
        new TestHandler(
            "[{\"labels\":"
                + "{\"severity\":\"critical\","
                + "\"series\":\"root.ln.wt01.wf01.temperature\","
                + "\"alertname\":\"test1\","
                + "\"value\":\"100.0\"}}]"));

    httpServer3.start();

    AlertManagerConfiguration alertManagerConfiguration =
        new AlertManagerConfiguration("http://127.0.0.1:9096/api/v2/alerts");

    AlertManagerConfiguration alertManagerConfiguration1 =
        new AlertManagerConfiguration("http://127.0.0.1:9097/api/v2/alerts");

    AlertManagerConfiguration alertManagerConfiguration2 =
        new AlertManagerConfiguration("http://127.0.0.1:9098/api/v2/alerts");

    String alertName = "test1";

    HashMap<String, String> extraLabels = new HashMap<>();
    extraLabels.put("severity", "critical");
    extraLabels.put("series", "root.ln.wt01.wf01.temperature");
    extraLabels.put("value", String.valueOf(100.0));

    AlertManagerEvent alertManagerEvent = new AlertManagerEvent(alertName, extraLabels);

    AlertManagerHandler alertManagerHandler = new AlertManagerHandler();
    alertManagerHandler.open(alertManagerConfiguration);
    alertManagerHandler.onEvent(alertManagerEvent);
    alertManagerHandler.close();

    AlertManagerHandler alertManagerHandler1 = new AlertManagerHandler();
    alertManagerHandler1.open(alertManagerConfiguration1);
    alertManagerHandler1.onEvent(alertManagerEvent);
    alertManagerHandler1.close();

    AlertManagerHandler alertManagerHandler2 = new AlertManagerHandler();
    alertManagerHandler2.open(alertManagerConfiguration2);

    alertManagerHandler1.open(alertManagerConfiguration1);

    alertManagerHandler1.onEvent(alertManagerEvent);

    alertManagerHandler2.onEvent(alertManagerEvent);

    alertManagerHandler2.close();

    alertManagerHandler1.close();

    httpServer.stop(0);
    httpServer2.stop(0);
    httpServer3.stop(0);
  }

  @Test
  public void alertManagerEventToJsonTest0() throws Exception {

    String alertName = "test0";

    AlertManagerEvent alertManagerEvent = new AlertManagerEvent(alertName);

    assertEquals("{\"labels\":{\"alertname\":\"test0\"}}", alertManagerEvent.toJsonString());
  }

  @Test
  public void alertManagerEventToJsonTest1() throws Exception {

    String alertName = "test1";

    HashMap<String, String> extraLabels = new HashMap<>();
    extraLabels.put("severity", "critical");
    extraLabels.put("series", "root.ln.wt01.wf01.temperature");
    extraLabels.put("value", String.valueOf(100.0));

    AlertManagerEvent alertManagerEvent = new AlertManagerEvent(alertName, extraLabels);

    assertEquals(
        "{\"labels\":"
            + "{\"severity\":\"critical\","
            + "\"series\":\"root.ln.wt01.wf01.temperature\","
            + "\"alertname\":\"test1\","
            + "\"value\":\"100.0\"}}",
        alertManagerEvent.toJsonString());
  }

  @Test
  public void alertManagerEventToJsonTest2() throws Exception {

    String alertName = "test2";

    HashMap<String, String> extraLabels = new HashMap<>();
    extraLabels.put("severity", "critical");
    extraLabels.put("series", "root.ln.wt01.wf01.temperature");
    extraLabels.put("value", String.valueOf(100.0));

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("summary", "high temperature");
    annotations.put("description", "{{.alertname}}: {{.series}} is {{.value}}");

    AlertManagerEvent alertManagerEvent =
        new AlertManagerEvent(alertName, extraLabels, annotations);

    assertEquals(
        "{\"labels\":"
            + "{\"severity\":\"critical\","
            + "\"series\":\"root.ln.wt01.wf01.temperature\","
            + "\"alertname\":\"test2\","
            + "\"value\":\"100.0\"},"
            + "\"annotations\":"
            + "{\"summary\":\"high temperature\","
            + "\"description\":\"test2: root.ln.wt01.wf01.temperature is 100.0\"}}",
        alertManagerEvent.toJsonString());
  }
}
