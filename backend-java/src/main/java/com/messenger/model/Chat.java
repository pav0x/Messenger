package com.messenger.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Чат: личный (двое участников) или групповой (от двух и больше).
public class Chat {
    private final String id;
    private final boolean group;
    private final String title;      // null для личных чатов
    private final String creatorId;  // null для личных чатов
    private final List<String> memberIds;

    // Конструктор для личного чата.
    public Chat(String id, String userAId, String userBId) {
        this.id = id;
        this.group = false;
        this.title = null;
        this.creatorId = null;
        this.memberIds = List.of(userAId, userBId);
    }

    // Конструктор для группового чата.
    public Chat(String id, String title, String creatorId, List<String> memberIds) {
        this.id = id;
        this.group = true;
        this.title = title;
        this.creatorId = creatorId;
        this.memberIds = new ArrayList<>(memberIds);
    }

    public String getId() { return id; }
    public boolean isGroup() { return group; }
    public String getTitle() { return title; }
    public String getCreatorId() { return creatorId; }

    public List<String> getMemberIds() {
        return Collections.unmodifiableList(memberIds);
    }

    // Состоит ли пользователь в чате.
    public boolean hasParticipant(String userId) {
        return memberIds.contains(userId);
    }

    // Является ли пользователь создателем (админом) группы. У личных чатов всегда false.
    public boolean isCreator(String userId) {
        return creatorId != null && creatorId.equals(userId);
    }

    // Собеседник в личном чате. Для группового чата бросает исключение.
    public String getPeerId(String userId) {
        if (group) {
            throw new IllegalStateException("group chat has no single peer, use getMemberIds()");
        }
        for (String memberId : memberIds) {
            if (!memberId.equals(userId)) {
                return memberId;
            }
        }
        throw new IllegalArgumentException("user is not a participant of this chat");
    }

    // Все участники, кроме указанного пользователя.
    public List<String> getOtherMemberIds(String userId) {
        List<String> result = new ArrayList<>();
        for (String memberId : memberIds) {
            if (!memberId.equals(userId)) {
                result.add(memberId);
            }
        }
        return result;
    }
}
