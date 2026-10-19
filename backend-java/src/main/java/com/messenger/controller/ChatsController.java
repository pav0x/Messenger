package com.messenger.controller;

import com.messenger.auth.AuthUtils;
import com.messenger.model.Chat;
import com.messenger.model.Message;
import com.messenger.model.User;
import com.messenger.service.ChatService;
import com.messenger.websocket.RealtimeNotifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

// REST: чаты, группы, история и удаление сообщений.
// Права проверяет ChatService; после изменений контроллер рассылает WebSocket-события через RealtimeNotifier.
@RestController
@RequestMapping("/api/chats")
public class ChatsController {

    private final ChatService chatService;
    private final RealtimeNotifier notifier;

    public ChatsController(ChatService chatService, RealtimeNotifier notifier) {
        this.chatService = chatService;
        this.notifier = notifier;
    }

    // Тело запроса создания группы.
    public static class CreateGroupRequest {
        public String title;
        public List<String> member_ids;
    }

    // Тело запроса переименования группы.
    public static class RenameGroupRequest {
        public String title;
    }

    // Тело запроса добавления участника.
    public static class AddMemberRequest {
        public String user_id;
    }

    // Участник группы: id и имя.
    public static class MemberView {
        public String id;
        public String display_name;

        public MemberView(User user) {
            this.id = user.getId();
            this.display_name = user.getDisplayName();
        }
    }

    // Чат в списке. Для группы peer_name — название группы, peer_id пустой.
    public static class ChatSummaryView {
        public String chat_id;
        public boolean is_group;
        public String peer_id;
        public String peer_name;
        public boolean peer_online;
        public String creator_id;        // только для групп
        public int unread_count;
        public List<MemberView> members; // заполнено только для групп

        public ChatSummaryView(ChatService.ChatSummary summary) {
            this.chat_id = summary.chatId;
            this.is_group = summary.isGroup;
            this.unread_count = summary.unreadCount;

            if (summary.isGroup) {
                this.peer_id = null;
                this.peer_name = summary.title;
                this.peer_online = false;
                this.creator_id = summary.creatorId;
                this.members = new ArrayList<>();
                for (User member : summary.members) {
                    this.members.add(new MemberView(member));
                }
            } else {
                this.peer_id = summary.peer.getId();
                this.peer_name = summary.peer.getDisplayName();
                this.peer_online = summary.peer.isOnline();
                this.creator_id = null;
                this.members = null;
            }
        }
    }

    // Сообщение для клиента.
    public static class MessageView {
        public String id;
        public String sender_id;
        public String content;
        public String created_at;
        public String status;

        public MessageView(Message message) {
            this.id = message.getId();
            this.sender_id = message.getSenderId();
            this.content = message.getContent();
            this.created_at = message.getCreatedAt();
            this.status = message.getStatus().name().toLowerCase();
        }
    }

    // GET /api/chats — список чатов пользователя.
    @GetMapping
    public List<ChatSummaryView> listChats(HttpServletRequest request) {
        String myUserId = AuthUtils.requireUserId(request);

        List<ChatSummaryView> result = new ArrayList<>();
        for (ChatService.ChatSummary summary : chatService.listChatsForUser(myUserId)) {
            result.add(new ChatSummaryView(summary));
        }
        return result;
    }

    // POST /api/chats/group — создаёт группу.
    @PostMapping("/group")
    public ChatSummaryView createGroup(HttpServletRequest request, @RequestBody CreateGroupRequest body) {
        String myUserId = AuthUtils.requireUserId(request);
        Chat newChat = chatService.createGroup(myUserId, body.title, body.member_ids);
        return summaryViewFor(myUserId, newChat.getId());
    }

    // PATCH /api/chats/{id} — переименовывает группу.
    @PatchMapping("/{chatId}")
    public ChatSummaryView renameGroup(HttpServletRequest request, @PathVariable String chatId,
                                        @RequestBody RenameGroupRequest body) {
        String myUserId = AuthUtils.requireUserId(request);
        Chat chat = chatService.renameGroup(myUserId, chatId, body.title);

        notifier.sendToUsers(chat.getOtherMemberIds(myUserId),
                notifier.newEvent("chat_updated").put("chat_id", chatId));

        return summaryViewFor(myUserId, chatId);
    }

    // POST /api/chats/{id}/members — добавляет участника.
    @PostMapping("/{chatId}/members")
    public ChatSummaryView addMember(HttpServletRequest request, @PathVariable String chatId,
                                      @RequestBody AddMemberRequest body) {
        String myUserId = AuthUtils.requireUserId(request);
        chatService.addMember(myUserId, chatId, body.user_id);

        // Уведомление после добавления: его получает и новый участник.
        notifier.sendToUsers(chatService.getOtherMemberIds(chatId, myUserId),
                notifier.newEvent("chat_updated").put("chat_id", chatId));

        return summaryViewFor(myUserId, chatId);
    }

    // DELETE /api/chats/{id}/members/{memberId} — удаляет участника.
    @DeleteMapping("/{chatId}/members/{memberId}")
    public ChatSummaryView removeMember(HttpServletRequest request, @PathVariable String chatId,
                                         @PathVariable String memberId) {
        String myUserId = AuthUtils.requireUserId(request);
        chatService.removeMember(myUserId, chatId, memberId);

        notifier.sendToUser(memberId, notifier.newEvent("removed_from_chat").put("chat_id", chatId));
        notifier.sendToUsers(chatService.getOtherMemberIds(chatId, myUserId),
                notifier.newEvent("chat_updated").put("chat_id", chatId));

        return summaryViewFor(myUserId, chatId);
    }

    // DELETE /api/chats/{id} — удаляет чат целиком.
    @DeleteMapping("/{chatId}")
    public void deleteChat(HttpServletRequest request, @PathVariable String chatId) {
        String myUserId = AuthUtils.requireUserId(request);

        // Участников берём до удаления: после него чата уже нет.
        List<String> otherMembers = chatService.getOtherMemberIds(chatId, myUserId);
        chatService.deleteChat(myUserId, chatId);

        notifier.sendToUsers(otherMembers, notifier.newEvent("chat_deleted").put("chat_id", chatId));
    }

    // DELETE /api/chats/{id}/messages/{messageId} — удаляет сообщение.
    @DeleteMapping("/{chatId}/messages/{messageId}")
    public void deleteMessage(HttpServletRequest request, @PathVariable String chatId,
                               @PathVariable String messageId) {
        String myUserId = AuthUtils.requireUserId(request);
        chatService.deleteMessage(myUserId, messageId);

        notifier.sendToUsers(chatService.getOtherMemberIds(chatId, myUserId),
                notifier.newEvent("message_deleted").put("chat_id", chatId).put("message_id", messageId));
    }

    // GET /api/chats/{id}/messages?page&limit — история сообщений.
    @GetMapping("/{chatId}/messages")
    public List<MessageView> getMessages(
            HttpServletRequest request,
            @PathVariable String chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int limit) {
        String myUserId = AuthUtils.requireUserId(request);

        List<MessageView> result = new ArrayList<>();
        for (Message message : chatService.getMessages(myUserId, chatId, page, limit)) {
            result.add(new MessageView(message));
        }
        return result;
    }

    // Собирает ChatSummaryView одного чата в том же формате, что и список чатов.
    private ChatSummaryView summaryViewFor(String userId, String chatId) {
        for (ChatService.ChatSummary summary : chatService.listChatsForUser(userId)) {
            if (summary.chatId.equals(chatId)) {
                return new ChatSummaryView(summary);
            }
        }
        throw new IllegalStateException("chat not found right after modifying it");
    }
}
