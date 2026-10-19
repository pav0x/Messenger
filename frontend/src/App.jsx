import React, { useState, useCallback, useEffect } from 'react';
import { api } from './api.js';
import { useChatSocket } from './useChatSocket.js';
import './App.css';

// Цвета аватаров.
const AVATAR_COLORS = ['#2f5d62', '#5b7ea3', '#8c6bae', '#b5764f', '#4c9a73', '#a3555b'];

// Выбирает цвет аватара по id: для одного id цвет всегда один и тот же.
function colorForId(id) {
  const text = String(id ?? '');
  let hash = 0;
  for (let i = 0; i < text.length; i++) {
    hash = (hash * 31 + text.charCodeAt(i)) % AVATAR_COLORS.length;
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

// Круглый аватар с инициалом (для группы — значок).
function Avatar({ id, name, isGroup }) {
  const initial = isGroup ? '👥' : (name || '?').trim().charAt(0).toUpperCase();
  return (
    <div className={`avatar${isGroup ? ' is-group' : ''}`} style={{ background: colorForId(id) }}>
      {initial}
    </div>
  );
}

// Галочки статуса сообщения: отправляется, отправлено, доставлено, прочитано.
function StatusTicks({ status }) {
  if (status === 'read') return <span className="message-status is-read">✓✓</span>;
  if (status === 'delivered') return <span className="message-status is-sent-only">✓✓</span>;
  if (status === 'sending') return <span className="message-status is-sent-only">…</span>;
  return <span className="message-status is-sent-only">✓</span>; // 'sent'
}

// ---------- Экран входа/регистрации ----------

// Форма входа и регистрации. После успеха сохраняет токен и вызывает onAuth.
function AuthForm({ onAuth }) {
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState('');

  // Отправляет форму: регистрация или вход.
  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const result = isRegister
        ? await api.register(username, password, displayName || username)
        : await api.login(username, password);
      localStorage.setItem('token', result.token);
      localStorage.setItem('user_id', result.user_id);
      onAuth(result.token, result.user_id);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="auth-screen">
      <form onSubmit={submit} className="auth-card">
        <h2 className="auth-title">{isRegister ? 'Регистрация' : 'Вход'}</h2>

        <input className="field" placeholder="Имя пользователя" value={username}
               onChange={(e) => setUsername(e.target.value)} required />

        {isRegister && (
          <input className="field" placeholder="Отображаемое имя" value={displayName}
                 onChange={(e) => setDisplayName(e.target.value)} />
        )}

        <input className="field" type="password" placeholder="Пароль" value={password}
               onChange={(e) => setPassword(e.target.value)} required />

        <button type="submit" className="btn-primary">
          {isRegister ? 'Зарегистрироваться' : 'Войти'}
        </button>

        {error && <p className="auth-error">{error}</p>}

        <a href="#" className="auth-switch" onClick={(e) => { e.preventDefault(); setIsRegister(!isRegister); }}>
          {isRegister ? 'Уже есть аккаунт? Войти' : 'Нет аккаунта? Зарегистрироваться'}
        </a>
      </form>
    </div>
  );
}

// ---------- Экран чата ----------

// Основной экран: список чатов, переписка, управление группой.
// Чат в состоянии: { chat_id, is_group, peer_id, peer_name, peer_online, creator_id, members, unread_count }.
// members и creator_id есть только у групп. chat_id == null у личного чата, в котором ещё не было сообщений;
// настоящий chat_id приходит с эхом первого сообщения.
function ChatWindow({ token, userId, onLogout }) {
  const [chats, setChats] = useState([]);
  const [activeChat, setActiveChat] = useState(null);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');

  const [lookupName, setLookupName] = useState('');
  const [lookupError, setLookupError] = useState('');

  const [groupTitle, setGroupTitle] = useState('');
  const [groupUsernames, setGroupUsernames] = useState('');
  const [groupError, setGroupError] = useState('');

  const [showGroupSettings, setShowGroupSettings] = useState(false);
  const [renameValue, setRenameValue] = useState('');
  const [addMemberUsername, setAddMemberUsername] = useState('');
  const [groupSettingsError, setGroupSettingsError] = useState('');

  // Перезагружает список чатов; при 401 выходит из аккаунта.
  const refreshChats = useCallback(async () => {
    try {
      const serverChats = await api.listChats();
      setChats(serverChats);
    } catch (err) {
      if (err.status === 401) {
        // Токен невалиден или истёк — возвращаем на форму входа.
        onLogout();
        return;
      }
      throw err;
    }
  }, [onLogout]);

  useEffect(() => {
    refreshChats();
  }, [refreshChats]);

  // При смене чата сбрасывает панель настроек группы и поле названия.
  useEffect(() => {
    setShowGroupSettings(false);
    setGroupSettingsError('');
    setRenameValue(activeChat?.is_group ? activeChat.peer_name : '');
  }, [activeChat?.chat_id]);

  // Обрабатывает события от сервера: изменения чатов, удаления, прочтения, новые сообщения.
  const handleIncomingMessage = useCallback((wsMessage) => {
    // Изменение группы: перезагружаем список чатов.
    if (wsMessage.type === 'chat_updated') {
      refreshChats();
      return;
    }

    if (wsMessage.type === 'chat_deleted' || wsMessage.type === 'removed_from_chat') {
      setChats((prev) => prev.filter((c) => c.chat_id !== wsMessage.chat_id));
      setActiveChat((current) => (current?.chat_id === wsMessage.chat_id ? null : current));
      return;
    }

    if (wsMessage.type === 'message_deleted') {
      if (wsMessage.chat_id === activeChat?.chat_id) {
        setMessages((prev) => prev.filter((m) => m.id !== wsMessage.message_id));
      }
      return;
    }

    if (wsMessage.type === 'read_receipt') {
      if (wsMessage.chat_id !== activeChat?.chat_id) return;
      setMessages((prev) => prev.map((m) =>
        wsMessage.message_ids.includes(m.id) ? { ...m, status: 'read' } : m
      ));
      return;
    }

    if (wsMessage.type !== 'message') return;

    const isForOpenPendingChat = activeChat && !activeChat.chat_id
      && (wsMessage.from === activeChat.peer_id || wsMessage.from === userId);
    const belongsToOpenChat = wsMessage.chat_id === activeChat?.chat_id || isForOpenPendingChat;

    if (!belongsToOpenChat) {
      // Чат не открыт: обновляем только список (счётчик непрочитанных).
      refreshChats();
      return;
    }

    if (isForOpenPendingChat) {
      const confirmedChat = { ...activeChat, chat_id: wsMessage.chat_id };
      setActiveChat(confirmedChat);
      setChats((prev) => prev.map((c) => c.peer_id === confirmedChat.peer_id ? confirmedChat : c));
    }

    setMessages((prev) => {
      // Эхо своего сообщения: заменяем временную запись настоящей.
      if (wsMessage.client_id) {
        const index = prev.findIndex((m) => m.client_id === wsMessage.client_id);
        if (index !== -1) {
          const updated = [...prev];
          updated[index] = {
            id: wsMessage.message_id,
            sender_id: wsMessage.from,
            content: wsMessage.content,
            status: wsMessage.status,
          };
          return updated;
        }
      }

      return [...prev, {
        id: wsMessage.message_id,
        sender_id: wsMessage.from,
        content: wsMessage.content,
        status: wsMessage.status,
      }];
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeChat, userId, refreshChats]);

  const { connected, sendMessage } = useChatSocket(token, handleIncomingMessage);

  // При выборе чата загружает историю и сообщает серверу о прочтении.
  useEffect(() => {
    if (!activeChat?.chat_id) {
      setMessages([]);
      return;
    }
    api.getMessages(activeChat.chat_id).then((history) => setMessages(history));
    sendMessage({ type: 'read', chat_id: activeChat.chat_id });

    // Сразу обнуляем счётчик непрочитанных этого чата.
    setChats((prev) => prev.map((c) => c.chat_id === activeChat.chat_id ? { ...c, unread_count: 0 } : c));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeChat?.chat_id]);

  // Ищет пользователя по логину и открывает чат с ним.
  const startChatWithUsername = async () => {
    setLookupError('');
    if (!lookupName.trim()) return;

    try {
      const peer = await api.lookupUser(lookupName.trim());
      const existingChat = chats.find((c) => !c.is_group && c.peer_id === peer.id);

      if (existingChat) {
        setActiveChat(existingChat);
      } else {
        const newChat = {
          chat_id: null,
          is_group: false,
          peer_id: peer.id,
          peer_name: peer.display_name,
          peer_online: peer.is_online,
        };
        setChats((prev) => [...prev, newChat]);
        setActiveChat(newChat);
      }
      setLookupName('');
    } catch {
      setLookupError('Пользователь не найден');
    }
  };

  // Создаёт группу из введённых названия и логинов участников.
  const createGroup = async () => {
    setGroupError('');
    if (!groupTitle.trim() || !groupUsernames.trim()) return;

    try {
      const usernames = groupUsernames.split(',').map((u) => u.trim()).filter(Boolean);
      const memberIds = [];
      for (const username of usernames) {
        const peer = await api.lookupUser(username);
        memberIds.push(peer.id);
      }

      const newChat = await api.createGroup(groupTitle.trim(), memberIds);
      setChats((prev) => [...prev, newChat]);
      setActiveChat(newChat);
      setGroupTitle('');
      setGroupUsernames('');
    } catch {
      setGroupError('Не получилось — проверь имена пользователей');
    }
  };

  // Отправляет сообщение: показывает его сразу и шлёт на сервер по WebSocket.
  const send = () => {
    if (!draft.trim() || !activeChat) return;

    // client_id — временный id: по нему эхо сервера заменит эту запись.
    const clientId = `local-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const content = draft;

    setMessages((prev) => [...prev, {
      id: clientId,
      client_id: clientId,
      sender_id: userId,
      content,
      status: 'sending',
    }]);

    if (activeChat.chat_id) {
      sendMessage({ chat_id: activeChat.chat_id, content, client_id: clientId });
    } else {
      sendMessage({ to: activeChat.peer_id, content, client_id: clientId });
    }
    setDraft('');
  };

  // Удаляет своё сообщение у всех.
  const deleteMessage = async (message) => {
    if (!activeChat?.chat_id) return;
    if (!window.confirm('Удалить это сообщение для всех участников чата?')) return;

    try {
      await api.deleteMessage(activeChat.chat_id, message.id);
      setMessages((prev) => prev.filter((m) => m.id !== message.id));
    } catch (err) {
      alert(err.message);
    }
  };

  // Удаляет текущий чат целиком.
  const deleteChat = async () => {
    if (!activeChat) return;
    const warning = activeChat.is_group
      ? 'Удалить группу целиком? Это действие нельзя отменить.'
      : 'Удалить переписку целиком? Это действие нельзя отменить.';
    if (!window.confirm(warning)) return;

    try {
      if (activeChat.chat_id) {
        await api.deleteChat(activeChat.chat_id);
      }
      setChats((prev) => prev.filter((c) => c.chat_id !== activeChat.chat_id));
      setActiveChat(null);
    } catch (err) {
      alert(err.message);
    }
  };

  // Переименовывает группу.
  const submitRename = async () => {
    setGroupSettingsError('');
    if (!renameValue.trim() || !activeChat?.chat_id) return;

    try {
      const updated = await api.renameGroup(activeChat.chat_id, renameValue.trim());
      setActiveChat(updated);
      setChats((prev) => prev.map((c) => c.chat_id === updated.chat_id ? updated : c));
    } catch (err) {
      setGroupSettingsError(err.message);
    }
  };

  // Добавляет участника в группу по логину.
  const submitAddMember = async () => {
    setGroupSettingsError('');
    if (!addMemberUsername.trim() || !activeChat?.chat_id) return;

    try {
      const peer = await api.lookupUser(addMemberUsername.trim());
      const updated = await api.addMember(activeChat.chat_id, peer.id);
      setActiveChat(updated);
      setChats((prev) => prev.map((c) => c.chat_id === updated.chat_id ? updated : c));
      setAddMemberUsername('');
    } catch {
      setGroupSettingsError('Не удалось добавить — проверь username');
    }
  };

  // Удаляет участника из группы.
  const removeMemberFromGroup = async (memberId) => {
    if (!activeChat?.chat_id) return;
    try {
      const updated = await api.removeMember(activeChat.chat_id, memberId);
      setActiveChat(updated);
      setChats((prev) => prev.map((c) => c.chat_id === updated.chat_id ? updated : c));
    } catch (err) {
      setGroupSettingsError(err.message);
    }
  };

  // Является ли текущий пользователь админом открытой группы.
  const isGroupAdmin = activeChat?.is_group && activeChat.creator_id === userId;

  return (
    <div className="chat-app">
      <div className={`sidebar${activeChat ? ' is-hidden-on-mobile' : ''}`}>
        <div className="connection-badge">
          <span className={`connection-dot${connected ? ' is-connected' : ''}`} />
          <span className="connection-badge-text">{connected ? 'В сети' : 'Переподключение...'}</span>
          <button className="logout-button" onClick={onLogout}>Выйти</button>
        </div>

        <div className="sidebar-panel">
          <p className="sidebar-panel-title">Новый чат</p>
          <input
            className="field field-compact"
            placeholder="Найти по username"
            value={lookupName}
            onChange={(e) => setLookupName(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && startChatWithUsername()}
          />
          {lookupError && <p className="field-error">{lookupError}</p>}
        </div>

        <div className="sidebar-panel">
          <p className="sidebar-panel-title">Новая группа</p>
          <input
            className="field field-compact"
            placeholder="Название группы"
            value={groupTitle}
            onChange={(e) => setGroupTitle(e.target.value)}
          />
          <input
            className="field field-compact"
            placeholder="Участники через запятую"
            value={groupUsernames}
            onChange={(e) => setGroupUsernames(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && createGroup()}
          />
          <button className="btn-secondary" onClick={createGroup}>Создать группу</button>
          {groupError && <p className="field-error">{groupError}</p>}
        </div>

        <div className="chat-list">
          {chats.map((chat) => (
            <div
              key={chat.chat_id ?? chat.peer_id}
              onClick={() => setActiveChat(chat)}
              className={`chat-row${activeChat?.chat_id === chat.chat_id && activeChat?.peer_id === chat.peer_id ? ' is-active' : ''}`}
            >
              <Avatar id={chat.chat_id ?? chat.peer_id} name={chat.peer_name} isGroup={chat.is_group} />
              <span className="chat-row-name">{chat.peer_name}</span>
              {!chat.is_group && chat.peer_online && <span className="chat-row-online-dot" />}
              {chat.unread_count > 0 && <span className="chat-row-unread-badge">{chat.unread_count}</span>}
            </div>
          ))}
        </div>
      </div>

      <div className="conversation">
        {activeChat ? (
          <>
            <div className="conversation-header">
              <button className="back-to-list" onClick={() => setActiveChat(null)} aria-label="Назад к списку чатов">‹</button>
              <Avatar id={activeChat.chat_id ?? activeChat.peer_id} name={activeChat.peer_name} isGroup={activeChat.is_group} />
              <div className="conversation-header-info">
                <div className="conversation-header-name">{activeChat.peer_name}</div>
                <div className="conversation-header-status">
                  {activeChat.is_group ? `${activeChat.members?.length ?? ''} участников` : (activeChat.peer_online ? 'в сети' : 'не в сети')}
                </div>
              </div>
              {isGroupAdmin && (
                <button className="icon-button" onClick={() => setShowGroupSettings((v) => !v)} aria-label="Настройки группы">⚙</button>
              )}
              <button className="icon-button is-danger" onClick={deleteChat} aria-label="Удалить чат">🗑</button>
            </div>

            {showGroupSettings && isGroupAdmin && (
              <div className="group-settings-panel">
                <div className="group-settings-row">
                  <input className="field field-compact" value={renameValue}
                         onChange={(e) => setRenameValue(e.target.value)} />
                  <button className="btn-secondary" onClick={submitRename}>Переименовать</button>
                </div>

                <p className="sidebar-panel-title">Участники</p>
                <ul className="group-member-list">
                  {activeChat.members?.map((member) => (
                    <li key={member.id} className="group-member-row">
                      <span>{member.display_name}{member.id === activeChat.creator_id ? ' (админ)' : ''}</span>
                      {member.id !== activeChat.creator_id && (
                        <button className="icon-button is-danger" onClick={() => removeMemberFromGroup(member.id)}
                                aria-label="Удалить участника">✕</button>
                      )}
                    </li>
                  ))}
                </ul>

                <div className="group-settings-row">
                  <input className="field field-compact" placeholder="Добавить по username"
                         value={addMemberUsername}
                         onChange={(e) => setAddMemberUsername(e.target.value)}
                         onKeyDown={(e) => e.key === 'Enter' && submitAddMember()} />
                  <button className="btn-secondary" onClick={submitAddMember}>Добавить</button>
                </div>

                {groupSettingsError && <p className="field-error">{groupSettingsError}</p>}
              </div>
            )}

            <div className="message-list">
              {messages.map((message, i) => {
                const isMine = message.sender_id === userId;
                const senderName = activeChat.is_group && !isMine
                  ? activeChat.members?.find((m) => m.id === message.sender_id)?.display_name
                  : null;
                const canDelete = isMine && !String(message.id).startsWith('local-');
                return (
                  <div key={message.id ?? i} className={`message-row ${isMine ? 'is-mine' : 'is-theirs'}`}>
                    {senderName && <div className="message-sender-name">{senderName}</div>}
                    <div className="message-bubble-wrap">
                      <div className="message-bubble">{message.content}</div>
                      {canDelete && (
                        <button className="message-delete-button" onClick={() => deleteMessage(message)}
                                aria-label="Удалить сообщение">✕</button>
                      )}
                    </div>
                    {isMine && <StatusTicks status={message.status} />}
                  </div>
                );
              })}
            </div>

            <div className="composer">
              <input
                className="field composer-input"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && send()}
                placeholder="Сообщение..."
              />
              <button className="composer-send" onClick={send}>Отправить</button>
            </div>
          </>
        ) : (
          <div className="conversation-empty">Выберите чат слева</div>
        )}
      </div>
    </div>
  );
}

// ---------- Корневой компонент ----------

// Показывает форму входа или экран чата в зависимости от наличия токена.
export default function App() {
  const [token, setToken] = useState(localStorage.getItem('token'));
  const [userId, setUserId] = useState(localStorage.getItem('user_id'));

  // Выход: удаляет токен и id из localStorage.
  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user_id');
    setToken(null);
    setUserId(null);
  };

  if (!token) {
    return <AuthForm onAuth={(t, uid) => { setToken(t); setUserId(uid); }} />;
  }
  return <ChatWindow token={token} userId={userId} onLogout={logout} />;
}
