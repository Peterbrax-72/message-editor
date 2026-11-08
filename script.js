const STORAGE_KEY = 'message-editor-messages-v2';
const OLD_STORAGE_KEY = 'message-editor-messages';
const CONTACTS_KEY = 'message-editor-contacts-v1';
const SAMPLE_CONTACTS = ['Mum', 'Dad', 'Alex', 'Work Group', 'Neighbour'];

// ---------- state ----------
let messages = loadMessages();
let contacts = loadContacts();
let currentFolder = 'inbox';
let editingId = null;

// ---------- DOM ----------
const folderBtns = document.querySelectorAll('.folder-btn');
const listTitle = document.getElementById('list-title');
const messageList = document.getElementById('message-list');
const form = document.getElementById('message-form');
const editIdInput = document.getElementById('edit-id');
const recipientInput = document.getElementById('recipient');
const bodyInput = document.getElementById('body');
const dateInput = document.getElementById('send-date');
const timeInput = document.getElementById('send-time');
const folderSelect = document.getElementById('folder-select');
const editorTitle = document.getElementById('editor-title');
const saveBtn = document.getElementById('save-btn');
const cancelBtn = document.getElementById('cancel-btn');
const contactDatalist = document.getElementById('contact-list');
const recentBox = document.getElementById('recent-contacts');
const charCount = document.getElementById('char-count');

// ---------- persistence ----------
function loadMessages() {
    const v2 = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
    if (Array.isArray(v2)) return v2;

    // migrate from the old version that had a subject field
    const old = JSON.parse(localStorage.getItem(OLD_STORAGE_KEY) || '[]');
    const migrated = old.map((m) => ({
        id: m.id,
        recipient: m.recipient || '',
        body: m.body || m.subject || '',
        date: m.date,
        time: m.time,
        folder: m.folder || 'draft',
        createdAt: m.createdAt || Date.now(),
    }));
    if (migrated.length) saveMessagesTo(migrated);
    return migrated;
}

function loadContacts() {
    const saved = JSON.parse(localStorage.getItem(CONTACTS_KEY) || 'null');
    return Array.isArray(saved) ? saved : [...SAMPLE_CONTACTS];
}

function saveMessagesTo(list) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
}

function saveMessages() {
    saveMessagesTo(messages);
}

function saveContacts() {
    localStorage.setItem(CONTACTS_KEY, JSON.stringify(contacts));
}

// ---------- helpers ----------
function generateId() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

function getTodayDate() {
    return new Date().toISOString().slice(0, 10);
}

function getCurrentTime() {
    return new Date().toTimeString().slice(0, 5);
}

function formatDateTime(dateStr, timeStr) {
    if (!dateStr || !timeStr) return 'No date';
    const d = new Date(`${dateStr}T${timeStr}`);
    const now = new Date();
    const sameDay = d.toDateString() === now.toDateString();
    if (sameDay) {
        return `Today ${d.toLocaleTimeString(undefined, {
            hour: '2-digit', minute: '2-digit',
        })}`;
    }
    return d.toLocaleString(undefined, {
        month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit',
    });
}

function initials(name) {
    const clean = (name || '?').replace(/[^\p{L}\p{N} ]/gu, ' ').trim();
    if (!clean) return '?';
    const parts = clean.split(/\s+/);
    if (parts.length === 1) return parts[0].slice(0, 2);
    return (parts[0][0] + parts[1][0]);
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text ?? '';
    return div.innerHTML;
}

function allKnownContacts() {
    const set = new Set([...contacts, ...messages.map((m) => m.recipient)]);
    return [...set].filter(Boolean);
}

function recentRecipients() {
    const seen = [];
    const sorted = [...messages].sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
    for (const m of sorted) {
        if (m.recipient && !seen.includes(m.recipient)) seen.push(m.recipient);
        if (seen.length >= 6) break;
    }
    return seen;
}

// ---------- rendering ----------
function renderContacts() {
    const known = allKnownContacts();
    contactDatalist.innerHTML = known.map((c) => `<option value="${escapeHtml(c)}"></option>`).join('');

    const recents = recentRecipients();
    if (recents.length === 0) {
        recentBox.innerHTML = '';
        return;
    }
    recentBox.innerHTML = recents
        .map((c) => `<button type="button" class="chip" data-name="${escapeHtml(c)}">${escapeHtml(c)}</button>`)
        .join('');
}

function renderMessageList() {
    const labels = { inbox: 'Inbox', outbox: 'Sent', draft: 'Drafts' };
    listTitle.textContent = labels[currentFolder] || currentFolder;
    const items = messages
        .filter((m) => m.folder === currentFolder)
        .sort((a, b) => {
            const da = new Date(`${a.date}T${a.time}`);
            const db = new Date(`${b.date}T${b.time}`);
            return db - da;
        });

    if (items.length === 0) {
        messageList.innerHTML = `<p class="empty">No messages in ${labels[currentFolder].toLowerCase()}.</p>`;
        return;
    }

    messageList.innerHTML = items
        .map((m) => `
        <div class="message-card" data-id="${m.id}">
            <div class="avatar">${escapeHtml(initials(m.recipient))}</div>
            <div class="message-main">
                <div class="message-top">
                    <span class="message-recipient">${escapeHtml(m.recipient)}</span>
                    <span class="message-date">${formatDateTime(m.date, m.time)}</span>
                </div>
                <div class="message-body">${escapeHtml(m.body)}</div>
                <div class="message-actions">
                    <button class="edit-btn" data-id="${m.id}">Edit</button>
                    <button class="delete-btn" data-id="${m.id}">Delete</button>
                </div>
            </div>
        </div>
    `).join('');
}

function updateCharCount() {
    charCount.textContent = bodyInput.value.length;
}

// ---------- form ----------
function resetForm() {
    editingId = null;
    editIdInput.value = '';
    form.reset();
    dateInput.value = getTodayDate();
    timeInput.value = getCurrentTime();
    folderSelect.value = 'draft';
    editorTitle.textContent = 'New message';
    saveBtn.textContent = 'Save';
    updateCharCount();
}

function handleFolderSwitch(folder) {
    currentFolder = folder;
    folderBtns.forEach((btn) => {
        btn.classList.toggle('active', btn.dataset.folder === folder);
    });
    resetForm();
    renderMessageList();
}

function handleFormSubmit(e) {
    e.preventDefault();

    const recipient = recipientInput.value.trim();
    const body = bodyInput.value.trim();
    const date = dateInput.value;
    const time = timeInput.value;
    const folder = folderSelect.value;

    if (!recipient || !body || !date || !time) return;

    if (editingId) {
        const msg = messages.find((m) => m.id === editingId);
        if (msg) Object.assign(msg, { recipient, body, date, time, folder });
    } else {
        messages.push({
            id: generateId(),
            recipient, body, date, time, folder,
            createdAt: Date.now(),
        });
    }

    // remember this recipient as a contact
    if (!contacts.includes(recipient)) {
        contacts.push(recipient);
        saveContacts();
    }

    saveMessages();
    resetForm();
    renderContacts();

    if (currentFolder !== folder) {
        handleFolderSwitch(folder);
    } else {
        renderMessageList();
    }
}

function handleListClick(e) {
    const editBtn = e.target.closest('.edit-btn');
    const deleteBtn = e.target.closest('.delete-btn');

    if (editBtn) {
        const id = editBtn.dataset.id;
        const msg = messages.find((m) => m.id === id);
        if (!msg) return;

        editingId = id;
        editIdInput.value = id;
        recipientInput.value = msg.recipient;
        bodyInput.value = msg.body;
        dateInput.value = msg.date;
        timeInput.value = msg.time;
        folderSelect.value = msg.folder;
        editorTitle.textContent = 'Edit message';
        saveBtn.textContent = 'Update';
        updateCharCount();
        bodyInput.focus();
    }

    if (deleteBtn) {
        const id = deleteBtn.dataset.id;
        if (confirm('Delete this message?')) {
            messages = messages.filter((m) => m.id !== id);
            saveMessages();
            renderMessageList();
        }
    }
}

function handleRecentClick(e) {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    recipientInput.value = chip.dataset.name;
}

// ---------- init ----------
function init() {
    dateInput.value = getTodayDate();
    timeInput.value = getCurrentTime();
    folderSelect.value = 'draft';

    folderBtns.forEach((btn) => {
        btn.addEventListener('click', () => handleFolderSwitch(btn.dataset.folder));
    });

    form.addEventListener('submit', handleFormSubmit);
    cancelBtn.addEventListener('click', resetForm);
    bodyInput.addEventListener('input', updateCharCount);
    messageList.addEventListener('click', handleListClick);
    recentBox.addEventListener('click', handleRecentClick);

    updateCharCount();
    renderContacts();
    renderMessageList();
}

init();