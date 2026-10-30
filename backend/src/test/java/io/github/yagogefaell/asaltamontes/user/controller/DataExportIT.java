package io.github.yagogefaell.asaltamontes.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.export.DataExportService;

/** T24d: "download my data". Only her own data, no internal ids, bounded size and a strict limit. */
@TestPropertySource(properties = {
        "asaltamontes.rate-limit.enabled=true",
        "asaltamontes.rate-limit.data-exports-per-day=2"})
class DataExportIT extends AbstractIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MessageService messageService;

    /** A real HTTP call: the body is streamed on an async dispatch, which MockMvc does not run through the security filters. */
    private HttpResponse<String> call(UserAccount user) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/me/export"));
        if (user != null) {
            request.header("Authorization", "Bearer " + auth.loginAs(user));
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode export(UserAccount user) throws Exception {
        HttpResponse<String> response = call(user);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Disposition")).get().asString().startsWith("attachment");
        assertThat(response.headers().firstValue("Cache-Control")).get().asString().contains("no-store");
        return objectMapper.readTree(response.body());
    }

    private String createPost(UserAccount author, String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/posts").with(auth.bearer(author))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", body))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void exportContainsOnlyHerOwnData_andNoInternalIds() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String anaPost = createPost(ana, "post de ana");
        String beaPost = createPost(bea, "post secreto de bea");
        mockMvc.perform(post("/api/posts/" + beaPost + "/comments").with(auth.bearer(ana))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", "comentario de ana")))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/posts/" + anaPost + "/comments").with(auth.bearer(bea))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", "comentario de bea")))).andExpect(status().isCreated());
        mockMvc.perform(put("/api/posts/" + beaPost + "/like").with(auth.bearer(ana))).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/follow/{u}", bea.getUsername()).with(auth.bearer(ana))).andExpect(status().isOk());
        UUID group = testData.createGroup(ana, bea);
        messageService.processIncomingMessage(group, ana.getId(), "mensaje de ana");
        messageService.processIncomingMessage(group, bea.getId(), "mensaje de bea");

        JsonNode json = export(ana);
        String text = json.toString();

        assertThat(json.get("format").asText()).isEqualTo("asaltamontes-export-v1");
        assertThat(json.get("account").get("username").asText()).isEqualTo(ana.getUsername());
        assertThat(json.get("account").get("email").asText()).isEqualTo(ana.getEmail());
        assertThat(json.get("posts")).hasSize(1);
        assertThat(json.get("posts").get(0).get("body").asText()).isEqualTo("post de ana");
        assertThat(json.get("comments")).hasSize(1);
        assertThat(json.get("comments").get(0).get("body").asText()).isEqualTo("comentario de ana");
        assertThat(json.get("comments").get(0).get("postId").asText()).isEqualTo(beaPost);
        assertThat(json.get("likes")).hasSize(1);
        assertThat(json.get("messages")).hasSize(1);
        assertThat(json.get("messages").get(0).get("content").asText()).isEqualTo("mensaje de ana");
        assertThat(json.get("following")).hasSize(1);
        assertThat(json.get("following").get(0).get("username").asText()).isEqualTo(bea.getUsername());
        assertThat(json.get("truncatedSections")).isEmpty();

        // nothing written by someone else, and no credentials
        assertThat(text).doesNotContain("post secreto de bea", "comentario de bea", "mensaje de bea", bea.getEmail());
        assertThat(text).doesNotContain("passwordHash", "password_hash", "$2a$");
        // D-021: every id-like field is a UUID, never a bigint
        List<String> numericIds = new ArrayList<>();
        collectNumericIds(json, numericIds);
        assertThat(numericIds).isEmpty();
    }

    @Test
    void exportOfOneUser_neverContainsTheOthers() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        createPost(bea, "post de bea");
        JsonNode json = export(ana);
        assertThat(json.get("posts")).isEmpty();
        assertThat(json.toString()).doesNotContain("post de bea", bea.getEmail());
        assertThat(json.get("account").get("username").asText()).isEqualTo(ana.getUsername());
    }

    @Test
    void exportRequiresAuthentication() throws Exception {
        assertThat(call(null).statusCode()).isEqualTo(401);
    }

    @Test
    void exportIsLimitedPerUser() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        export(ana);
        export(ana);
        HttpResponse<String> limited = call(ana);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        // the limit is hers, not global
        export(bea);
    }

    @Test
    void longListsAreCutAtTheCap_andNamed() throws Exception {
        UserAccount ana = testData.createUser();
        for (int i = 0; i < 5; i++) {
            createPost(ana, "post " + i);
        }
        DataExportService small = new DataExportService(jdbc, objectMapper, 3);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        small.write(ana.getId(), out);
        JsonNode json = objectMapper.readTree(out.toByteArray());
        assertThat(json.get("posts")).hasSize(3);
        assertThat(json.get("truncatedSections")).extracting(JsonNode::asText).containsExactly("posts");

        // exactly at the cap is not truncated
        DataExportService exact = new DataExportService(jdbc, objectMapper, 5);
        out.reset();
        exact.write(ana.getId(), out);
        json = objectMapper.readTree(out.toByteArray());
        assertThat(json.get("posts")).hasSize(5);
        assertThat(json.get("truncatedSections")).isEmpty();
    }

    @Test
    void paginatesAcrossPages() throws Exception {
        UserAccount ana = testData.createUser();
        jdbc.update("""
                INSERT INTO posts (public_id, author_id, body)
                SELECT gen_random_uuid(), ?, 'p' || g FROM generate_series(1, 1203) g""", ana.getId());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new DataExportService(jdbc, objectMapper, 50_000).write(ana.getId(), out);
        JsonNode json = objectMapper.readTree(out.toByteArray());
        assertThat(json.get("posts")).hasSize(1203);
        assertThat(json.get("posts").findValuesAsText("id")).doesNotHaveDuplicates();
    }

    private void collectNumericIds(JsonNode node, List<String> found) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                String name = e.getKey();
                if ((name.equals("id") || name.endsWith("Id")) && e.getValue().isNumber()) {
                    found.add(name);
                }
                collectNumericIds(e.getValue(), found);
            });
        } else if (node.isArray()) {
            node.forEach(n -> collectNumericIds(n, found));
        }
    }
}
