package hse.java.restcountries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("REST Countries API")
class RestCountriesTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String BASE_URL = "https://restcountries.com/v3.1";
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
	private static final String JSON_HEADER = "application/json";

	private HttpClient httpClient;

	@BeforeAll
	void setUpClient() {
		httpClient = HttpClient.newBuilder()
				.connectTimeout(CONNECT_TIMEOUT)
				.build();
	}

	@Test
	@Tag("rest-countries-tests-1")
	@DisplayName("GET /all возвращает 200 и непустой список стран")
	void getAllReturnsNonEmptyCountryList() throws Exception {
		String path = "/all?fields=name";
		ApiResponse response = get(path);
		List<JsonNode> countries = elements(response.body());
		int countriesCount = countries.size();

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body().isArray()).isTrue();
		assertThat(0 < countriesCount).isTrue();
	}

	@Test
	@Tag("rest-countries-tests-1")
	@DisplayName("GET /name/russia возвращает страну со столицей Moscow")
	void getNameRussiaContainsMoscowCapital() throws Exception {
		String path = "/name/russia";
		ApiResponse response = get(path);
		List<JsonNode> countries = elements(response.body());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body().isArray()).isTrue();
		assertThat(countries)
				.anySatisfy(country -> {
					List<JsonNode> capitals = elements(country.path("capital"));
					assertThat(capitals)
							.extracting(JsonNode::asText)
							.contains("Moscow");
				});
	}

	@Test
	@Tag("rest-countries-tests-1")
	@DisplayName("GET /alpha/de возвращает Germany как common name")
	void getAlphaDeReturnsGermany() throws Exception {
		String path = "/alpha/de";
		ApiResponse response = get(path);
		List<JsonNode> countries = elements(response.body());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body().isArray()).isTrue();
		assertThat(countries)
				.anySatisfy(country -> {
					String commonName = country.path("name").path("common").asText();
					assertThat(commonName).isEqualTo("Germany");
				});
	}

	@Test
	@Tag("rest-countries-tests-1")
	@DisplayName("GET /name/nonexistentcountryxyz возвращает 404")
	void getUnknownCountryReturns404() throws Exception {
		String path = "/name/nonexistentcountryxyz";
		ApiResponse response = get(path);

		assertThat(response.statusCode()).isEqualTo(404);
	}

	@Test
	@Tag("rest-countries-tests-1")
	@DisplayName("GET /all возвращает population для всех стран и оно неотрицательное")
	void getAllReturnsNonNegativePopulationForAllCountries() throws Exception {
		String path = "/all?fields=name,population";
		ApiResponse response = get(path);
		List<JsonNode> countries = elements(response.body());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body().isArray()).isTrue();
		assertThat(0 < countries.size()).isTrue();
		assertThat(countries)
				.allSatisfy(country -> {
					long population = country.path("population").asLong();
					assertThat(0L <= population).isTrue();
				});
	}

	@Test
	@Tag("rest-countries-tests-2")
	@DisplayName("GET /name/canada возвращает страну со столицей Ottawa и регионом Americas")
	void getNameCanadaReturnsOttawaInAmericas() throws Exception {
		String path = "/name/canada";
		ApiResponse response = get(path);
		List<JsonNode> countries = elements(response.body());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body().isArray()).isTrue();
		assertThat(countries)
				.anySatisfy(country -> {
					List<JsonNode> capitals = elements(country.path("capital"));
					String region = country.path("region").asText();
					assertThat(capitals)
							.extracting(JsonNode::asText)
							.contains("Ottawa");
					assertThat(region).isEqualTo("Americas");
				});
	}

	@Test
	@Tag("rest-countries-tests-2")
	@DisplayName("GET /alpha/jp возвращает Japan со столицей Tokyo")
	void getAlphaJpReturnsJapanWithTokyoCapital() throws Exception {
		String path = "/alpha/jp";
		ApiResponse response = get(path);
		List<JsonNode> countries = elements(response.body());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body().isArray()).isTrue();
		assertThat(countries)
				.anySatisfy(country -> {
					String commonName = country.path("name").path("common").asText();
					List<JsonNode> capitals = elements(country.path("capital"));
					assertThat(commonName).isEqualTo("Japan");
					assertThat(capitals)
							.extracting(JsonNode::asText)
							.contains("Tokyo");
				});
	}

	private ApiResponse get(String path) throws IOException, InterruptedException {
		String fullPath = BASE_URL + path;
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(fullPath))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", JSON_HEADER)
				.GET()
				.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		String responseBody = response.body();
		boolean bodyIsBlank = responseBody.isBlank();
		JsonNode body;

		if (bodyIsBlank) {
			body = OBJECT_MAPPER.nullNode();
		} else {
			body = OBJECT_MAPPER.readTree(responseBody);
		}

		return new ApiResponse(response.statusCode(), body);
	}

	private List<JsonNode> elements(JsonNode arrayNode) {
		return StreamSupport.stream(arrayNode.spliterator(), false).toList();
	}

	private record ApiResponse(int statusCode, JsonNode body) {
	}
}
