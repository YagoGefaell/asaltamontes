package io.github.yagogefaell.asaltamontes.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.admin.maintenance.ReportRetentionService;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** T23c1: the moderation queue, who may use it, its actions, audit and the purge of the copied text. */
class AdminReportIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MessageService messageService;
    @Autowired private ReportRetentionService retention;

    // ---------------------------------------------------------------- who gets in

    @Test
    void aUser_isForbidden_everywhereInThePanel_andAnonymousIs401() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String report = fileReport(ana, "USER", bea.getUsername());
        String any = UUID.randomUUID().toString();
        int auditBefore = audit();

        mockMvc.perform(get("/api/admin/reports").with(auth.bearer(ana))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports/" + report).with(auth.bearer(ana))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/reports/" + any + "/dismiss").with(auth.bearer(ana))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/reports/" + any + "/remove-content").with(auth.bearer(ana))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
        assertThat(audit()).isEqualTo(auditBefore);
    }

    @Test
    void theRoleIsReadFromTheDatabase_soDemotingTakesEffectAtOnce() throws Exception {
        UserAccount mod = staff("MODERATOR");
        mockMvc.perform(get("/api/admin/reports").with(auth.bearer(mod))).andExpect(status().isOk());
        jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", mod.getId());
        mockMvc.perform(get("/api/admin/reports").with(auth.bearer(mod))).andExpect(status().isForbidden());
    }

    @Test
    void thereIsNoEndpointToChangeRoles_andProfileUpdatesIgnoreTheRole() throws Exception {
        UserAccount ana = testData.createUser();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/users/me").with(auth.bearer(ana))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"bio\":\"hola\"}"));
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, ana.getId())).isEqualTo("USER");
    }

    // ---------------------------------------------------------------- queue and detail

    @Test
    void theQueue_listsOpenOldestFirst_withCursor_andFilters() throws Exception {
        UserAccount mod = staff("MODERATOR");
        // The database is shared with the other tests: this queue starts empty
        jdbc.update("UPDATE reports SET status = 'DISMISSED', handled_at = now() WHERE status = 'OPEN'");
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        String first = fileReport(ana, "USER", bea.getUsername());
        String second = fileReport(ana, "USER", cris.getUsername());
        String post = createPost(bea, "texto");
        String third = fileReport(cris, "POST", post);

        JsonNode page1 = json(mockMvc.perform(get("/api/admin/reports?status=OPEN&limit=2").with(auth.bearer(mod))).andExpect(status().isOk()));
        assertThat(page1.get("items")).hasSize(2);
        assertThat(page1.get("items").get(0).get("id").asText()).isEqualTo(first);
        assertThat(page1.get("items").get(1).get("id").asText()).isEqualTo(second);
        assertThat(page1.get("nextBefore").asText()).isNotBlank();

        JsonNode page2 = json(mockMvc.perform(get("/api/admin/reports?status=OPEN&limit=2&before=" + page1.get("nextBefore").asText())
                .with(auth.bearer(mod))).andExpect(status().isOk()));
        assertThat(page2.get("items")).hasSize(1);
        assertThat(page2.get("items").get(0).get("id").asText()).isEqualTo(third);
        assertThat(page2.get("nextBefore").isNull()).isTrue();

        JsonNode posts = json(mockMvc.perform(get("/api/admin/reports?status=OPEN&type=POST").with(auth.bearer(mod))).andExpect(status().isOk()));
        assertThat(posts.get("items")).hasSize(1);
        mockMvc.perform(get("/api/admin/reports?before=basura").with(auth.bearer(mod))).andExpect(status().isBadRequest());
    }

    @Test
    void theDetail_showsTheContentAndWhoIsInvolved_andNoInternalIds() throws Exception {
        UserAccount mod = staff("ADMIN");
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String post = createPost(bea, "texto ofensivo <script>alert(1)</script>");
        String report = fileReport(ana, "POST", post, "HATE", "porque sí");
        fileReport(testData.createUser(), "POST", post);

        ResultActions result = mockMvc.perform(get("/api/admin/reports/" + report).with(auth.bearer(mod))).andExpect(status().isOk())
                .andExpect(jsonPath("$.targetType").value("POST"))
                .andExpect(jsonPath("$.target").value(post))
                .andExpect(jsonPath("$.reason").value("HATE"))
                .andExpect(jsonPath("$.details").value("porque sí"))
                .andExpect(jsonPath("$.contentSnapshot").value("texto ofensivo <script>alert(1)</script>"))
                .andExpect(jsonPath("$.reporterUsername").value(ana.getUsername()))
                .andExpect(jsonPath("$.reportedUsername").value(bea.getUsername()))
                .andExpect(jsonPath("$.openReportsOnTarget").value(2))
                .andExpect(jsonPath("$.contentAvailable").value(true));
        JsonNode body = json(result);
        assertThat(body.has("reporterId")).isFalse();
        assertThat(body.has("targetId")).isFalse();
        assertThat(body.has("handledBy")).isFalse();
    }

    @Test
    void unknownReport_is404() throws Exception {
        UserAccount mod = staff("MODERATOR");
        mockMvc.perform(get("/api/admin/reports/" + UUID.randomUUID()).with(auth.bearer(mod))).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- dismiss

    @Test
    void dismiss_closesOnlyThatReport_audits_andBlocksTheSameReporterForAWhile() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String post = createPost(bea, "dudoso");
        String mine = fileReport(ana, "POST", post);
        String other = fileReport(testData.createUser(), "POST", post);

        mockMvc.perform(post("/api/admin/reports/" + mine + "/dismiss").with(auth.bearer(mod)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"  sin motivo  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.handledByUsername").value(mod.getUsername()))
                .andExpect(jsonPath("$.resolution").value("sin motivo"));
        assertThat(statusOf(other)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE actor_id = ? AND action = 'REPORT_DISMISSED' AND note = 'sin motivo'",
                Integer.class, mod.getId())).isEqualTo(1);

        mockMvc.perform(post("/api/admin/reports/" + mine + "/dismiss").with(auth.bearer(mod))).andExpect(status().isConflict());
        assertThat(audit(mod)).isEqualTo(1);

        // The cooldown: she cannot file the same content again, somebody else can
        reportRequest(ana, "POST", post, "SPAM", null).andExpect(status().isConflict());
        reportRequest(testData.createUser(), "POST", post, "SPAM", null).andExpect(status().isCreated());
    }

    @Test
    void dismissedReportOfAnAccount_doesNotBlockReportingAgain() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        String report = fileReport(ana, "USER", bea.getUsername());
        mockMvc.perform(post("/api/admin/reports/" + report + "/dismiss").with(auth.bearer(mod))).andExpect(status().isOk());
        reportRequest(ana, "USER", bea.getUsername(), "HARASSMENT", null).andExpect(status().isCreated());
    }

    @Test
    void staffCannotResolveReportsAboutThemselves() throws Exception {
        UserAccount mod = staff("MODERATOR");
        String post = createPost(mod, "mi post");
        String report = fileReport(testData.createUser(), "POST", post);
        mockMvc.perform(post("/api/admin/reports/" + report + "/dismiss").with(auth.bearer(mod))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/reports/" + report + "/remove-content").with(auth.bearer(mod))).andExpect(status().isForbidden());
        assertThat(statusOf(report)).isEqualTo("OPEN");
        // Another member of the staff can
        mockMvc.perform(post("/api/admin/reports/" + report + "/dismiss").with(auth.bearer(staff("ADMIN")))).andExpect(status().isOk());
    }

    @Test
    void removeContentAlreadyGone_isAConflict_andNothingIsAudited() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount bea = testData.createUser();
        String post = createPost(bea, "efimero");
        String report = fileReport(testData.createUser(), "POST", post);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/posts/" + post).with(auth.bearer(bea)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/reports/" + report + "/remove-content").with(auth.bearer(mod))).andExpect(status().isConflict());
        assertThat(statusOf(report)).isEqualTo("OPEN");
        assertThat(audit(mod)).isZero();
    }

    @Test
    void dismissWithoutBody_isAccepted() throws Exception {
        UserAccount mod = staff("MODERATOR");
        String report = fileReport(testData.createUser(), "USER", testData.createUser().getUsername());
        mockMvc.perform(post("/api/admin/reports/" + report + "/dismiss").with(auth.bearer(mod))).andExpect(status().isOk());
        assertThat(statusOf(report)).isEqualTo("DISMISSED");
    }

    // ---------------------------------------------------------------- remove content

    @Test
    void removeAPost_deletesIt_closesAllItsOpenReports_andAudits() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount bea = testData.createUser();
        UserAccount ana = testData.createUser();
        String post = createPost(bea, "malo");
        String r1 = fileReport(ana, "POST", post);
        String r2 = fileReport(testData.createUser(), "POST", post);

        mockMvc.perform(post("/api/admin/reports/" + r1 + "/remove-content").with(auth.bearer(mod)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"retirada\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIONED")).andExpect(jsonPath("$.contentAvailable").value(false));

        assertThat(statusOf(r2)).isEqualTo("ACTIONED");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM posts WHERE public_id = ?::uuid", Boolean.class, post)).isTrue();
        mockMvc.perform(get("/api/posts/" + post).with(auth.bearer(bea))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE action = 'CONTENT_REMOVED' AND target_public_id = ?::uuid",
                Integer.class, post)).isEqualTo(1);
        mockMvc.perform(post("/api/admin/reports/" + r2 + "/remove-content").with(auth.bearer(mod))).andExpect(status().isConflict());
    }

    @Test
    void removeAComment_decrementsTheCounter() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount bea = testData.createUser();
        String post = createPost(bea, "post");
        String comment = objectMapper.readTree(mockMvc.perform(post("/api/posts/" + post + "/comments").with(auth.bearer(bea))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapperJson(Map.of("body", "feo"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String report = fileReport(testData.createUser(), "COMMENT", comment);

        mockMvc.perform(post("/api/admin/reports/" + report + "/remove-content").with(auth.bearer(mod))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT comment_count FROM posts WHERE public_id = ?::uuid", Integer.class, post)).isZero();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM post_comments WHERE public_id = ?::uuid", Boolean.class, comment)).isTrue();
    }

    @Test
    void removeAMessage_deletesItFromAnyChat() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID chat = testData.createPrivateConversation(ana, bea);
        UUID message = messageService.processIncomingMessage(chat, bea.getId(), "mensaje feo").id();
        String report = fileReport(ana, "MESSAGE", message.toString());

        mockMvc.perform(post("/api/admin/reports/" + report + "/remove-content").with(auth.bearer(mod))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE public_id = ?::uuid", Integer.class, message.toString())).isZero();
        // The copy is still in the report for the moderation
        assertThat(jdbc.queryForObject("SELECT content_snapshot FROM reports WHERE public_id = ?::uuid", String.class, report)).isEqualTo("mensaje feo");
    }

    @Test
    void removeAnEvent_cancelsIt() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount organizer = testData.createUser();
        String event = createEvent(organizer);
        String report = fileReport(testData.createUser(), "EVENT", event);

        mockMvc.perform(post("/api/admin/reports/" + report + "/remove-content").with(auth.bearer(mod))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contentAvailable").value(false));

        assertThat(jdbc.queryForObject("SELECT status FROM events WHERE public_id = ?::uuid", String.class, event)).isEqualTo("CANCELLED");
    }

    @Test
    void removeACommunity_isForAdminsOnly() throws Exception {
        UserAccount owner = testData.createUser();
        Community community = testData.createCommunity(owner);
        String report = fileReport(testData.createUser(), "COMMUNITY", community.getSlug());

        mockMvc.perform(post("/api/admin/reports/" + report + "/remove-content").with(auth.bearer(staff("MODERATOR")))).andExpect(status().isForbidden());
        assertThat(statusOf(report)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM communities WHERE id = ?", Integer.class, community.getId())).isEqualTo(1);

        mockMvc.perform(post("/api/admin/reports/" + report + "/remove-content").with(auth.bearer(staff("ADMIN")))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM communities WHERE id = ?", Integer.class, community.getId())).isZero();
    }

    @Test
    void removeContentOfAUserReport_isRefused_andLeavesItOpen() throws Exception {
        UserAccount mod = staff("ADMIN");
        String report = fileReport(testData.createUser(), "USER", testData.createUser().getUsername());
        mockMvc.perform(post("/api/admin/reports/" + report + "/remove-content").with(auth.bearer(mod))).andExpect(status().isBadRequest());
        assertThat(statusOf(report)).isEqualTo("OPEN");
        assertThat(audit(mod)).isZero();
    }

    // ---------------------------------------------------------------- copies and purge

    @Test
    void reportsOfUsersEventsAndCommunities_keepACopyOfTheirText() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount ana = testData.createUser();
        Community community = testData.createCommunity(owner);
        String event = createEvent(owner);

        jdbc.update("UPDATE user_profiles SET bio = 'bio ofensiva' WHERE user_id = ?", owner.getId());
        String ofUser = fileReport(ana, "USER", owner.getUsername());
        String ofEvent = fileReport(ana, "EVENT", event);
        String ofCommunity = fileReport(ana, "COMMUNITY", community.getSlug());

        assertThat(snapshotOf(ofUser)).contains("bio ofensiva");
        assertThat(snapshotOf(ofEvent)).contains("Salida ");
        assertThat(snapshotOf(ofCommunity)).contains(community.getName());
    }

    @Test
    void theCopiedTextOfResolvedReports_isPurgedAfterTheRetention_openOnesKeepIt() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount bea = testData.createUser();
        String post = createPost(bea, "texto");
        String resolved = fileReport(testData.createUser(), "POST", post, "SPAM", "detalle");
        String recent = fileReport(testData.createUser(), "POST", post, "SPAM", "detalle");
        String open = fileReport(testData.createUser(), "USER", bea.getUsername(), "SPAM", "detalle");
        mockMvc.perform(post("/api/admin/reports/" + resolved + "/dismiss").with(auth.bearer(mod))).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/reports/" + recent + "/dismiss").with(auth.bearer(mod))).andExpect(status().isOk());
        jdbc.update("UPDATE reports SET handled_at = ? WHERE public_id = ?::uuid", java.sql.Timestamp.from(Instant.now().minus(31, ChronoUnit.DAYS)), resolved);

        retention.purgeResolvedText();

        assertThat(snapshotOf(resolved)).isNull();
        assertThat(jdbc.queryForObject("SELECT details FROM reports WHERE public_id = ?::uuid", String.class, resolved)).isNull();
        assertThat(jdbc.queryForObject("SELECT reason FROM reports WHERE public_id = ?::uuid", String.class, resolved)).isEqualTo("SPAM");
        assertThat(snapshotOf(recent)).isEqualTo("texto");
        assertThat(snapshotOf(open)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT details FROM reports WHERE public_id = ?::uuid", String.class, open)).isEqualTo("detalle");
    }

    // ---------------------------------------------------------------- helpers

    private UserAccount staff(String role) {
        UserAccount user = testData.createUser();
        jdbc.update("UPDATE users SET role = ? WHERE id = ?", role, user.getId());
        return user;
    }

    private String fileReport(UserAccount reporter, String type, String target) throws Exception {
        return fileReport(reporter, type, target, "SPAM", null);
    }

    private String fileReport(UserAccount reporter, String type, String target, String reason, String details) throws Exception {
        String response = reportRequest(reporter, type, target, reason, details).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private ResultActions reportRequest(UserAccount reporter, String type, String target, String reason, String details) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("targetType", type);
        request.put("target", target);
        request.put("reason", reason);
        request.put("details", details);
        return mockMvc.perform(post("/api/reports").with(auth.bearer(reporter)).contentType(MediaType.APPLICATION_JSON).content(objectMapperJson(request)));
    }

    private String createPost(UserAccount author, String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/posts").with(auth.bearer(author)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapperJson(Map.of("body", body)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private String createEvent(UserAccount organizer) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Salida " + System.nanoTime());
        body.put("type", "MEETUP");
        body.put("timezone", "Europe/Madrid");
        body.put("startsAt", Instant.now().plus(7, ChronoUnit.DAYS).toString());
        body.put("meetingPointName", "Parking");
        String response = mockMvc.perform(post("/api/events").with(auth.bearer(organizer)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapperJson(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String objectMapperJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String statusOf(String reportId) {
        return jdbc.queryForObject("SELECT status FROM reports WHERE public_id = ?::uuid", String.class, reportId);
    }

    private String snapshotOf(String reportId) {
        return jdbc.queryForObject("SELECT content_snapshot FROM reports WHERE public_id = ?::uuid", String.class, reportId);
    }

    private int audit(UserAccount actor) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE actor_id = ?", Integer.class, actor.getId());
    }

    private int audit() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log", Integer.class);
    }
}
