package io.github.yagogefaell.asaltamontes.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.TestDataFactory;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.servlet.http.Cookie;

/** T23c2: suspending accounts from the panel. Login, refresh and the API stop for her; the staff is protected. */
class AccountSuspensionIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void suspendedAccount_cannotLogIn_refresh_orUseHerToken_andSessionsAreRevoked() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount bea = testData.createUser();
        Session session = login(bea);
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.access())).andExpect(status().isOk());
        String report = fileReport(testData.createUser(), bea);

        mockMvc.perform(post("/api/admin/reports/" + report + "/suspend-user").with(auth.bearer(mod)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":7,\"resolution\":\"acoso\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIONED"));

        // The token she holds dies at once; same 401 as any invalid token
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.access())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", session.refresh()))).andExpect(status().isUnauthorized());
        // Login with the right password gives the same answer as with a wrong one
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(bea.getUsername(), TestDataFactory.DEFAULT_PASSWORD)))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_sessions WHERE user_id = ? AND revoked_at IS NULL", Integer.class, bea.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE actor_id = ? AND action = 'USER_SUSPENDED'", Integer.class, mod.getId())).isEqualTo(1);
    }

    @Test
    void temporarySuspension_endsByItself() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount bea = testData.createUser();
        String report = fileReport(testData.createUser(), bea);
        mockMvc.perform(post("/api/admin/reports/" + report + "/suspend-user").with(auth.bearer(mod)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":1}")).andExpect(status().isOk());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(bea.getUsername(), TestDataFactory.DEFAULT_PASSWORD)))
                .andExpect(status().isUnauthorized());

        jdbc.update("UPDATE users SET suspended_until = now() - interval '1 minute' WHERE id = ?", bea.getId());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(bea.getUsername(), TestDataFactory.DEFAULT_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void permanentSuspension_andUnsuspend() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount bea = testData.createUser();
        String report = fileReport(testData.createUser(), bea);
        mockMvc.perform(post("/api/admin/reports/" + report + "/suspend-user").with(auth.bearer(mod)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"permanent\":true}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT suspended_permanently FROM users WHERE id = ?", Boolean.class, bea.getId())).isTrue();

        mockMvc.perform(post("/api/admin/users/" + bea.getUsername() + "/unsuspend").with(auth.bearer(testData.createUser()))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users/" + bea.getUsername() + "/unsuspend").with(auth.bearer(mod))).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/users/" + bea.getUsername() + "/unsuspend").with(auth.bearer(mod))).andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(bea.getUsername(), TestDataFactory.DEFAULT_PASSWORD)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE actor_id = ? AND action = 'USER_UNSUSPENDED'", Integer.class, mod.getId())).isEqualTo(1);
    }

    @Test
    void invalidDuration_isRejected_andTheReportStaysOpen() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount bea = testData.createUser();
        String report = fileReport(testData.createUser(), bea);
        for (String body : new String[] {"{}", "{\"days\":0}", "{\"days\":99999}", "{\"days\":3,\"permanent\":true}"}) {
            mockMvc.perform(post("/api/admin/reports/" + report + "/suspend-user").with(auth.bearer(mod)).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE public_id = ?::uuid", String.class, report)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT suspended_permanently OR suspended_until IS NOT NULL FROM users WHERE id = ?", Boolean.class, bea.getId())).isFalse();
    }

    @Test
    void aUserCannotSuspend_andTheStaffIsProtected() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount otherMod = staff("MODERATOR");
        UserAccount admin = staff("ADMIN");
        UserAccount plain = testData.createUser();

        String report = fileReport(testData.createUser(), testData.createUser());
        mockMvc.perform(post("/api/admin/reports/" + report + "/suspend-user").with(auth.bearer(plain)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":1}")).andExpect(status().isForbidden());

        // Nobody suspends an administrator; a moderator cannot suspend a moderator; an administrator can
        String ofAdmin = fileReport(testData.createUser(), admin);
        mockMvc.perform(post("/api/admin/reports/" + ofAdmin + "/suspend-user").with(auth.bearer(mod)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":1}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/reports/" + ofAdmin + "/suspend-user").with(auth.bearer(staff("ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":1}")).andExpect(status().isForbidden());
        String ofMod = fileReport(testData.createUser(), otherMod);
        mockMvc.perform(post("/api/admin/reports/" + ofMod + "/suspend-user").with(auth.bearer(mod)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":1}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/reports/" + ofMod + "/suspend-user").with(auth.bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":1}")).andExpect(status().isOk());
    }

    @Test
    void suspendingFromAContentReport_suspendsTheAuthor() throws Exception {
        UserAccount mod = staff("MODERATOR");
        UserAccount bea = testData.createUser();
        String post = objectMapper.readTree(mockMvc.perform(post("/api/posts").with(auth.bearer(bea)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"feo\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        Map<String, Object> request = new HashMap<>();
        request.put("targetType", "POST");
        request.put("target", post);
        request.put("reason", "HATE");
        String report = objectMapper.readTree(mockMvc.perform(post("/api/reports").with(auth.bearer(testData.createUser()))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/admin/reports/" + report + "/suspend-user").with(auth.bearer(mod)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":30}")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT suspended_until IS NOT NULL FROM users WHERE id = ?", Boolean.class, bea.getId())).isTrue();
    }

    @Test
    void unknownReport_is404() throws Exception {
        mockMvc.perform(post("/api/admin/reports/" + UUID.randomUUID() + "/suspend-user").with(auth.bearer(staff("ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"days\":1}")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    private record Session(String access, String refresh) {
    }

    private Session login(UserAccount user) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(user.getUsername(), TestDataFactory.DEFAULT_PASSWORD))).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String refresh = cookie.substring(cookie.indexOf("refreshToken=") + "refreshToken=".length(), cookie.indexOf(';'));
        return new Session(body.get("accessToken").asText(), refresh);
    }

    private String credentials(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    private UserAccount staff(String role) {
        UserAccount user = testData.createUser();
        jdbc.update("UPDATE users SET role = ? WHERE id = ?", role, user.getId());
        return user;
    }

    private String fileReport(UserAccount reporter, UserAccount reported) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("targetType", "USER");
        request.put("target", reported.getUsername());
        request.put("reason", "HARASSMENT");
        return objectMapper.readTree(mockMvc.perform(post("/api/reports").with(auth.bearer(reporter)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }
}
