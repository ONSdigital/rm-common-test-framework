package uk.gov.ons.ctp.common.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;

import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Test the RestClient class
 */
public class RestClientTest {

  @Mock
  Tracer tracer;

  @Mock
  Span span;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(tracer.getCurrentSpan()).thenReturn(span);
    Mockito.when(tracer.createSpan(any(String.class))).thenReturn(span);
  }

  /**
   * A test
   */
  @Test
  public void testPutResourceOk() {
    RestClient restClient = new RestClient();
    restClient.setTracer(tracer);
    RestTemplate restTemplate = restClient.getRestTemplate();

    MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
    mockServer.expect(requestTo("http://localhost:8080/hotels/42")).andExpect(method(HttpMethod.PUT))
        .andRespond(withSuccess());

    FakeDTO fakeDTO = new FakeDTO("blue", 52);
    restClient.putResource("/hotels/{hotelId}", fakeDTO, FakeDTO.class, "42");
    mockServer.verify();
  }

  /*
   * A test
   */
  @Test
  public void testPostResourceOk() {
    RestClient restClient = new RestClient();
    restClient.setTracer(tracer);
    RestTemplate restTemplate = restClient.getRestTemplate();

    MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
    mockServer.expect(requestTo("http://localhost:8080/hotels/42")).andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess());

    FakeDTO fakeDTO = new FakeDTO("blue", 52);
    restClient.postResource("/hotels/{hotelId}", fakeDTO, FakeDTO.class, "42");
    mockServer.verify();
  }

  /**
   * Test that we can call the json echo service with a very short timeout ie we
   * expect to get a timeout! this is proving that our RestClient can be
   * configured with a timeout for circuit breaker use
   */
  @Test
  public void testGetTimeoutFail() {
    RestClientConfig config = RestClientConfig.builder()
        .scheme("http")
        .host("jsontest.com")
        .port("80")
        .retryAttempts(3)
        .retryPauseMilliSeconds(2)
        .connectTimeoutMilliSeconds(1)
        .readTimeoutMilliSeconds(1)
        .build();
    RestClient restClient = new RestClient(config);
    restClient.setTracer(tracer);
    try {
      restClient.getResource("/maryhadalittlehorse", FakeDTO.class);
      fail();
    } catch (RestClientException e) {
      assertTrue(e.getMessage().contains(
              "Max retries exceeded. cause = org.apache.http.conn.ConnectTimeoutException"));
    }
  }

  /**
   * Test that we get an underlying UnknownHostException when we ask for a
   * connection to a non resolvable host
   */
  @Test
  public void testGetTimeoutURLInvalid() {
    RestClientConfig config = RestClientConfig.builder()
        .scheme("http")
        .host("phil.whiles.for.president.com")
        .port("80")
        .retryAttempts(1)
        .retryPauseMilliSeconds(100)
        .connectTimeoutMilliSeconds(1000)
        .readTimeoutMilliSeconds(1000)
        .build();
    RestClient restClient = new RestClient(config);
    restClient.setTracer(tracer);
    try {
      restClient.getResource("/hairColor/blue/shoeSize/10", FakeDTO.class);
      fail();
    } catch (RestClientException e) {
      assertTrue(e.getMessage().contains(
              "Max retries exceeded. cause = java.net.UnknownHostException"));
    }
  }

  /**
   * Test that we can call the json echo service with a very long timeout
   */
  @Test
  public void testGetTimeoutOK() {
    RestClientConfig config = RestClientConfig.builder()
        .scheme("http")
        .host("echo.jsontest.com")
        .port("80")
        .retryAttempts(3)
        .retryPauseMilliSeconds(100)
        .connectTimeoutMilliSeconds(1000)
        .readTimeoutMilliSeconds(1000)
        .build();

    RestClient restClient = new RestClient(config);
    restClient.setTracer(tracer);

    FakeDTO fakeDTOpage = restClient.getResource("/hairColor/blue/shoeSize/10", FakeDTO.class);
    assertTrue(fakeDTOpage != null);
  }

  /**
   * A test
   */
  @Test
  public void testGetResourceOk() {
    RestClient restClient = new RestClient();
    restClient.setTracer(tracer);
    RestTemplate restTemplate = restClient.getRestTemplate();

    MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
    mockServer.expect(requestTo("http://localhost:8080/hotels/42")).andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{ \"hairColor\" : \"blonde\", \"shoeSize\" : \"8\"}", MediaType.APPLICATION_JSON));

    FakeDTO fakeDTO = restClient.getResource("/hotels/{hotelId}", FakeDTO.class, "42");
    assertTrue(fakeDTO != null);
    assertTrue(fakeDTO.getHairColor().equals("blonde"));
    assertTrue(fakeDTO.getShoeSize().equals(8));
    mockServer.verify();
  }

  /**
   * A test
   */
  @Test(expected = RestClientException.class)
  public void testGetResourceReallyNotOk() {
    RestClientConfig config = RestClientConfig.builder()
        .scheme("http")
        .host("localhost")
        .port("8080")
        .retryAttempts(3)
        .retryPauseMilliSeconds(2)
        .connectTimeoutMilliSeconds(1)
        .readTimeoutMilliSeconds(1)
        .build();
    RestClient restClient = new RestClient(config);
    restClient.setTracer(tracer);
    RestTemplate restTemplate = restClient.getRestTemplate();

    MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

    for (int i = 0; i < 3; i++) {
      mockServer.expect(requestTo("http://localhost:8080/hotels/42")).andExpect(method(HttpMethod.GET))
          .andRespond(withStatus(HttpStatus.CONFLICT));
    }

    restClient.getResource("/hotels/{hotelId}", FakeDTO.class, "42");
  }

  /**
   * A test
   */
  @Test(expected = RestClientException.class)
  public void testGetResourceNotFound() {
    RestClientConfig config = RestClientConfig.builder()
        .scheme("http")
        .host("localhost")
        .port("8080")
        .retryAttempts(3)
        .retryPauseMilliSeconds(2)
        .connectTimeoutMilliSeconds(1)
        .readTimeoutMilliSeconds(1)
        .build();
    RestClient restClient = new RestClient(config);
    
    restClient.setTracer(tracer);
    RestTemplate restTemplate = restClient.getRestTemplate();

    MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
    for (int i = 0; i < 3; i++) {
      mockServer.expect(requestTo("http://localhost:8080/hotels/42")).andExpect(method(HttpMethod.GET))
          .andRespond(withStatus(HttpStatus.NOT_FOUND)
              .body("{ \"error\" :{  \"code\" : \"123\", \"message\" : \"123\", \"timestamp\" : \"123\"}}"));
    }

    FakeDTO fakeDTO = restClient.getResource("/hotels/{hotelId}", FakeDTO.class, "42");
    assertTrue(fakeDTO == null);
    mockServer.verify();
  }

  /**
   * A test
   */
  @Test
  public void testGetResourcesOk() {
    RestClient restClient = new RestClient();
    restClient.setTracer(tracer);
    RestTemplate restTemplate = restClient.getRestTemplate();

    MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
    mockServer.expect(requestTo("http://localhost:8080/hotels")).andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(
            "[{ \"hairColor\" : \"blonde\", \"shoeSize\" : \"8\"},{ \"hairColor\" : \"brown\", \"shoeSize\" : \"12\"}]",
            MediaType.APPLICATION_JSON));

    List<FakeDTO> fakeDTOs = restClient.getResources("/hotels", FakeDTO[].class);
    assertTrue(fakeDTOs != null);
    assertTrue(fakeDTOs.size() == 2);
    mockServer.verify();
  }

  /**
   * A test
   */
  @Test
  public void testGetResourcesNoContent() {
    RestClient restClient = new RestClient();
    restClient.setTracer(tracer);
    RestTemplate restTemplate = restClient.getRestTemplate();

    MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
    mockServer.expect(requestTo("http://localhost:8080/hotels")).andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    List<FakeDTO> fakeDTOs = restClient.getResources("/hotels", FakeDTO[].class);
    assertTrue(fakeDTOs != null);
    assertTrue(fakeDTOs.size() == 0);
    mockServer.verify();
  }

  /**
   * A test
   */
  @Test(expected = RestClientException.class)
  public void testGetResourcesReallyNotOk() {
    RestClientConfig config = RestClientConfig.builder()
        .scheme("http")
        .host("localhost")
        .port("8080")
        .retryAttempts(3)
        .retryPauseMilliSeconds(2)
        .connectTimeoutMilliSeconds(1)
        .readTimeoutMilliSeconds(1)
        .build();
    RestClient restClient = new RestClient(config);
    restClient.setTracer(tracer);
    RestTemplate restTemplate = restClient.getRestTemplate();

    MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
    for (int i = 0; i < 3; i++) {
      mockServer.expect(requestTo("http://localhost:8080/hotels")).andExpect(method(HttpMethod.GET))
          .andRespond(withStatus(HttpStatus.BAD_REQUEST));
    }

    restClient.getResources("/hotels", FakeDTO[].class);
  } 
  
  /**
   * A test
   */
  @Test(expected = RestClientException.class)
  public void testGetResourceUnauthorized() {
    RestClientConfig config = RestClientConfig.builder()
        .scheme("http")
        .host("localhost")
        .port("8080")
        .retryAttempts(3)
        .retryPauseMilliSeconds(2)
        .connectTimeoutMilliSeconds(1)
        .readTimeoutMilliSeconds(1)
        .build();
    RestClient restClient = new RestClient(config);
    restClient.setTracer(tracer);
    RestTemplate restTemplate = restClient.getRestTemplate();

    MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
      mockServer.expect(requestTo("http://localhost:8080/hotels")).andExpect(method(HttpMethod.GET))
          .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    restClient.getResources("/hotels", FakeDTO[].class);
  }

  /**
   * A Test
   */
  @Test
  public void testUriJsonParamNotEncoded() {

    RestClientConfig config = RestClientConfig.builder()
            .scheme("https")
            .host("api-dev.apps.mvp.onsclofo.uk")
            .port("443")
            .retryAttempts(3)
            .retryPauseMilliSeconds(2)
            .connectTimeoutMilliSeconds(1)
            .readTimeoutMilliSeconds(1)
            .build();
    RestClient restClient = new RestClient(config);
    restClient.setTracer(tracer);

    String path = "collection-instrument-api/1.0.2/collectioninstrument";
    MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
    //queryParams.add("searchString", "{\"RU_REF\":\"0123456789\"}");
    queryParams.add("searchString", "{searchStringValue}");

    String uriParams = "{\"RU_REF\":\"0123456789\"}";

    UriComponents uriComponents = restClient.createUriComponentsWithJsonParam(path, queryParams, uriParams);
    System.out.println(uriComponents.toString());

  }

}
