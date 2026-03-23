package io.github.yagogefaell.asaltamontes.message.service;

import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.dto.PrivateMessage;
import io.github.yagogefaell.asaltamontes.message.repository.MessageRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserService userService;

    public MessageService(MessageRepository messageRepository, UserService userService) {
        this.messageRepository = messageRepository;
        this.userService = userService;
    }

    @Transactional
    public Message saveMessage(PrivateMessage dto) {
        UserAccount sender = (UserAccount) userService.loadUserByUsername(dto.senderUsername());
        UserAccount receiver = (UserAccount) userService.loadUserByUsername(dto.receiverUsername());

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(dto.content());
        message.setSentAt(LocalDateTime.now());
        message.setRead(false);

        return messageRepository.save(message);
    }

    public List<Message> getChatHistory(String user1, String user2) {
        return messageRepository.findChatHistory(user1, user2);
    }
}