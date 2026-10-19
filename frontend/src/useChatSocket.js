import { useEffect, useRef, useCallback, useState } from 'react';

// Пауза перед переподключением.
const RECONNECT_DELAY_MS = 3000;

export function useChatSocket(token, onMessage) {
  const wsRef = useRef(null);
  const [connected, setConnected] = useState(false);

  // В ref хранится актуальный onMessage: так connect зависит только от token и соединение не пересоздаётся.
  const onMessageRef = useRef(onMessage);
  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  // Открывает соединение; при обрыве переподключается.
  const connect = useCallback(() => {
    if (!token) return;
    const ws = new WebSocket(`ws://${location.host}/ws/chat?token=${token}`);

    ws.onopen = () => setConnected(true);
    ws.onmessage = (event) => onMessageRef.current(JSON.parse(event.data));
    ws.onclose = () => {
      setConnected(false);
      setTimeout(connect, RECONNECT_DELAY_MS);
    };

    wsRef.current = ws;
  }, [token]);

  useEffect(() => {
    connect();
    return () => wsRef.current?.close();
  }, [connect]);

  // Отправляет объект как JSON: {to|chat_id, content, client_id} или {type: 'read', chat_id}.
  const sendMessage = useCallback((payload) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(payload));
    }
  }, []);

  return { connected, sendMessage };
}
