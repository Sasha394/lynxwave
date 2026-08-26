# LynxWave IRC Local Server

Локальный WebSocket-сервер для модуля IRC из NexusWarden.

## Запуск

```bash
cd irc-server
npm install
npm start
```

Сервер слушает `ws://0.0.0.0:8080` (порт меняется через `PORT=9090 npm start`).

## Подключение клиента

В файле [src/main/java/dev/client/modules/core/misc/IRC.java](../src/main/java/dev/client/modules/core/misc/IRC.java#L62) поменяй:

```java
String SERVER_URI = "ws://localhost:8080";
```

(было `wss://irc-server-production-a550.up.railway.app`)

После пересборки чита включи модуль IRC — должно появиться `Успешное подключение к серверу!`.

## Что поддерживается

- `presence` / `client_presence` — список онлайна, heartbeat
- `register` / `login` — регистрация и вход (хранится в `users.json`, пароли SHA-256)
- `text` — общий чат
- `private_message` — ЛС
- `party_pos` — координаты пати
- `get_online_users` — список онлайна
- `moderator_command` / `raffle_command` — заглушки (отвечают, но без реальной логики мутов)

API-ключ зашит в [server.js](server.js) и совпадает с клиентским (`LynxWave_secret_key_1488`).

## Сделать первого админа

Зарегистрируйся через клиент, затем вручную в `users.json` поставь:

```json
"твой_логин": {
  ...
  "isAdmin": true,
  "isModerator": true
}
```

И перезайди.
