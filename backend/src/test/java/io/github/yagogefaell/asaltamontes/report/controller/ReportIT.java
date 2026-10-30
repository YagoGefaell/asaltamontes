package io.github.yagogefaell.asaltamontes.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** T23b: filing reports. Each target type, what the reporter may see, duplicates, privacy of the reported and the schema. */
class ReportIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MessageService messageService;

    @Test
    void reportAUser_isStored_andTheAnswerSaysNothingAboutTheReported() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();

        ResultActions result = report(ana, "USER", bea.getUsername(), "HARASSMENT", "  Me escribe sin parar  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.targetType").value("USER"))
                .andExpect(jsonPath("$.reason").value("HARASSMENT"));
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        assertThat(body.has("target")).isFalse();
        assertThat(body.has("reportedUserId")).isFalse();
        assertThat(UUID.fromString(body.get("id").asText())).isNotNull();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM reports WHERE public_id = ?::uuid", body.get("id").asText());
        assertThat(row.get("reporter_id")).isEqualTo(ana.getId());
        assertThat(row.get("reported_user_id")).isEqualTo(bea.getId());
        assertThat(row.get("target_id")).isEqualTo(bea.getId());
        assertThat(row.get("details")).isEqualTo("Me escribe sin parar");
        assertThat(row.get("status")).isEqualTo("OPEN");
    }

    @Test
    void reportAPost_andItsComment_keepACopyOfTheText() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String postId = objectMapper.readTree(mockMvc.perform(post("/api/posts").with(auth.bearer(bea))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", "texto ofensivo"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String commentId = objectMapper.readTree(mockMvc.perform(post("/api/posts/" + postId + "/comments").with(auth.bearer(bea))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", "comentario ofensivo"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        report(ana, "POST", postId, "SPAM", null).andExpect(status().isCreated());
        report(ana, "COMMENT", commentId, "HATE", null).andExpect(status().isCreated());

        assertThat(snapshot("POST", postId)).isEqualTo("texto ofensivo");
        assertThat(snapshot("COMMENT", commentId)).isEqualTo("comentario ofensivo");
        assertThat(jdbc.queryForObject("SELECT reported_user_id FROM reports WHERE target_public_id = ?::uuid", Long.class, postId))
                .isEqualTo(bea.getId());
    }

    @Test
    void reportAMessage_keepsTheTextEvenIfTheMessageIsDeleted() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID chat = testData.createPrivateConversation(ana, bea);
        UUID messageId = messageService.processIncomingMessage(chat, bea.getId(), "mensaje feo").id();

        report(ana, "MESSAGE", messageId.toString(), "HARASSMENT", null).andExpect(status().isCreated());
        jdbc.update("DELETE FROM messages WHERE public_id = ?::uuid", messageId.toString());

        assertThat(snapshot("MESSAGE", messageId.toString())).isEqualTo("mensaje feo");
    }

    @Test
    void reportAMessage_ofAChatSheIsNotIn_isTheSame404AsAMissingOne() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID chat = testData.createPrivateConversation(bea, cris);
        UUID messageId = messageService.processIncomingMessage(chat, bea.getId(), "secreto").id();

        report(ana, "MESSAGE", messageId.toString(), "SPAM", null).andExpect(status().isNotFound());
        report(ana, "MESSAGE", UUID.randomUUID().toString(), "SPAM", null).andExpect(status().isNotFound());
        assertThat(count(ana)).isZero();
    }

    @Test
    void reportAMessage_sentBeforeSheJoinedAManualGroup_isNotFound() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount late = testData.createUser();
        UUID group = testData.createGroup(ana, bea);
        UUID old = messageService.processIncomingMessage(group, bea.getId(), "antiguo").id();
        Thread.sleep(50);
        mockMvc.perform(post("/api/conversations/" + group + "/participants").with(auth.bearer(ana))
                .contentType(MediaType.APPLICATION_JSON).content("{\"usernames\":[\"" + late.getUsername() + "\"]}"))
                .andExpect(status().isOk());

        report(late, "MESSAGE", old.toString(), "SPAM", null).andExpect(status().isNotFound());
        report(ana, "MESSAGE", old.toString(), "SPAM", null).andExpect(status().isCreated());
    }

    @Test
    void reportAnEvent_publicOrOfACommunity_withTheVisibilityOfTheReporter() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount outsider = testData.createUser();
        Community community = testData.createCommunity(owner);
        String publicEvent = createEvent(owner, Map.of());
        String hiddenEvent = createEvent(owner, Map.of("communityId", community.getPublicId(), "visibility", "COMMUNITY"));

        report(outsider, "EVENT", publicEvent, "DANGEROUS", null).andExpect(status().isCreated());
        report(outsider, "EVENT", hiddenEvent, "DANGEROUS", null).andExpect(status().isNotFound());
        report(owner, "EVENT", publicEvent, "SPAM", null).andExpect(status().isBadRequest());
    }

    @Test
    void reportACommunity_bySlug_notTheOnesSheCannotSee() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount ana = testData.createUser();
        Community open = testData.createCommunity(owner);
        report(ana, "COMMUNITY", open.getSlug(), "INAPPROPRIATE", null).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT target_public_id FROM reports WHERE target_type = 'COMMUNITY' AND reporter_id = ?",
                UUID.class, ana.getId())).isEqualTo(open.getPublicId());

        Community secret = testData.createCommunity(owner);
        jdbc.update("UPDATE communities SET visibility = 'PRIVATE' WHERE id = ?", secret.getId());
        report(ana, "COMMUNITY", secret.getSlug(), "INAPPROPRIATE", null).andExpect(status().isNotFound());
        report(ana, "COMMUNITY", "no-existe-" + System.nanoTime(), "INAPPROPRIATE", null).andExpect(status().isNotFound());
    }

    @Test
    void postsOfAPrivateProfile_cannotBeReportedByWhoCannotSeeThem() throws Exception {
        UserAccount priv = testData.createPrivateUser();
        UserAccount stranger = testData.createUser();
        String postId = objectMapper.readTree(mockMvc.perform(post("/api/posts").with(auth.bearer(priv))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", "solo para seguidoras"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        report(stranger, "POST", postId, "SPAM", null).andExpect(status().isNotFound());
        assertThat(count(stranger)).isZero();
    }

    @Test
    void reportingYourself_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        report(ana, "USER", ana.getUsername(), "SPAM", null).andExpect(status().isBadRequest());
    }

    @Test
    void anOpenReportPerTarget_aSecondOneIsAConflict_butAnotherReporterOrTargetIsFine() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();

        report(ana, "USER", bea.getUsername(), "SPAM", null).andExpect(status().isCreated());
        report(ana, "USER", bea.getUsername(), "HATE", null).andExpect(status().isConflict());
        report(cris, "USER", bea.getUsername(), "SPAM", null).andExpect(status().isCreated());
        report(ana, "USER", cris.getUsername(), "SPAM", null).andExpect(status().isCreated());

        // Once handled, she can report it again
        jdbc.update("UPDATE reports SET status = 'DISMISSED' WHERE reporter_id = ? AND target_id = ?", ana.getId(), bea.getId());
        report(ana, "USER", bea.getUsername(), "SPAM", null).andExpect(status().isCreated());
    }

    @Test
    void concurrentDoubleClick_leavesOneOpenReport() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                Callable<Integer> call = () -> report(ana, "USER", bea.getUsername(), "SPAM", null).andReturn().getResponse().getStatus();
                results.add(pool.submit(call));
            }
            int created = 0;
            for (Future<Integer> result : results) {
                int code = result.get();
                assertThat(code).isIn(201, 409);
                if (code == 201) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE reporter_id = ?", Integer.class, ana.getId())).isEqualTo(1);
    }

    @Test
    void validation_missingFieldsBadValuesAndOtherNeedsDetails() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();

        mockMvc.perform(post("/api/reports").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        report(ana, "USER", bea.getUsername(), "NOT_A_REASON", null).andExpect(status().isBadRequest());
        report(ana, "NOT_A_TYPE", bea.getUsername(), "SPAM", null).andExpect(status().isBadRequest());
        report(ana, "USER", bea.getUsername(), "SPAM", "x".repeat(1001)).andExpect(status().isBadRequest());
        report(ana, "USER", bea.getUsername(), "OTHER", "   ").andExpect(status().isBadRequest());
        report(ana, "POST", "no-es-un-uuid", "SPAM", null).andExpect(status().isNotFound());
        report(ana, "USER", "nadie_" + System.nanoTime(), "SPAM", null).andExpect(status().isNotFound());
        report(ana, "USER", bea.getUsername(), "OTHER", "x".repeat(1000)).andExpect(status().isCreated());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"USER\",\"target\":\"x\",\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions report(UserAccount user, String type, String target, String reason, String details) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("targetType", type);
        request.put("target", target);
        request.put("reason", reason);
        request.put("details", details);
        return mockMvc.perform(post("/api/reports").with(auth.bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }

    private String createEvent(UserAccount organizer, Map<String, Object> overrides) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Salida " + System.nanoTime());
        body.put("type", "MEETUP");
        body.put("timezone", "Europe/Madrid");
        body.put("startsAt", Instant.now().plus(7, ChronoUnit.DAYS).toString());
        body.put("meetingPointName", "Parking");
        body.putAll(overrides);
        String response = mockMvc.perform(post("/api/events").with(auth.bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String snapshot(String type, String publicId) {
        return jdbc.queryForObject("SELECT content_snapshot FROM reports WHERE target_type = ? AND target_public_id = ?::uuid",
                String.class, type, publicId);
    }

    private int count(UserAccount reporter) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE reporter_id = ?", Integer.class, reporter.getId());
    }
}
