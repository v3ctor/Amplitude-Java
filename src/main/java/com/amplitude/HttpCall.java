package com.amplitude;

import com.amplitude.exception.AmplitudeInvalidAPIKeyException;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.List;

enum HttpCallMode {
  REGULAR_HTTPCALL,
  BATCH_HTTPCALL
}

public abstract class HttpCall {
  /*
   * Use HTTPUrlConnection object to make async HTTP request,
   * using data from event like device, class name, event props, etc.
   *
   * @return The response object which contains a code and other information
   */
  private String apiKey;
  private String serverUrl;

  protected HttpCall(String apiKey, String serverUrl) {
    this.apiKey = apiKey;
    this.serverUrl = serverUrl;
  }

  protected String getApiUrl() {
    return this.serverUrl;
  }

  protected Response syncHttpCallWithEventsBuffer(List<Event> events)
      throws AmplitudeInvalidAPIKeyException {
    String apiUrl = getApiUrl();
    HttpsURLConnection connection;
    InputStream inputStream = null;
    int responseCode = 500;
    Response responseBody = new Response();
    try {
      connection = (HttpsURLConnection) new URL(apiUrl).openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Accept", "application/json");
      connection.setConnectTimeout(Constants.NETWORK_TIMEOUT_MILLIS);
      connection.setReadTimeout(Constants.NETWORK_TIMEOUT_MILLIS);
      connection.setDoOutput(true);

      JSONObject bodyJson = new JSONObject();
      bodyJson.put("api_key", this.apiKey);

      JSONArray eventsArr = new JSONArray();
      for (int i = 0; i < events.size(); i++) {
        eventsArr.put(i, events.get(i).toJsonObject());
      }
      bodyJson.put("events", eventsArr);

      String bodyString = bodyJson.toString();
      OutputStream os = connection.getOutputStream();
      byte[] input = bodyString.getBytes("UTF-8");
      os.write(input, 0, input.length);

      responseCode = connection.getResponseCode();
      boolean isErrorCode = responseCode >= Constants.HTTP_STATUS_BAD_REQ;
      if (!isErrorCode) {
        inputStream = connection.getInputStream();
      } else {
        inputStream = connection.getErrorStream();
      }

      BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
      StringBuilder sb = new StringBuilder();
      String output;
      while ((output = br.readLine()) != null) {
        sb.append(output);
      }
      JSONObject responseJson = new JSONObject(sb.toString());
      responseBody = Response.populateResponse(responseJson);
    } catch (IOException e) {
      // This handles UnknownHostException, when the SDK has no internet.
      // Also SocketTimeoutException, when the HTTP request times out.
      JSONObject timesOutResponse = new JSONObject();
      timesOutResponse.put("status", Status.TIMEOUT);
      timesOutResponse.put("code", 408);
      responseBody = Response.populateResponse(timesOutResponse);
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
        }
      }
    }
    return responseBody;
  }
}
