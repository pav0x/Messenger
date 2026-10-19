// Клиент REST API: добавляет токен авторизации и превращает HTTP-ошибки в исключения.

const BASE_URL = '/api';

// Отправляет запрос, возвращает разобранный JSON (или null при пустом ответе).
async function request(path, options = {}) {
  const token = localStorage.getItem('token');

  const response = await fetch(BASE_URL + path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  });

  if (!response.ok) {
    // Сервер отдаёт текст ошибки JSON-строкой; на всякий случай поддержан и вид {message}.
    const body = await response.json().catch(() => null);
    const errorText = typeof body === 'string' ? body : body?.message;
    const error = new Error(errorText || `Ошибка запроса: ${response.status}`);
    error.status = response.status; // по статусу 401 приложение определяет, что нужно выйти
    throw error;
  }

  // Ответ на DELETE пустой, поэтому парсим только непустое тело.
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

// Методы API по группам: авторизация, чаты и группы, сообщения, пользователи.
export const api = {
  // Регистрация.
  register: (username, password, displayName) =>
    request('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password, display_name: displayName }),
    }),

  // Вход.
  login: (username, password) =>
    request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),

  // Список чатов.
  listChats: () => request('/chats'),

  // Создание группы.
  createGroup: (title, memberIds) =>
    request('/chats/group', {
      method: 'POST',
      body: JSON.stringify({ title, member_ids: memberIds }),
    }),

  // Переименование группы.
  renameGroup: (chatId, title) =>
    request(`/chats/${chatId}`, {
      method: 'PATCH',
      body: JSON.stringify({ title }),
    }),

  // Добавление участника.
  addMember: (chatId, userId) =>
    request(`/chats/${chatId}/members`, {
      method: 'POST',
      body: JSON.stringify({ user_id: userId }),
    }),

  // Удаление участника.
  removeMember: (chatId, memberId) =>
    request(`/chats/${chatId}/members/${memberId}`, { method: 'DELETE' }),

  // Удаление чата.
  deleteChat: (chatId) =>
    request(`/chats/${chatId}`, { method: 'DELETE' }),

  // Удаление сообщения.
  deleteMessage: (chatId, messageId) =>
    request(`/chats/${chatId}/messages/${messageId}`, { method: 'DELETE' }),

  // Поиск пользователя по логину.
  lookupUser: (username) => request(`/users/lookup?username=${encodeURIComponent(username)}`),

  // Страница истории сообщений.
  getMessages: (chatId, page = 0) =>
    request(`/chats/${chatId}/messages?page=${page}&limit=50`),
};
