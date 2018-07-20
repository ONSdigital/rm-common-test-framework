package uk.gov.ons.ctp.common;

import com.google.gson.Gson;
import com.mashape.unirest.http.Unirest;

public class UnirestInitialiser {
  public static void initialise() {
    final Gson gson = new Gson();
    Unirest.setObjectMapper(
        new com.mashape.unirest.http.ObjectMapper() {
          public <T> T readValue(final String value, final Class<T> valueType) {
            return gson.fromJson(value, valueType);
          }

          public String writeValue(final Object value) {
            return gson.toJson(value);
          }
        });
  }
}
