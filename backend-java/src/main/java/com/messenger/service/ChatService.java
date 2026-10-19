package com.messenger.service;

import com.messenger.exception.ChatNotFoundException;
import com.messenger.exception.ForbiddenException;
import com.messenger.exception.MessageNotFoundException;
import com.messenger.exception.UserNotFoundException;
import com.messenger.exception.ValidationException;
import com.messenger.model.Chat;
import com.messenger.model.Message;
import com.messenger.model.User;
import com.messenger.repository.ChatRepository;
import com.messenger.repository.MessageRepository;
import com.messenger.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Логика чатов и сообщений: чаты, отправка, статусы, управление группой, удаление.
// Права доступа проверяются здесь.
@Service
public class ChatService {

    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;

    public ChatService(UserRepository userRepository, ChatRepository chatRepository,
                        MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
    }

    // Ищет пользователя по логину. Бросает UserNotFoundException.
    public User findUserByUsername(String username) {
        Optional<User> found = userRepository.findByUsername(username);
        if (found.isEmpty()) {
            throw new UserNotFoundException("user not found");
        }
        return found.get();
    }

    // Создаёт группу. Создатель добавляется в участники автоматически и становится админом.
    public Chat createGroup(String creatorId, String title, List<String> memberIds) {
        if (title == null || title.isBlank()) {
            throw new ValidationException("group title is required");
        }

        List<String> allMembers = new ArrayList<>(memberIds);
        if (!allMembers.contains(creatorId)) {
            allMembers.add(creatorId);
        }
        if (allMembers.size() < 2) {
            throw new ValidationException("group needs at least 2 members");
        }

        return chatRepository.createGroup(title, creatorId, allMembers);
    }

    // Список чатов пользователя со счётчиком непрочитанных.
    public List<ChatSummary> listChatsForUser(String userId) {
        List<ChatSummary> result = new ArrayList<>();

        for (Chat chat : chatRepository.findAllForUser(userId)) {
            int unreadCount = countUnread(chat.getId(), userId);

            if (chat.isGroup()) {
                result.add(ChatSummary.forGroup(chat, resolveMembers(chat.getMemberIds()), unreadCount));
            } else {
                String peerId = chat.getPeerId(userId);
                Optional<User> peer = userRepository.findById(peerId);
                if (peer.isPresent()) {
                    result.add(ChatSummary.forDirect(chat, peer.get(), unreadCount));
                }
            }
        }

        return result;
    }

    // Загружает пользователей по списку id.
    private List<User> resolveMembers(List<String> memberIds) {
        List<User> members = new ArrayList<>();
        for (String memberId : memberIds) {
            Optional<User> member = userRepository.findById(memberId);
            if (member.isPresent()) {
                members.add(member.get());
            }
        }
        return members;
    }

    // Число чужих непрочитанных сообщений в чате. Статус общий для всех участников группы.
    private int countUnread(String chatId, String userId) {
        int count = 0;
        for (Message message : messageRepository.findAllByChatId(chatId)) {
            boolean isFromSomeoneElse = !message.getSenderId().equals(userId);
            boolean notRead = message.getStatus() != Message.Status.READ;
            if (isFromSomeoneElse && notRead) {
                count++;
            }
        }
        return count;
    }

    // История сообщений чата (только для участника).
    public List<Message> getMessages(String userId, String chatId, int page, int limit) {
        Chat chat = requireParticipant(userId, chatId);

        int safeLimit = (limit <= 0 || limit > 100) ? 50 : limit;
        return messageRepository.findPage(chat.getId(), page, safeLimit);
    }

    // Отправляет сообщение пользователю; личный чат создаётся при первом сообщении.
    public Message sendMessageToUser(String senderId, String recipientUserId, String content) {
        Chat chat = chatRepository.findOrCreateDirect(senderId, recipientUserId);
        return messageRepository.save(chat.getId(), senderId, content);
    }

    // Отправляет сообщение в существующий чат.
    public Message sendMessageToChat(String senderId, String chatId, String content) {
        Chat chat = requireParticipant(senderId, chatId);
        return messageRepository.save(chat.getId(), senderId, content);
    }

    // Участники чата, кроме отправителя. Если чата нет — пустой список.
    public List<String> getOtherMemberIds(String chatId, String senderId) {
        Optional<Chat> chat = chatRepository.findById(chatId);
        if (chat.isEmpty()) {
            return new ArrayList<>();
        }
        return chat.get().getOtherMemberIds(senderId);
    }

    // Помечает сообщение доставленным, если оно ещё в статусе SENT.
    public void markDelivered(String messageId) {
        Optional<Message> message = messageRepository.findById(messageId);
        if (message.isPresent() && message.get().getStatus() == Message.Status.SENT) {
            messageRepository.updateStatus(messageId, Message.Status.DELIVERED);
        }
    }

    // Помечает прочитанными чужие сообщения чата. Возвращает id изменённых сообщений.
    public List<String> markChatRead(String chatId, String readerId) {
        requireParticipant(readerId, chatId);

        List<String> changedMessageIds = new ArrayList<>();
        for (Message message : messageRepository.findAllByChatId(chatId)) {
            boolean isFromSomeoneElse = !message.getSenderId().equals(readerId);
            boolean notYetRead = message.getStatus() != Message.Status.READ;

            if (isFromSomeoneElse && notYetRead) {
                messageRepository.updateStatus(message.getId(), Message.Status.READ);
                changedMessageIds.add(message.getId());
            }
        }
        return changedMessageIds;
    }

    // ---------- Управление группой (только создатель) ----------

    // Переименовывает группу.
    public Chat renameGroup(String userId, String chatId, String newTitle) {
        if (newTitle == null || newTitle.isBlank()) {
            throw new ValidationException("group title is required");
        }
        requireGroupAdmin(userId, chatId);

        chatRepository.renameGroup(chatId, newTitle.trim());
        return chatRepository.findById(chatId).get();
    }

    // Добавляет участника в группу.
    public void addMember(String userId, String chatId, String newMemberId) {
        requireGroupAdmin(userId, chatId);

        Optional<User> newMember = userRepository.findById(newMemberId);
        if (newMember.isEmpty()) {
            throw new UserNotFoundException("user not found");
        }

        chatRepository.addMember(chatId, newMemberId);
    }

    // Удаляет участника из группы. Создателя удалить нельзя.
    public void removeMember(String userId, String chatId, String memberIdToRemove) {
        Chat chat = requireGroupAdmin(userId, chatId);

        if (chat.isCreator(memberIdToRemove)) {
            throw new ValidationException("creator can't be removed from the group, delete the group instead");
        }

        chatRepository.removeMember(chatId, memberIdToRemove);
    }

    // Удаляет чат целиком: группу — только создатель, личный чат — любой участник.
    public void deleteChat(String userId, String chatId) {
        Chat chat = requireParticipant(userId, chatId);

        if (chat.isGroup() && !chat.isCreator(userId)) {
            throw new ForbiddenException("only the group creator can delete the group");
        }

        chatRepository.deleteChat(chatId);
    }

    // Удаляет сообщение у всех участников. Доступно только автору.
    public void deleteMessage(String userId, String messageId) {
        Optional<Message> message = messageRepository.findById(messageId);
        if (message.isEmpty()) {
            throw new MessageNotFoundException("message not found");
        }
        if (!message.get().getSenderId().equals(userId)) {
            throw new ForbiddenException("you can only delete your own messages");
        }
        messageRepository.delete(messageId);
    }

    // Возвращает чат или бросает исключение, если чата нет или пользователь не его участник.
    private Chat requireParticipant(String userId, String chatId) {
        Optional<Chat> chatOpt = chatRepository.findById(chatId);
        if (chatOpt.isEmpty()) {
            throw new ChatNotFoundException("chat not found");
        }
        Chat chat = chatOpt.get();

        if (!chat.hasParticipant(userId)) {
            throw new ForbiddenException("you are not a participant of this chat");
        }
        return chat;
    }

    // Проверяет, что чат групповой и пользователь — его создатель.
    private Chat requireGroupAdmin(String userId, String chatId) {
        Chat chat = requireParticipant(userId, chatId);
        if (!chat.isGroup()) {
            throw new ValidationException("this action is only available for group chats");
        }
        if (!chat.isCreator(userId)) {
            throw new ForbiddenException("only the group creator can manage this group");
        }
        return chat;
    }

    // Строка списка чатов: для группы заполнены title, creatorId, members, для личного чата — peer.
    public static class ChatSummary {
        public final String chatId;
        public final boolean isGroup;
        public final String title;       // только для группы
        public final String creatorId;   // только для группы — фронтенду нужно знать, кто админ
        public final User peer;          // только для личного чата
        public final List<User> members; // только для группы
        public final int unreadCount;

        private ChatSummary(String chatId, boolean isGroup, String title, String creatorId,
                             User peer, List<User> members, int unreadCount) {
            this.chatId = chatId;
            this.isGroup = isGroup;
            this.title = title;
            this.creatorId = creatorId;
            this.peer = peer;
            this.members = members;
            this.unreadCount = unreadCount;
        }

        // Сводка личного чата.
        public static ChatSummary forDirect(Chat chat, User peer, int unreadCount) {
            return new ChatSummary(chat.getId(), false, null, null, peer, null, unreadCount);
        }

        // Сводка группового чата.
        public static ChatSummary forGroup(Chat chat, List<User> members, int unreadCount) {
            return new ChatSummary(chat.getId(), true, chat.getTitle(), chat.getCreatorId(),
                    null, members, unreadCount);
        }
    }
}
