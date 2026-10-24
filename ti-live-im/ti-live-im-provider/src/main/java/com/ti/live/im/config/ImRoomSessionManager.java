package com.ti.live.im.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ImRoomSessionManager {

    /**
     * roomId -> (userId -> WebSocketSession)
     */
    private final Map<Long, Map<Long, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    /**
     * userId -> WebSocketSession
     */
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    public void addSession(Long roomId, Long userId, WebSocketSession session) {
        // 同一用户重复连接时, 先关闭旧连接, 防止会话泄漏
        WebSocketSession oldSession = userSessions.put(userId, session);
        if (oldSession != null && oldSession.isOpen() && oldSession != session) {
            try {
                oldSession.close(CloseStatus.NORMAL);
            } catch (Exception e) {
                log.error("关闭旧连接失败: userId={}", userId, e);
            }
        }
        roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(userId, session);
        log.info("用户{}加入房间{}, 当前在线人数: {}", userId, roomId, getOnlineCount(roomId));
    }

    public void removeSession(Long roomId, Long userId) {
        Map<Long, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(userId);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
        userSessions.remove(userId);
        log.info("用户{}离开房间{}, 当前在线人数: {}", userId, roomId, getOnlineCount(roomId));
    }

    public Map<Long, WebSocketSession> getRoomSessions(Long roomId) {
        return roomSessions.getOrDefault(roomId, new ConcurrentHashMap<>());
    }

    public int getOnlineCount(Long roomId) {
        Map<Long, WebSocketSession> sessions = roomSessions.get(roomId);
        return sessions != null ? sessions.size() : 0;
    }

    public void broadcast(Long roomId, String message, Long excludeUserId) {
        Map<Long, WebSocketSession> sessions = getRoomSessions(roomId);
        for (Map.Entry<Long, WebSocketSession> entry : sessions.entrySet()) {
            if (excludeUserId != null && excludeUserId.equals(entry.getKey())) {
                continue;
            }
            WebSocketSession session = entry.getValue();
            if (session.isOpen()) {
                try {
                    session.sendMessage(new org.springframework.web.socket.TextMessage(message));
                } catch (Exception e) {
                    log.error("发送消息失败", e);
                }
            }
        }
    }

    public void sendToUser(Long userId, String message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new org.springframework.web.socket.TextMessage(message));
            } catch (Exception e) {
                log.error("发送消息给用户失败: {}", userId, e);
            }
        }
    }
}
