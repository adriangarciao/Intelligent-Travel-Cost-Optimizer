package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.dto.SavedTripDTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension;
import com.adriangarciao.traveloptimizer.test.support.WireMockMlServerExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-no-security")
@Testcontainers
@ExtendWith({ThreadLeakDetectorExtension.class})
public class SavedIntegrationTest {

    static WireMockMlServerExtension WMEXT = new WireMockMlServerExtension();

    @org.junit.jupiter.api.extension.RegisterExtension
    static WireMockMlServerExtension registeredWireMock = WMEXT;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("travelassistant")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> Integer.toString(redis.getMappedPort(6379)));
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> Integer.toString(redis.getMappedPort(6379)));
        registry.add("ml.service.base-url", () -> WMEXT.getServer().baseUrl());
    }

    @LocalServerPort
    private int port;

    @BeforeAll
    static void beforeAll() {
        // no-op
    }

    @Test
    void saveListDeleteRecentFlow() {
        RestTemplate rest = new RestTemplate();

        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO")
                .destination("JFK")
                .earliestDepartureDate(LocalDate.now().plusDays(7))
                .latestDepartureDate(LocalDate.now().plusDays(9))
                .maxBudget(BigDecimal.valueOf(2000))
                .numTravelers(1)
                .build();

        var resp = rest.postForEntity("http://localhost:" + port + "/api/trips/search", req, TripSearchResponseDTO.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        TripSearchResponseDTO body = resp.getBody();
        assertThat(body).isNotNull();
        UUID searchId = body.getSearchId();

        // Save one of the options
        SavedTripDTO toSave = SavedTripDTO.builder()
                .searchId(searchId)
                .tripOptionId(body.getOptions().get(0).getTripOptionId())
                .origin(body.getOrigin())
                .destination(body.getDestination())
                .totalPrice(body.getOptions().get(0).getTotalPrice())
                .currency(body.getCurrency())
                .airline(body.getOptions().get(0).getFlight()!=null?body.getOptions().get(0).getFlight().getAirline():null)
                .hotelName(body.getOptions().get(0).getLodging()!=null?body.getOptions().get(0).getLodging().getHotelName():null)
                .valueScore(body.getOptions().get(0).getValueScore())
                .build();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Client-Id", "test-client-1");
        org.springframework.http.HttpEntity<SavedTripDTO> entity = new org.springframework.http.HttpEntity<>(toSave, headers);
        var saveResp = rest.postForEntity("http://localhost:" + port + "/api/saved", entity, String.class);
        assertThat(saveResp.getStatusCode().value()).isEqualTo(201);

        // list
        org.springframework.http.HttpEntity<Void> listEntity = new org.springframework.http.HttpEntity<>(headers);
        var listResp = rest.exchange("http://localhost:" + port + "/api/saved", org.springframework.http.HttpMethod.GET, listEntity, SavedTripDTO[].class);
        assertThat(listResp.getStatusCode().is2xxSuccessful()).isTrue();
        SavedTripDTO[] saved = listResp.getBody();
        assertThat(saved).isNotNull();
        assertThat(saved.length).isGreaterThanOrEqualTo(1);

        UUID savedId = saved[0].getId();

        // delete
        var delResp = rest.exchange("http://localhost:" + port + "/api/saved/" + savedId, org.springframework.http.HttpMethod.DELETE, listEntity, Void.class);
        assertThat(delResp.getStatusCode().is2xxSuccessful()).isTrue();

        // recent searches
        var recentResp = rest.getForEntity("http://localhost:" + port + "/api/trips/recent?limit=5", List.class);
        assertThat(recentResp.getStatusCode().is2xxSuccessful()).isTrue();
        List<?> recs = recentResp.getBody();
        assertThat(recs).isNotNull();
        assertThat(recs.size()).isGreaterThanOrEqualTo(1);
    }
}
