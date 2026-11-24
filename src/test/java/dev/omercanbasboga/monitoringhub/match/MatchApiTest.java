package dev.omercanbasboga.monitoringhub.match;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omercanbasboga.monitoringhub.demo.DemoFeed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots with the demo feed and walks the match API end to end. */
@SpringBootTest(properties = {"matches.scan.initial-delay=PT1H", "cache.warmup.initial-delay=PT1H"})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class MatchApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FailoverService failover;
    @Autowired DemoFeed demo;

    private JsonNode getJson(String url) throws Exception {
        return json.readTree(mvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String matchId(String name) throws Exception {
        for (JsonNode m : getJson("/api/matches")) {
            if (m.get("name").asText().equals(name)) return m.get("id").asText();
        }
        throw new AssertionError("no match " + name);
    }

    @Test
    void primaryOutageIsCoveredByTheBackupAndLogged() throws Exception {
        failover.scan(Duration.ofHours(24));
        String id = matchId("Piraeus"); // primary has been down for the last 95 minutes

        JsonNode series = getJson("/api/matches/" + id + "/series/last-24h");
        Set<String> sides = new HashSet<>();
        series.forEach(p -> sides.add(p.get("side").asText()));
        assertThat(sides).containsExactlyInAnyOrder("PRIMARY", "SECONDARY");
        assertThat(series.get(series.size() - 1).get("side").asText()).isEqualTo("SECONDARY");

        JsonNode gaps = getJson("/api/matches/" + id + "/gaps");
        assertThat(gaps.size()).isGreaterThan(80);

        JsonNode match = getJson("/api/matches/" + id);
        assertThat(match.get("activeSide").asText()).isEqualTo("SECONDARY");
        assertThat(getJson("/api/matches/" + id + "/usage").get(0).get("side").asText()).isEqualTo("SECONDARY");
    }

    @Autowired MatchGapRepository gapRows;

    @Test
    void scanningTwiceDoesNotLogTheSameOutageAgain() {
        failover.scan(Duration.ofHours(24));
        long first = gapRows.count();
        failover.scan(Duration.ofHours(24));
        assertThat(first).isPositive();
        assertThat(gapRows.count()).isEqualTo(first);
    }

    @Test
    void mergedSeriesRangeIsCappedAt31Days() throws Exception {
        mvc.perform(get("/api/matches/" + matchId("Split") + "/series")
                        .param("start", "2026-01-01T00:00:00Z").param("end", "2026-03-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bothSidesDownMeansNone() throws Exception {
        failover.scan(Duration.ofHours(24));
        assertThat(getJson("/api/matches/" + matchId("Alicante")).get("activeSide").asText()).isEqualTo("NONE");
    }

    @Test
    void matchNeedsTwoDifferentStations() throws Exception {
        JsonNode m = getJson("/api/matches/" + matchId("Split"));
        String p = m.get("primary").get("id").asText();
        mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"primaryStationId\":\"" + p + "\",\"secondaryStationId\":\"" + p + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void v2HistoryIsPagedNewestFirst() throws Exception {
        JsonNode page = getJson("/api/v2/sources/CSV_FEED/stations/split-p/readings/period?period=DAILY&size=50");
        assertThat(page.get("content").size()).isEqualTo(50);
        assertThat(page.get("totalElements").asLong()).isGreaterThan(1000);
        String first = page.get("content").get(0).get("timestamp").asText();
        String second = page.get("content").get(1).get("timestamp").asText();
        assertThat(first.compareTo(second)).isPositive();

        mvc.perform(get("/api/v2/sources/CSV_FEED/stations/split-p/readings")
                        .param("start", "2024-01-01T00:00:00Z").param("end", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
