package it.intesigroup.ums.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.intesigroup.ums.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = {"OWNER","MAINTAINER"})
class UserApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AmqpTemplate amqpTemplate;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
    }

    @Test
    void createUser_returns201_andPersistsUser() throws Exception {
        String body = """
            {
              "username": "mrossi",
              "email": "m.rossi@example.com",
              "codiceFiscale": "RSSMRA80A01H501U",
              "nome": "Mario",
              "cognome": "Rossi",
              "roles": ["DEVELOPER"]
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("m.rossi@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("DEVELOPER"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        String id = node.get("id").asText();

        assertThat(userRepository.findById(java.util.UUID.fromString(id))).isPresent();
    }

    @Test
    void createUser_withInvalidPayload_returns422() throws Exception {
        String body = """
            {
              "username": "",
              "email": "not-an-email",
              "codiceFiscale": "INVALID",
              "nome": "",
              "cognome": "",
              "roles": []
            }
            """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Input non valido"))
                .andExpect(jsonPath("$.details.username").exists())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.codiceFiscale").exists());
    }

    @Test
    void createUser_withDuplicateEmail_returns409() throws Exception {
        String body = """
            {
              "username": "mrossi",
              "email": "m.rossi@example.com",
              "codiceFiscale": "RSSMRA80A01H501U",
              "nome": "Mario",
              "cognome": "Rossi",
              "roles": ["DEVELOPER"]
            }
            """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void disableAndSoftDelete_flow_worksAsExpected() throws Exception {
        String body = """
            {
              "username": "mrossi",
              "email": "m.rossi@example.com",
              "codiceFiscale": "RSSMRA80A01H501U",
              "nome": "Mario",
              "cognome": "Rossi",
              "roles": ["DEVELOPER"]
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String json = createResult.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        String id = node.get("id").asText();

        mockMvc.perform(post("/api/users/{id}/disable", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(delete("/api/users/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void listUsers_returnsPagedResult() throws Exception {
        String body = """
            {
              "username": "mrossi",
              "email": "m.rossi@example.com",
              "codiceFiscale": "RSSMRA80A01H501U",
              "nome": "Mario",
              "cognome": "Rossi",
              "roles": ["DEVELOPER"]
            }
            """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/users?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("mrossi"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }
}
