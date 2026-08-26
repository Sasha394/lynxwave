const { WebSocketServer } = require('ws');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = process.env.PORT || 8080;
const HOST = process.env.HOST || '0.0.0.0';
const API_KEY = 'LynxWave_secret_key_1488';
const SERVER_VERSION = 2;
const MIN_CLIENT_VERSION = 1;
const XOR_KEY = (0x15 ^ 0x3) ^ 2;

const USERS_FILE = path.join(__dirname, 'users.json');
const users = loadUsers();

function loadUsers() {
    try {
        if (fs.existsSync(USERS_FILE)) {
            return JSON.parse(fs.readFileSync(USERS_FILE, 'utf8'));
        }
    } catch (e) {
        console.error('Failed to load users:', e.message);
    }
    return {};
}

function saveUsers() {
    try {
        fs.writeFileSync(USERS_FILE, JSON.stringify(users, null, 2));
    } catch (e) {
        console.error('Failed to save users:', e.message);
    }
}

function hashPassword(password) {
    return crypto.createHash('sha256').update(password).digest('hex');
}

function cypher(input) {
    const buf = Buffer.from(input, 'utf8');
    for (let i = 0; i < buf.length; i++) buf[i] ^= XOR_KEY;
    return buf.toString('utf8');
}

function decypher(input) {
    return cypher(input);
}

const clients = new Set();
const parties = new Map();

function send(ws, payload) {
    if (ws.readyState !== ws.OPEN) return;
    try {
        ws.send(cypher(JSON.stringify(payload)));
    } catch (e) {
        console.error('send error:', e.message);
    }
}

function broadcast(payload, exclude = null) {
    for (const client of clients) {
        if (client === exclude) continue;
        send(client, payload);
    }
}

function systemMessage(ws, message) {
    send(ws, { type: 'system', message });
}

function roleOf(ws) {
    if (ws.isAdmin) return 'admin';
    if (ws.isModerator) return 'moderator';
    if (ws.nickname) return 'user';
    return 'guest';
}

function getOnlineUsers() {
    // Онлайн-пользователи (с активным сокетом)
    const online = new Map();
    for (const c of clients) {
        const nick = c.nickname || c.author;
        if (!nick) continue;
        const stored = c.username ? users[c.username.toLowerCase()] : null;
        online.set(nick.toLowerCase(), {
            nickname: nick,
            gameUsername: c.player || (stored ? stored.lastPlayer : null) || null,
            clientLabel: c.client || (stored ? stored.lastClient : null) || 'LynxWave',
            role: roleOf(c),
            server: c.server || (stored ? stored.lastServer : null) || null,
            online: true,
        });
    }

    // Зарегистрированные, но не онлайн
    const offline = [];
    for (const u of Object.values(users)) {
        if (online.has(u.username.toLowerCase())) continue;
        offline.push({
            nickname: u.nickname || u.username,
            gameUsername: null,
            clientLabel: u.lastClient || 'LynxWave',
            role: u.isAdmin ? 'admin' : (u.isModerator ? 'moderator' : 'user'),
            server: u.lastServer || null,
            online: false,
        });
    }

    return [...online.values(), ...offline];
}

function presenceSnapshot() {
    const entries = [];
    for (const c of clients) {
        if (!c.author) continue;
        entries.push({
            author: c.author,
            player: c.player || null,
            label: c.client || 'LynxWave',
            status: 'online',
        });
    }
    return { type: 'client_presence', entries, full: true };
}

function broadcastPresenceUpdate(client, status = 'online') {
    broadcast({
        type: 'client_presence',
        entries: [{
            author: client.author,
            player: client.player || null,
            label: client.client || 'LynxWave',
            status,
        }],
    });
}

function getOrCreateParty(key) {
    if (!parties.has(key)) {
        parties.set(key, { key, members: new Map(), markers: [] });
    }
    return parties.get(key);
}

function partyBroadcast(party, payload, excludeAuthor = null) {
    for (const c of clients) {
        if (!c.partyKey || c.partyKey !== party.key) continue;
        if (excludeAuthor && c.author === excludeAuthor) continue;
        send(c, payload);
    }
}

const wss = new WebSocketServer({ host: HOST, port: PORT });

wss.on('listening', () => {
    console.log(`[IRC] Server listening on ws://${HOST}:${PORT}`);
    console.log(`[IRC] Connect clients to ws://localhost:${PORT}`);
});

wss.on('connection', (ws, req) => {
    console.log(`[IRC] Connection from ${req.socket.remoteAddress}`);
    clients.add(ws);

    ws.author = null;
    ws.player = null;
    ws.client = 'LynxWave';
    ws.nickname = null;
    ws.username = null;
    ws.isModerator = false;
    ws.isAdmin = false;
    ws.partyKey = null;
    ws.lastMessageAt = 0;

    send(ws, { type: 'system', message: 'CONNECTED' });
    send(ws, { type: 'version_info', serverVersion: SERVER_VERSION, minClientVersion: MIN_CLIENT_VERSION });

    ws.on('message', (raw) => {
        let text;
        try {
            text = decypher(raw.toString('utf8'));
        } catch (e) {
            console.warn('[IRC] decypher fail:', e.message);
            return;
        }

        console.log('[IRC] RAW <-', text.slice(0, 200));

        let json;
        try {
            json = JSON.parse(text);
        } catch (e) {
            console.warn('[IRC] Bad JSON:', text.slice(0, 200));
            return;
        }

        if (!json || typeof json !== 'object') return;

        if (json.api_key !== API_KEY) {
            systemMessage(ws, 'INVALID_API_KEY');
            return;
        }

        // Клиентский sendMessage() не ставит type — это обычный текст
        const type = json.type || (typeof json.message === 'string' ? 'text' : null);
        if (!type) return;
        try {
            handleMessage(ws, type, json);
        } catch (e) {
            console.error('[IRC] handler error:', e);
        }
    });

    ws.on('close', () => {
        clients.delete(ws);
        if (ws.author) {
            broadcastPresenceUpdate(ws, 'offline');
        }
        if (ws.partyKey) {
            const party = parties.get(ws.partyKey);
            if (party) {
                party.members.delete(ws.author);
                if (party.members.size === 0) {
                    parties.delete(ws.partyKey);
                }
            }
        }
        broadcastOnlineUsers();
        refreshAllFriends();
        console.log('[IRC] Client disconnected');
    });

    ws.on('error', (err) => {
        console.error('[IRC] socket error:', err.message);
    });
});

function handleMessage(ws, type, json) {
    switch (type) {
        case 'presence':
            return handlePresence(ws, json);
        case 'text':
            return handleText(ws, json);
        case 'private_message':
            return handlePrivateMessage(ws, json);
        case 'register':
            return handleRegister(ws, json);
        case 'login':
            return handleLogin(ws, json);
        case 'party_pos':
            return handlePartyPos(ws, json);
        case 'get_online_users':
            return handleGetOnlineUsers(ws, json);
        case 'friend_request':
            return handleFriendRequest(ws, json);
        case 'friend_action':
            return handleFriendAction(ws, json);
        case 'get_friends':
            return handleGetFriends(ws, json);
        case 'moderator_command':
            return handleModeratorCommand(ws, json);
        case 'raffle_command':
            return handleRaffleCommand(ws, json);
        default:
            console.log('[IRC] Unhandled type:', type);
    }
}

function handlePresence(ws, json) {
    const clientVersion = json.client_version || 1;
    if (clientVersion < MIN_CLIENT_VERSION) {
        systemMessage(ws, `VERSION_REQUIRED: Требуется версия ${MIN_CLIENT_VERSION}+`);
        ws.close();
        return;
    }
    ws.author = json.author || ws.author;
    ws.player = json.player || ws.player;
    ws.server = json.server || ws.server || null;
    ws.client = ws.client || 'LynxWave';

    // Запомним последний сервер/клиент/ник в профиль
    if (ws.username && users[ws.username.toLowerCase()]) {
        const u = users[ws.username.toLowerCase()];
        if (ws.server) u.lastServer = ws.server;
        if (ws.client) u.lastClient = ws.client;
        if (ws.player) u.lastPlayer = ws.player;
        u.lastSeenAt = Date.now();
        saveUsers();
    }

    if (json.snapshot) {
        send(ws, presenceSnapshot());
    }
    broadcastPresenceUpdate(ws, 'online');
    broadcastOnlineUsers();
    refreshAllFriends();
}

function broadcastOnlineUsers() {
    const payload = { type: 'online_users_list', users: getOnlineUsers() };
    for (const c of clients) {
        if (c.nickname) send(c, payload);
    }
}

function handleText(ws, json) {
    if (!ws.nickname) {
        systemMessage(ws, 'AUTHENTICATION_REQUIRED');
        return;
    }
    const now = Date.now();
    if (now - ws.lastMessageAt < 800) {
        systemMessage(ws, 'WAIT');
        return;
    }
    ws.lastMessageAt = now;

    if (json.server && json.server !== ws.server) {
        ws.server = json.server;
        if (ws.username && users[ws.username.toLowerCase()]) {
            users[ws.username.toLowerCase()].lastServer = json.server;
            saveUsers();
        }
        broadcastOnlineUsers();
    }

    const message = (json.message || '').toString();
    if (message.length > 500) {
        systemMessage(ws, 'MESSAGE_TOO_LONG');
        return;
    }

    const payload = {
        type: 'text',
        message,
        author: ws.nickname,
        client: json.client || ws.client || 'LynxWave',
        prefix: json.prefix || 'Default',
        server: json.server || null,
    };
    broadcast(payload);
    console.log(`[IRC] <${ws.nickname}> ${message}`);
}

function handlePrivateMessage(ws, json) {
    if (!ws.nickname) {
        systemMessage(ws, 'AUTHENTICATION_REQUIRED');
        return;
    }
    const target = (json.target || '').toLowerCase();
    const message = (json.message || '').toString();
    let delivered = false;
    for (const c of clients) {
        if (c.nickname && c.nickname.toLowerCase() === target) {
            send(c, { type: 'private_message', author: ws.nickname, message });
            delivered = true;
        }
    }
    if (!delivered) {
        systemMessage(ws, 'PRIVATE_MESSAGE_FAILED: Пользователь не в сети');
    }
}

function handleRegister(ws, json) {
    const username = (json.username || '').trim();
    const password = (json.password || '').toString();
    const nickname = (json.nickname || username).trim();
    if (username.length < 3 || password.length < 4) {
        systemMessage(ws, 'REGISTER_FAILED: Слишком короткий логин или пароль');
        return;
    }
    if (users[username.toLowerCase()]) {
        systemMessage(ws, 'REGISTER_FAILED: Пользователь уже существует');
        return;
    }
    users[username.toLowerCase()] = {
        username,
        nickname,
        passwordHash: hashPassword(password),
        isModerator: false,
        isAdmin: false,
        createdAt: Date.now(),
    };
    saveUsers();
    systemMessage(ws, 'REGISTER_SUCCESS: Аккаунт создан, выполняется вход');
    finalizeLogin(ws, users[username.toLowerCase()]);
}

function handleLogin(ws, json) {
    const username = (json.username || '').trim().toLowerCase();
    const password = (json.password || '').toString();
    const user = users[username];
    if (!user || user.passwordHash !== hashPassword(password)) {
        systemMessage(ws, 'LOGIN_FAILED: Неверный логин или пароль');
        return;
    }
    finalizeLogin(ws, user);
}

function finalizeLogin(ws, user) {
    ws.username = user.username;
    ws.nickname = user.nickname;
    ws.isModerator = !!user.isModerator;
    ws.isAdmin = !!user.isAdmin;
    send(ws, {
        type: 'login_success',
        message: 'Вход выполнен успешно',
        user: {
            username: user.username,
            nickname: user.nickname,
            isModerator: ws.isModerator,
            isAdmin: ws.isAdmin,
        },
    });
    broadcastOnlineUsers();
    sendFriendsList(ws);
    refreshAllFriends();
}

function handlePartyPos(ws, json) {
    if (!ws.nickname) return;
    const key = json.party_key || json.key;
    if (!key) return;
    const party = getOrCreateParty(key);
    ws.partyKey = key;
    party.members.set(ws.author, {
        member: ws.nickname,
        member_mc: ws.player,
        position: {
            x: json.x,
            y: json.y,
            z: json.z,
            timestamp: Date.now(),
        },
    });
    const positions = Array.from(party.members.values());
    partyBroadcast(party, { type: 'party_pos', positions });
}

function handleGetOnlineUsers(ws) {
    send(ws, { type: 'online_users_list', users: getOnlineUsers() });
}

// ---------- FRIENDS ----------

function findUserByNickname(nick) {
    if (!nick) return null;
    const lower = nick.toLowerCase();
    if (users[lower]) return users[lower];
    for (const u of Object.values(users)) {
        if ((u.nickname || u.username || '').toLowerCase() === lower) return u;
    }
    return null;
}

function onlineSocketsByNickname(nick) {
    const lower = (nick || '').toLowerCase();
    const out = [];
    for (const c of clients) {
        if (c.nickname && c.nickname.toLowerCase() === lower) out.push(c);
    }
    return out;
}

function ensureFriendArrays(u) {
    if (!Array.isArray(u.friends)) u.friends = [];
    if (!Array.isArray(u.friendRequests)) u.friendRequests = [];
}

function friendInfo(nick) {
    const fu = findUserByNickname(nick);
    const sock = onlineSocketsByNickname(nick)[0];
    if (sock) {
        return {
            nickname: fu ? (fu.nickname || fu.username) : nick,
            gameUsername: sock.player || null,
            clientLabel: sock.client || 'LynxWave',
            role: roleOf(sock),
            server: sock.server || null,
            online: true,
        };
    }
    return {
        nickname: fu ? (fu.nickname || fu.username) : nick,
        gameUsername: null,
        clientLabel: fu ? (fu.lastClient || 'LynxWave') : 'LynxWave',
        role: fu ? (fu.isAdmin ? 'admin' : (fu.isModerator ? 'moderator' : 'user')) : 'user',
        server: fu ? (fu.lastServer || null) : null,
        online: false,
    };
}

function sendFriendsList(ws) {
    if (!ws.username) return;
    const u = users[ws.username.toLowerCase()];
    if (!u) return;
    ensureFriendArrays(u);
    const friends = u.friends.map(friendInfo);
    send(ws, { type: 'friends_list', friends, requests: u.friendRequests.slice() });
}

function refreshAllFriends() {
    for (const c of clients) {
        if (c.nickname) sendFriendsList(c);
    }
}

function addFriendBoth(aNick, bNick) {
    const a = findUserByNickname(aNick);
    const b = findUserByNickname(bNick);
    if (!a || !b) return;
    ensureFriendArrays(a);
    ensureFriendArrays(b);
    const aName = a.nickname || a.username;
    const bName = b.nickname || b.username;
    if (!a.friends.some(n => n.toLowerCase() === bName.toLowerCase())) a.friends.push(bName);
    if (!b.friends.some(n => n.toLowerCase() === aName.toLowerCase())) b.friends.push(aName);
    a.friendRequests = a.friendRequests.filter(n => n.toLowerCase() !== bName.toLowerCase());
    b.friendRequests = b.friendRequests.filter(n => n.toLowerCase() !== aName.toLowerCase());
    saveUsers();
    for (const c of onlineSocketsByNickname(aName)) { send(c, { type: 'friend_added', nickname: bName }); sendFriendsList(c); }
    for (const c of onlineSocketsByNickname(bName)) { send(c, { type: 'friend_added', nickname: aName }); sendFriendsList(c); }
}

function handleFriendRequest(ws, json) {
    if (!ws.nickname) { systemMessage(ws, 'AUTHENTICATION_REQUIRED'); return; }
    const target = (json.target || '').trim();
    if (!target) { systemMessage(ws, 'FRIEND_FAILED: Укажите ник игрока'); return; }
    if (target.toLowerCase() === ws.nickname.toLowerCase()) { systemMessage(ws, 'FRIEND_FAILED: Нельзя добавить самого себя'); return; }
    const targetUser = findUserByNickname(target);
    if (!targetUser) { systemMessage(ws, 'FRIEND_FAILED: Игрок не найден'); return; }
    const me = users[ws.username.toLowerCase()];
    ensureFriendArrays(me);
    ensureFriendArrays(targetUser);
    const myNick = ws.nickname;
    const targetNick = targetUser.nickname || targetUser.username;

    if (me.friends.some(n => n.toLowerCase() === targetNick.toLowerCase())) {
        systemMessage(ws, 'FRIEND_INFO: ' + targetNick + ' уже у вас в друзьях');
        return;
    }
    // Если этот игрок уже присылал вам заявку — сразу принимаем (взаимность)
    if (me.friendRequests.some(n => n.toLowerCase() === targetNick.toLowerCase())) {
        me.friendRequests = me.friendRequests.filter(n => n.toLowerCase() !== targetNick.toLowerCase());
        addFriendBoth(myNick, targetNick);
        systemMessage(ws, 'FRIEND_INFO: ' + targetNick + ' добавлен в друзья');
        return;
    }
    if (targetUser.friendRequests.some(n => n.toLowerCase() === myNick.toLowerCase())) {
        systemMessage(ws, 'FRIEND_INFO: Заявка уже отправлена игроку ' + targetNick);
        return;
    }
    targetUser.friendRequests.push(myNick);
    saveUsers();
    systemMessage(ws, 'FRIEND_INFO: Заявка отправлена игроку ' + targetNick);
    for (const c of onlineSocketsByNickname(targetNick)) {
        send(c, { type: 'friend_request', author: myNick });
        sendFriendsList(c);
    }
}

function handleFriendAction(ws, json) {
    if (!ws.nickname) { systemMessage(ws, 'AUTHENTICATION_REQUIRED'); return; }
    const me = users[ws.username.toLowerCase()];
    if (!me) return;
    ensureFriendArrays(me);
    const target = (json.target || '').trim();
    const action = (json.action || '').toLowerCase();
    if (!target) return;

    if (action === 'accept') {
        const idx = me.friendRequests.findIndex(n => n.toLowerCase() === target.toLowerCase());
        if (idx < 0) { systemMessage(ws, 'FRIEND_FAILED: Заявка не найдена'); return; }
        me.friendRequests.splice(idx, 1);
        saveUsers();
        addFriendBoth(ws.nickname, target);
        sendFriendsList(ws);
    } else if (action === 'decline') {
        me.friendRequests = me.friendRequests.filter(n => n.toLowerCase() !== target.toLowerCase());
        saveUsers();
        systemMessage(ws, 'FRIEND_INFO: Заявка от ' + target + ' отклонена');
        sendFriendsList(ws);
    } else if (action === 'remove') {
        me.friends = me.friends.filter(n => n.toLowerCase() !== target.toLowerCase());
        const other = findUserByNickname(target);
        if (other) {
            ensureFriendArrays(other);
            other.friends = other.friends.filter(n => n.toLowerCase() !== ws.nickname.toLowerCase());
        }
        saveUsers();
        for (const c of onlineSocketsByNickname(target)) { send(c, { type: 'friend_removed', nickname: ws.nickname }); sendFriendsList(c); }
        sendFriendsList(ws);
    }
}

function handleGetFriends(ws) {
    sendFriendsList(ws);
}

function handleModeratorCommand(ws, json) {
    if (!ws.isModerator && !ws.isAdmin) {
        systemMessage(ws, 'PERMISSION_DENIED: У вас нет прав модератора');
        return;
    }
    send(ws, { type: 'moderator_response', success: true, message: `Команда "${json.action || 'unknown'}" принята (заглушка)` });
}

function handleRaffleCommand(ws, json) {
    if (!ws.isAdmin) {
        systemMessage(ws, 'PERMISSION_DENIED: Нужны права администратора');
        return;
    }
    if (json.action === 'start') {
        broadcast({
            type: 'raffle_start',
            message: 'Начался розыгрыш!',
            prize: json.prize || 'Приз',
            durationSeconds: json.duration || 60,
        });
    } else if (json.action === 'end') {
        broadcast({
            type: 'raffle_end',
            message: 'Розыгрыш завершён',
            winner: json.winner || null,
            prize: json.prize || 'Приз',
            participantsCount: json.participants || 0,
        });
    }
}

process.on('SIGINT', () => {
    console.log('\n[IRC] Shutting down...');
    wss.close(() => process.exit(0));
});
