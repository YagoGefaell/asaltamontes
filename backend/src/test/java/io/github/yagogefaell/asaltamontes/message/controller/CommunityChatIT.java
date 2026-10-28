package io.github.yagogefaell.asaltamontes.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.message.dto.ChatErrorDto;
import io.github.yagogefaell.asaltamontes.message.dto.ConversationRemovedDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDeletedDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.dto.SendMessageCommandDto;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.StompTestClient;
import io.github.yagogefaell.asaltamontes.support.StompTestClient.Connection;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** T20: the chat of each community follows its members and its moderation. */
class CommunityChatIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private MessageService messageService;
    @Autowired private JdbcTemplate jdbc;

    private StompTestClient stomp;

    @BeforeEach
    void setUp() {
        stomp = new StompTestClient(port, objectMapper);
    }

    @Test
    void theChatExistsFromTheStart_andOnlyMembersGetIt() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount outsider = testData.createUser();
        Community community = testData.createCommunity(owner);

        UUID chat = chatOf(owner, community);

        // Managed group: it shows in her chat list under the name of the community
        mockMvc.perform(get("/api/conversations").with(auth.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].conversationId").value(chat.toString()))
                .andExpect(jsonPath("$.items[0].displayName").value(community.getName()));
        mockMvc.perform(get("/api/conversations/" + chat).with(auth.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managed").value(true))
                .andExpect(jsonPath("$.participantCount").value(1));

        // Not a member: same 404 as a community that does not exist, and the chat is invisible to her
        chatRequest(outsider, community).andExpect(status().isNotFound());
        chatRequest(outsider, UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(outsider))).andExpect(status().isNotFound());
    }

    @Test
    void joiningAndLeavingTheCommunity_keepsTheChatInSync() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);

        join(member, community).andExpect(status().isCreated());
        assertThat(chatOf(member, community)).isEqualTo(chat);
        mockMvc.perform(get("/api/conversations/" + chat).with(auth.bearer(owner)))
                .andExpect(jsonPath("$.participantCount").value(2));
        messageService.processIncomingMessage(chat, member.getId(), "hola a todas");

        mockMvc.perform(post("/api/communities/" + community.getPublicId() + "/leave").with(auth.bearer(member)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(member))).andExpect(status().isNotFound());
        chatRequest(member, community).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/conversations/" + chat).with(auth.bearer(owner)))
                .andExpect(jsonPath("$.participantCount").value(1));
    }

    @Test
    void aNewMember_seesTheWholeHistory() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount late = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        messageService.processIncomingMessage(chat, owner.getId(), "primero");
        messageService.processIncomingMessage(chat, owner.getId(), "segundo");

        join(late, community).andExpect(status().isCreated());

        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(late)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].content").value("primero"));
    }

    @Test
    void nobodyManagesTheMembersOrTheChatByHand_andLeavingIsLeavingTheCommunity() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount other = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(member, community).andExpect(status().isCreated());

        // Not even the owner: everybody is a plain participant of a chat that the community manages
        mockMvc.perform(post("/api/conversations/" + chat + "/participants").with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"usernames\":[\"" + other.getUsername() + "\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/conversations/" + chat + "/participants/" + member.getUsername()).with(auth.bearer(owner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/conversations/" + chat).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"groupName\":\"otro\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/conversations/" + chat + "/leave").with(auth.bearer(member)))
                .andExpect(status().isConflict());
        assertThat(participants(chat)).isEqualTo(2);
    }

    @Test
    void renamingTheCommunity_renamesTheChat() throws Exception {
        UserAccount owner = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);

        mockMvc.perform(patch("/api/communities/" + community.getPublicId()).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nuevo nombre\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations/" + chat).with(auth.bearer(owner)))
                .andExpect(jsonPath("$.name").value("Nuevo nombre"));
    }

    @Test
    void aMutedMember_readsButCannotWrite_untilUnmuted() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount muted = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(muted, community).andExpect(status().isCreated());
        messageService.processIncomingMessage(chat, owner.getId(), "reglas del grupo");

        moderate(owner, community, muted, "mute").andExpect(status().isNoContent());

        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, muted.getId(), "spam"))
                .isInstanceOf(ForbiddenException.class);
        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(muted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        JsonNode state = json(chatRequest(muted, community).andExpect(status().isOk()));
        assertThat(state.get("canWrite").asBoolean()).isFalse();
        assertThat(state.get("enabled").asBoolean()).isTrue();

        moderate(owner, community, muted, "unmute").andExpect(status().isNoContent());

        assertThat(messageService.processIncomingMessage(chat, muted.getId(), "gracias").content()).isEqualTo("gracias");
    }

    @Test
    void aMutedMember_canLeave_andIsStillMutedWhenSheComesBack() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount muted = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(muted, community).andExpect(status().isCreated());
        moderate(owner, community, muted, "mute").andExpect(status().isNoContent());

        // Leaving is allowed while muted (decision of Yago, 2026-10-29)
        mockMvc.perform(post("/api/communities/" + community.getPublicId() + "/leave").with(auth.bearer(muted)))
                .andExpect(status().isNoContent());
        chatRequest(muted, community).andExpect(status().isNotFound());

        join(muted, community).andExpect(status().isCreated());

        assertThat(chatOf(muted, community)).isEqualTo(chat);
        assertThat(json(chatRequest(muted, community).andExpect(status().isOk())).get("canWrite").asBoolean()).isFalse();
        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, muted.getId(), "ya estoy de vuelta"))
                .isInstanceOf(ForbiddenException.class);

        // Unmuting lifts it for good: leaving and coming back later does not mute her again
        moderate(owner, community, muted, "unmute").andExpect(status().isNoContent());
        mockMvc.perform(post("/api/communities/" + community.getPublicId() + "/leave").with(auth.bearer(muted)))
                .andExpect(status().isNoContent());
        join(muted, community).andExpect(status().isCreated());
        assertThat(messageService.processIncomingMessage(chat, muted.getId(), "hola").content()).isEqualTo("hola");
    }

    @Test
    void aMutedMemberOfAPrivateCommunity_isStillMutedWhenTheRequestIsApproved() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount muted = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(muted, community).andExpect(status().isCreated());
        moderate(owner, community, muted, "mute").andExpect(status().isNoContent());
        mockMvc.perform(post("/api/communities/" + community.getPublicId() + "/leave").with(auth.bearer(muted)))
                .andExpect(status().isNoContent());
        jdbc.update("UPDATE communities SET visibility = 'PRIVATE' WHERE id = ?", community.getId());

        join(muted, community).andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/communities/" + community.getPublicId() + "/requests/" + muted.getUsername() + "/approve")
                .with(auth.bearer(owner))).andExpect(status().isNoContent());

        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, muted.getId(), "hola"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theChatHasNoSystemMessages_whenSomeoneJoinsOrLeaves() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);

        join(member, community).andExpect(status().isCreated());
        mockMvc.perform(post("/api/communities/" + community.getPublicId() + "/leave").with(auth.bearer(member)))
                .andExpect(status().isNoContent());
        join(member, community).andExpect(status().isCreated());
        moderate(owner, community, member, "ban").andExpect(status().isNoContent());
        moderate(owner, community, member, "unban").andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages m JOIN conversations c ON c.id = m.conversation_id "
                + "WHERE c.public_id = ?", Integer.class, chat)).isZero();
    }

    @Test
    void onlyTheOwnerSwitchesTheChat_aModeratorGets403_andTheOwnerStillCan() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(mod, community).andExpect(status().isCreated());
        promote(owner, community, mod);

        mockMvc.perform(patch("/api/communities/" + community.getPublicId()).with(auth.bearer(mod))
                .contentType(MediaType.APPLICATION_JSON).content("{\"chatEnabled\":false}"))
                .andExpect(status().isForbidden());
        assertThat(json(chatRequest(owner, community).andExpect(status().isOk())).get("enabled").asBoolean()).isTrue();
        // Her other powers over the community stay
        mockMvc.perform(patch("/api/communities/" + community.getPublicId()).with(auth.bearer(mod))
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"nueva\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/communities/" + community.getPublicId()).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"chatEnabled\":false}"))
                .andExpect(status().isOk());
        assertThat(json(chatRequest(owner, community).andExpect(status().isOk())).get("enabled").asBoolean()).isFalse();
    }

    @Test
    void aModeratorMutesMembers_butNotTheOwnerNorAnotherModerator() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount otherMod = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        for (UserAccount u : new UserAccount[] {mod, otherMod, member}) {
            join(u, community).andExpect(status().isCreated());
        }
        promote(owner, community, mod);
        promote(owner, community, otherMod);

        moderate(mod, community, member, "mute").andExpect(status().isNoContent());
        moderate(mod, community, owner, "mute").andExpect(status().isConflict());
        moderate(mod, community, otherMod, "mute").andExpect(status().isForbidden());
        // The owner can mute a moderator
        moderate(owner, community, otherMod, "mute").andExpect(status().isNoContent());
    }

    @Test
    void moderatorsAndTheOwnerDeleteOthersMessages_membersCannot() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount other = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        for (UserAccount u : new UserAccount[] {mod, member, other}) {
            join(u, community).andExpect(status().isCreated());
        }
        promote(owner, community, mod);
        UUID kept = messageService.processIncomingMessage(chat, owner.getId(), "normas").id();
        UUID spam1 = messageService.processIncomingMessage(chat, member.getId(), "spam uno").id();
        UUID spam2 = messageService.processIncomingMessage(chat, member.getId(), "spam dos").id();

        // A plain member (even the author) cannot; someone outside the chat does not even see it
        deleteMessage(member, chat, spam1).andExpect(status().isForbidden());
        deleteMessage(other, chat, spam1).andExpect(status().isForbidden());
        UserAccount outsider = testData.createUser();
        deleteMessage(outsider, chat, spam1).andExpect(status().isNotFound());

        Connection otherConnection = stomp.connect("Bearer " + auth.loginAs(other));
        BlockingQueue<MessageDeletedDto> deletions = otherConnection.subscribe("/user/queue/message-deletions", MessageDeletedDto.class);
        deleteMessage(mod, chat, spam2).andExpect(status().isNoContent());
        MessageDeletedDto event = deletions.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.messageId()).isEqualTo(spam2);
        assertThat(event.conversationId()).isEqualTo(chat);
        otherConnection.disconnect();

        // A moderator cannot delete what the owner or another moderator wrote; the owner can delete a moderator's
        UUID ownerMsg = messageService.processIncomingMessage(chat, owner.getId(), "aviso").id();
        UUID modMsg = messageService.processIncomingMessage(chat, mod.getId(), "aviso mod").id();
        deleteMessage(mod, chat, ownerMsg).andExpect(status().isForbidden());
        deleteMessage(owner, chat, modMsg).andExpect(status().isNoContent());
        deleteMessage(owner, chat, ownerMsg).andExpect(status().isNoContent());

        deleteMessage(owner, chat, spam1).andExpect(status().isNoContent());
        // Gone, already gone, and unknown: 404
        deleteMessage(owner, chat, spam1).andExpect(status().isNotFound());
        deleteMessage(owner, chat, UUID.randomUUID()).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(other)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(kept.toString()));
        // The list preview follows the last message that is left
        mockMvc.perform(get("/api/conversations").with(auth.bearer(other)))
                .andExpect(jsonPath("$.items[0].lastMessage").value("normas"));
        // The moderation log keeps who deleted whose message
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_moderation_log WHERE action = 'MESSAGE_DELETED' AND community_id = ?",
                Integer.class, community.getId())).isEqualTo(4);
    }

    @Test
    void deletingMessagesOutsideACommunityChat_isForbidden() throws Exception {
        UserAccount a = testData.createUser();
        UserAccount b = testData.createUser();
        UUID chat = UUID.fromString(objectMapper.readTree(mockMvc.perform(post("/api/conversations/private").with(auth.bearer(a))
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + b.getUsername() + "\"}"))
                .andReturn().getResponse().getContentAsString()).get("conversationId").asText());
        UUID message = messageService.processIncomingMessage(chat, b.getId(), "hola").id();

        deleteMessage(a, chat, message).andExpect(status().isForbidden());
        deleteMessage(b, chat, message).andExpect(status().isForbidden());
    }

    @Test
    void aMutedMemberTryingToWriteOverWebSocket_getsTheErrorAndNothingIsDelivered() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount muted = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(muted, community).andExpect(status().isCreated());
        moderate(owner, community, muted, "mute").andExpect(status().isNoContent());
        Connection ownerConnection = stomp.connect("Bearer " + auth.loginAs(owner));
        BlockingQueue<MessageDto> ownerInbox = ownerConnection.subscribe("/user/queue/messages", MessageDto.class);
        Connection mutedConnection = stomp.connect("Bearer " + auth.loginAs(muted));
        BlockingQueue<ChatErrorDto> errors = mutedConnection.subscribe("/user/queue/errors", ChatErrorDto.class);

        mutedConnection.send("/app/chat.send", new SendMessageCommandDto(chat, "hola"));

        ChatErrorDto error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.detail()).contains("silenciada");
        assertThat(ownerInbox.poll(1, TimeUnit.SECONDS)).isNull();
        ownerConnection.disconnect();
        mutedConnection.disconnect();
    }

    @Test
    void anExpelledMember_losesTheChat_andGetsItBackWhenReadmitted() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount expelled = testData.createUser();
        UserAccount other = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(expelled, community).andExpect(status().isCreated());
        join(other, community).andExpect(status().isCreated());
        Connection expelledConnection = stomp.connect("Bearer " + auth.loginAs(expelled));
        BlockingQueue<MessageDto> inbox = expelledConnection.subscribe("/user/queue/messages", MessageDto.class);
        BlockingQueue<ConversationRemovedDto> removed = expelledConnection.subscribe("/user/queue/conversations", ConversationRemovedDto.class);

        moderate(owner, community, expelled, "ban").andExpect(status().isNoContent());

        ConversationRemovedDto event = removed.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.conversationId()).isEqualTo(chat);
        assertThat(event.reason()).isEqualTo("REMOVED");
        messageService.processIncomingMessage(chat, owner.getId(), "ya no estás");
        assertThat(inbox.poll(1, TimeUnit.SECONDS)).isNull();
        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(expelled))).andExpect(status().isNotFound());
        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, expelled.getId(), "dejadme entrar"))
                .isInstanceOf(NotFoundException.class);
        chatRequest(expelled, community).andExpect(status().isNotFound());
        assertThat(participants(chat)).isEqualTo(2);

        moderate(owner, community, expelled, "unban").andExpect(status().isNoContent());

        assertThat(chatOf(expelled, community)).isEqualTo(chat);
        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(expelled)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        expelledConnection.disconnect();
    }

    @Test
    void groupDetails_tellOwnerAndModeratorsTheyCanModerateMessages_butNotMembers() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount moderator = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(moderator, community).andExpect(status().isCreated());
        join(member, community).andExpect(status().isCreated());
        promote(owner, community, moderator);

        for (UserAccount who : new UserAccount[] {owner, moderator}) {
            mockMvc.perform(get("/api/conversations/" + chat).with(auth.bearer(who)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.canModerateMessages").value(true))
                    .andExpect(jsonPath("$.moderationRole").value(who == owner ? "OWNER" : "MODERATOR"));
        }
        mockMvc.perform(get("/api/conversations/" + chat).with(auth.bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canModerateMessages").value(false))
                .andExpect(jsonPath("$.moderationRole").doesNotExist());
    }

    @Test
    void theModerationCanSwitchTheChatOff_historyStaysReadable() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(member, community).andExpect(status().isCreated());
        messageService.processIncomingMessage(chat, owner.getId(), "antes");

        mockMvc.perform(patch("/api/communities/" + community.getPublicId()).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"chatEnabled\":false}"))
                .andExpect(status().isOk());

        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, member.getId(), "hola"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, owner.getId(), "hola"))
                .isInstanceOf(ConflictException.class);
        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(member)))
                .andExpect(jsonPath("$.items.length()").value(1));
        JsonNode state = json(chatRequest(member, community).andExpect(status().isOk()));
        assertThat(state.get("enabled").asBoolean()).isFalse();
        assertThat(state.get("canWrite").asBoolean()).isFalse();
        // Only a boolean, and only the staff
        mockMvc.perform(patch("/api/communities/" + community.getPublicId()).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"chatEnabled\":\"no\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/communities/" + community.getPublicId()).with(auth.bearer(member))
                .contentType(MediaType.APPLICATION_JSON).content("{\"chatEnabled\":true}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/communities/" + community.getPublicId()).with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"chatEnabled\":true}"))
                .andExpect(status().isOk());
        assertThat(messageService.processIncomingMessage(chat, member.getId(), "de vuelta").content()).isEqualTo("de vuelta");
    }

    @Test
    void anExistingCommunityWithoutChat_getsItOnFirstAccess_withItsMembers() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        join(member, community).andExpect(status().isCreated());
        // As if it predated the feature
        jdbc.update("DELETE FROM conversation_participants WHERE conversation_id IN "
                + "(SELECT id FROM conversations WHERE managed_by = 'COMMUNITY' AND managed_ref_id = ?)", community.getId());
        jdbc.update("DELETE FROM conversations WHERE managed_by = 'COMMUNITY' AND managed_ref_id = ?", community.getId());

        UUID chat = chatOf(member, community);

        assertThat(participants(chat)).isEqualTo(2);
        assertThat(chatOf(owner, community)).isEqualTo(chat);
    }

    @Test
    void deletingTheCommunity_deletesItsChat() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(member, community).andExpect(status().isCreated());
        messageService.processIncomingMessage(chat, owner.getId(), "adiós");

        mockMvc.perform(delete("/api/communities/" + community.getPublicId()).with(auth.bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(member))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE public_id = ?", Integer.class, chat)).isZero();
    }

    @Test
    void aMessageReachesEveryMember_butNotWhoIsNotOne() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount member = testData.createUser();
        UserAccount outsider = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(member, community).andExpect(status().isCreated());
        Connection memberConnection = stomp.connect("Bearer " + auth.loginAs(member));
        BlockingQueue<MessageDto> memberInbox = memberConnection.subscribe("/user/queue/messages", MessageDto.class);
        Connection outsiderConnection = stomp.connect("Bearer " + auth.loginAs(outsider));
        BlockingQueue<MessageDto> outsiderInbox = outsiderConnection.subscribe("/user/queue/messages", MessageDto.class);

        messageService.processIncomingMessage(chat, owner.getId(), "salimos el sábado");

        MessageDto received = memberInbox.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.content()).isEqualTo("salimos el sábado");
        assertThat(received.conversationId()).isEqualTo(chat);
        assertThat(outsiderInbox.poll(1, TimeUnit.SECONDS)).isNull();
        memberConnection.disconnect();
        outsiderConnection.disconnect();
    }

    @Test
    void messagesCarryTheRoleOfTheirAuthor_inTheHistoryAndOverTheWebSocket() throws Exception {
        UserAccount owner = testData.createUser();
        UserAccount mod = testData.createUser();
        UserAccount member = testData.createUser();
        Community community = testData.createCommunity(owner);
        UUID chat = chatOf(owner, community);
        join(mod, community).andExpect(status().isCreated());
        join(member, community).andExpect(status().isCreated());
        promote(owner, community, mod);
        Connection memberConnection = stomp.connect("Bearer " + auth.loginAs(member));
        BlockingQueue<MessageDto> inbox = memberConnection.subscribe("/user/queue/messages", MessageDto.class);

        messageService.processIncomingMessage(chat, owner.getId(), "uno");
        messageService.processIncomingMessage(chat, mod.getId(), "dos");
        messageService.processIncomingMessage(chat, member.getId(), "tres");

        assertThat(inbox.poll(5, TimeUnit.SECONDS).authorRole()).isEqualTo(CommunityRole.OWNER);
        assertThat(inbox.poll(5, TimeUnit.SECONDS).authorRole()).isEqualTo(CommunityRole.MODERATOR);
        memberConnection.disconnect();

        mockMvc.perform(get("/api/conversations/" + chat + "/messages").with(auth.bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].authorRole").value("OWNER"))
                .andExpect(jsonPath("$.items[1].authorRole").value("MODERATOR"))
                .andExpect(jsonPath("$.items[2].authorRole").doesNotExist());

        // A chat that no community manages never carries a role, even for the owner of a community
        UUID privateChat = UUID.fromString(json(mockMvc.perform(post("/api/conversations/private").with(auth.bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + member.getUsername() + "\"}"))
                ).get("conversationId").asText());
        MessageDto direct = messageService.processIncomingMessage(privateChat, owner.getId(), "hola");
        assertThat(direct.authorRole()).isNull();
    }

    // ---- helpers ----

    private ResultActions deleteMessage(UserAccount user, UUID chat, UUID message) throws Exception {
        return mockMvc.perform(delete("/api/conversations/" + chat + "/messages/" + message).with(auth.bearer(user)));
    }

    private void promote(UserAccount owner, Community community, UserAccount target) throws Exception {
        mockMvc.perform(put("/api/communities/" + community.getPublicId() + "/members/" + target.getUsername() + "/role")
                .with(auth.bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MODERATOR\"}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions chatRequest(UserAccount user, Community community) throws Exception {
        return chatRequest(user, community.getPublicId());
    }

    private ResultActions chatRequest(UserAccount user, UUID communityId) throws Exception {
        return mockMvc.perform(get("/api/communities/" + communityId + "/chat").with(auth.bearer(user)));
    }

    private UUID chatOf(UserAccount user, Community community) throws Exception {
        return UUID.fromString(json(chatRequest(user, community).andExpect(status().isOk())).get("conversationId").asText());
    }

    private ResultActions join(UserAccount user, Community community) throws Exception {
        return mockMvc.perform(post("/api/communities/" + community.getPublicId() + "/join").with(auth.bearer(user)));
    }

    private ResultActions moderate(UserAccount actor, Community community, UserAccount target, String verb) throws Exception {
        return mockMvc.perform(post("/api/communities/" + community.getPublicId() + "/members/" + target.getUsername() + "/" + verb)
                .with(auth.bearer(actor)).contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private int participants(UUID chat) {
        return jdbc.queryForObject("SELECT count(*) FROM conversation_participants p JOIN conversations c ON c.id = p.conversation_id "
                + "WHERE c.public_id = ?", Integer.class, chat);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
