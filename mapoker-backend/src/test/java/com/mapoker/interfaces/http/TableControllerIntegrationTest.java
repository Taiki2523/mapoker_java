package com.mapoker.interfaces.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TableControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createJoinAndListTableMembers() throws Exception {
        String createBody = """
                {
                  "table_name": "Cash Orbit Tokyo",
                  "player_count": 3,
                  "stack_size": 150,
                  "big_blind": 10,
                  "button_index": 1,
                  "visibility": "private",
                  "flags": ["casual", "newbie"]
                }
                """;

        String createResponse = mockMvc.perform(post("/v1/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cash Orbit Tokyo"))
                .andExpect(jsonPath("$.max_players").value(3))
                .andExpect(jsonPath("$.game.players.length()").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode table = objectMapper.readTree(createResponse);
        String tableId = table.get("id").asText();
        assertThat(tableId).isNotBlank();

        mockMvc.perform(post("/v1/tables/{id}/join", tableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"alice","seat_index":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].name").value("alice"))
                .andExpect(jsonPath("$.members[0].seat_index").value(1));

        mockMvc.perform(get("/v1/tables/{id}/members", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].seat_index").value(1));

        mockMvc.perform(get("/v1/rooms/{id}/members", tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].name").value("alice"));
    }

    @Test
    void tracksAuthenticatedUserHistoryAcrossJoinAndLeave() throws Exception {
        String tableId = createTable();

        mockMvc.perform(post("/v1/tables/{id}/join", tableId)
                        .with(SecurityMockMvcRequestPostProcessors.user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seat_index":1}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/auth/history")
                        .with(SecurityMockMvcRequestPostProcessors.user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].table_id").value(tableId))
                .andExpect(jsonPath("$[0].seat_index").value(1))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].left_at").doesNotExist());

        mockMvc.perform(post("/v1/tables/{id}/leave", tableId)
                        .with(SecurityMockMvcRequestPostProcessors.user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seat_index":1}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/auth/history")
                        .with(SecurityMockMvcRequestPostProcessors.user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].table_id").value(tableId))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].left_at").isString());
    }

    @Test
    void rejectsDuplicateSeatJoin() throws Exception {
        String tableId = createTable();

        mockMvc.perform(post("/v1/tables/{id}/join", tableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"alice","seat_index":0}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/tables/{id}/join", tableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"bob","seat_index":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andExpect(jsonPath("$.error.message").value("seat already taken"));
    }

    @Test
    void filtersTableListByVisibilityAndFlags() throws Exception {
        createTable("Lobby Filter Public", "public", "[\"casual\", \"newbie\"]");
        createTable("Lobby Filter Serious", "public", "[\"serious\"]");
        createTable("Lobby Filter Private", "private", "[\"casual\", \"newbie\"]");

        String response = mockMvc.perform(get("/v1/tables")
                        .param("visibility", "public")
                        .param("flags", "casual,newbie"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode tables = objectMapper.readTree(response);
        assertThat(tables.isArray()).isTrue();
        assertThat(tables.size()).isPositive();
        assertThat(tables.findValuesAsText("name"))
                .contains("Lobby Filter Public")
                .doesNotContain("Lobby Filter Serious", "Lobby Filter Private");
    }

    @Test
    void validatesCreateTableRequest() throws Exception {
        mockMvc.perform(post("/v1/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "table_name": "bad",
                                  "player_count": 1,
                                  "stack_size": 0,
                                  "big_blind": 0,
                                  "button_index": -1,
                                  "visibility": "secret",
                                  "flags": ["NotAllowed"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void validatesLoginRequest() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "ab",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));
    }

    private String createTable() throws Exception {
        return createTable("Quick Table", "public", "[\"casual\"]");
    }

    private String createTable(String tableName, String visibility, String flagsJson) throws Exception {
        String response = mockMvc.perform(post("/v1/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "table_name": "%s",
                                  "player_count": 2,
                                  "stack_size": 100,
                                  "big_blind": 10,
                                  "button_index": 0,
                                  "visibility": "%s",
                                  "flags": %s
                                }
                                """.formatted(tableName, visibility, flagsJson)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }
}
