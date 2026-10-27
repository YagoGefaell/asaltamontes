package io.github.yagogefaell.asaltamontes.user.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.event.domain.EventVisibility;
import io.github.yagogefaell.asaltamontes.event.dto.CreateEventDto;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;

/** BUG-069: a block (in either direction) hides the other woman in community member and event attendee lists. */
class BlockedInListsIT extends AbstractIntegrationTest {

    @Autowired private BlockService blocks;
    @Autowired private EventService eventService;

    @Test
    void communityMembers_hideBlockedInEitherDirection_keepingPaginationFull() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount blockedByAna = testData.createUser();
        UserAccount blockerOfAna = testData.createUser();
        UserAccount other = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : new UserAccount[] {ana, blockedByAna, blockerOfAna, other}) {
            mockMvc.perform(post("/api/communities/" + community.getPublicId() + "/join").with(auth.bearer(u)))
                    .andExpect(status().isCreated());
        }
        blocks.block(ana.getId(), blockedByAna.getUsername());
        blocks.block(blockerOfAna.getId(), ana.getUsername());

        mockMvc.perform(get("/api/communities/" + community.getPublicId() + "/members").with(auth.bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].username", containsInAnyOrder(owner.getUsername(), other.getUsername())));
        // Page size 1: every page is full, nothing hidden is counted
        mockMvc.perform(get("/api/communities/" + community.getPublicId() + "/members").param("size", "1").with(auth.bearer(ana)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2));
        // Members not involved in any block still see everybody else
        mockMvc.perform(get("/api/communities/" + community.getPublicId() + "/members").with(auth.bearer(other)))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void eventAttendees_hideBlockedInEitherDirection_butNotTheCallerHerself() throws Exception {
        UserAccount organizer = testData.createUser();
        UserAccount ana = testData.createUser();
        UserAccount blockedByAna = testData.createUser();
        UserAccount blockerOfAna = testData.createUser();
        UUID id = eventService.create(new CreateEventDto(testData.uniqueName("Quedada"), null, EventType.MEETUP,
                Instant.now().plus(3, ChronoUnit.DAYS), null, "Europe/Madrid", "Parking", null, null, "Vigo", null, null,
                null, 10, EventVisibility.PUBLIC, null), organizer).id();
        for (UserAccount u : new UserAccount[] {ana, blockedByAna, blockerOfAna}) {
            mockMvc.perform(post("/api/events/" + id + "/join").with(auth.bearer(u))).andExpect(status().isOk());
        }
        blocks.block(ana.getId(), blockedByAna.getUsername());
        blocks.block(blockerOfAna.getId(), ana.getUsername());

        mockMvc.perform(get("/api/events/" + id + "/attendees").with(auth.bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].username", contains(organizer.getUsername(), ana.getUsername())));
    }
}
