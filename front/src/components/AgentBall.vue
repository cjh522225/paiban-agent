<template>
  <div class="agent-ball-root">
    <!-- 悬浮球 -->
    <div
      class="agent-ball"
      :class="{ active: open }"
      :style="{ top: ballTop + 'px' }"
      title="值班助手（点击展开/收起）"
      @pointerdown="onPointerDown"
    >
      <span class="agent-ball-icon">AI</span>
      <span v-if="loading" class="agent-ball-loading"></span>
    </div>

    <!-- 聊天面板 -->
    <transition name="agent-panel">
      <section v-if="open" class="agent-panel" :style="{ top: panelTop + 'px' }">
        <header class="agent-header">
          <div class="agent-title">
            <span class="agent-status" :class="statusClass" :title="statusTip" @click="checkStatus">
              <span class="agent-dot"></span>{{ statusText }}
            </span>
            <span>{{ title }}</span>
          </div>
          <div class="agent-header-actions">
            <button class="agent-icon-btn" title="新会话（保留历史）" @click="newConversation">⟳</button>
            <button class="agent-icon-btn" title="历史会话" @click="toggleHistory">🕘</button>
            <button class="agent-icon-btn" title="设置" @click="showSettings = !showSettings">⚙</button>
            <button class="agent-icon-btn" title="收起" @click="open = false">—</button>
          </div>
        </header>

        <div v-if="showSettings" class="agent-settings">
          <label>Agent 服务地址</label>
          <input v-model="baseUrl" type="text" placeholder="http://localhost:8090" />
          <label class="agent-checkbox">
            <input v-model="allowWrites" type="checkbox" />
            允许写操作（需管理员，落库前会二次确认）
          </label>
          <p class="agent-tip">身份自动使用当前登录态（JWT），未登录时由服务端兜底。</p>
        </div>

        <div v-if="showHistory" class="agent-history">
          <div class="agent-history-head">
            <span>历史会话（{{ conversations.length }}）</span>
            <button class="agent-icon-btn" title="刷新" @click="loadConversations">↻</button>
          </div>
          <div class="agent-history-list">
            <div v-if="!conversations.length" class="agent-history-empty">
              {{ historyLoading ? '加载中…' : '暂无历史会话' }}
            </div>
            <div
              v-for="item in conversations"
              :key="item.id"
              class="agent-history-item"
              :class="{ active: item.id === conversationId }"
              @click="openConversation(item.id)"
            >
              <div class="agent-history-main">
                <div class="agent-history-title">{{ item.title || '未命名会话' }}</div>
                <div class="agent-history-meta">{{ formatTime(item.updatedAt) }} · {{ item.messageCount }} 条消息</div>
              </div>
              <button class="agent-icon-btn agent-history-delete" title="删除会话" @click.stop="deleteConversation(item.id)">✕</button>
            </div>
          </div>
        </div>

        <div ref="listRef" class="agent-list" v-show="!showHistory">
          <div v-if="messages.length === 0" class="agent-empty">
            <p class="agent-empty-title">你好，我是值班助手</p>
            <p class="agent-empty-desc">可以问我排班、统计、请假规则、换班多排、纪律处理等问题。</p>
            <div class="agent-quick">
              <button v-for="q in quickPrompts" :key="q" @click="send(q)">{{ q }}</button>
            </div>
          </div>
          <div v-for="(m, i) in messages" :key="i" class="agent-msg" :class="m.role">
            <div v-if="m.role === 'assistant'" class="agent-bubble md" v-html="renderMarkdown(m.content)"></div>
            <div v-else class="agent-bubble">{{ m.content }}</div>
          </div>
          <div v-if="loading && waitingFirstChunk" class="agent-msg assistant">
            <div class="agent-bubble agent-typing"><span></span><span></span><span></span><em>正在查询…</em></div>
          </div>
        </div>

        <div v-if="recentTools.length && !showHistory" class="agent-tools">
          <div class="agent-tools-head" @click="showTools = !showTools">
            最近工具调用（{{ recentTools.length }}）{{ showTools ? '▲' : '▼' }}
          </div>
          <ul v-show="showTools">
            <li v-for="(t, i) in recentTools" :key="i">
              <span :class="t.ok ? 'ok' : 'err'">{{ t.ok ? '✓' : '✗' }}</span>
              {{ t.tool }}
              <em>{{ t.durationMs }}ms</em>
            </li>
          </ul>
        </div>

        <footer class="agent-composer" v-show="!showHistory">
          <textarea
            ref="inputRef"
            v-model="draft"
            rows="1"
            placeholder="输入问题，Enter 发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="send()"
          ></textarea>
          <button class="agent-send" :disabled="loading || !draft.trim()" @click="send()">发送</button>
        </footer>
      </section>
    </transition>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps({
  baseUrl: { type: String, default: 'http://localhost:8090' },
  title: { type: String, default: '值班助手' }
})

const open = ref(false)
const loading = ref(false)
const waitingFirstChunk = ref(false)
const draft = ref('')
const messages = ref([])
const listRef = ref(null)
const inputRef = ref(null)
const showSettings = ref(false)
const showTools = ref(false)
const showHistory = ref(false)
const conversations = ref([])
const historyLoading = ref(false)
const recentTools = ref([])
const allowWrites = ref(localStorage.getItem('agentBall.allowWrites') === '1')
const baseUrl = ref(localStorage.getItem('agentBall.baseUrl') || props.baseUrl)
const ballTop = ref(Number(localStorage.getItem('agentBall.top') || 0) || Math.round(window.innerHeight * 0.55))
const status = ref('unknown')
const conversationId = ref(getOrCreateConversationId())

const WRITE_TOOLS = new Set([
  'approveLeave', 'submitLeave', 'cancelLeave',
  'approveSwapRequest', 'completeSwapRequest', 'cancelSwapRequest', 'submitSwapRequest',
  'approveMultiDuty', 'cancelMultiDuty', 'submitMultiDuty',
  'generateDormitorySchedule', 'generateOfficeSchedule', 'clearSchedules', 'swapSchedules', 'deleteSchedule',
  'handleDisciplineAction',
  'sendMessage', 'updateMessage', 'deleteMessage', 'markAllMessagesRead',
  'deleteHoliday', 'saveMyAvailability', 'deleteAvailability',
  'createUser', 'updateUser', 'deleteUser', 'batchDeleteUsers', 'applyHolidayConfig'
])

async function notifyDataChanged(base, since) {
  try {
    const res = await fetch(base + '/api/audit/recent?limit=20')
    const entries = await res.json()
    const wrote = Array.isArray(entries) && entries.some(
      (entry) => entry && entry.ok && WRITE_TOOLS.has(entry.tool) && Date.parse(entry.at) >= since - 2000
    )
    if (wrote) {
      window.dispatchEvent(new CustomEvent('agent:data-changed'))
    }
  } catch {}
}

function getOrCreateConversationId(forceNew = false) {
  if (!forceNew) {
    const existing = localStorage.getItem('agentBall.conversationId')
    if (existing) return existing
  }
  const id = 'ball-' + (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2))
  localStorage.setItem('agentBall.conversationId', id)
  return id
}

const quickPrompts = [
  '现在第几周？我本周有排班吗？',
  '统计一下每个人这学期值班次数前 5 名',
  '请假需要提前几天申请？',
  '为什么 223 号用户在第 5 教学周没有排班？'
]

const statusClass = computed(() => ({ ok: status.value === 'up', err: status.value === 'down' }))
const statusText = computed(() =>
  status.value === 'up' ? '在线' : status.value === 'down' ? '离线' : '检测中'
)
const statusTip = computed(() =>
  status.value === 'up'
    ? `Agent 服务已连接（${baseUrl.value}），点击重新检测`
    : `无法连接 Agent 服务（${baseUrl.value}）：请确认服务已启动、地址正确；点击重新检测`
)
const panelTop = computed(() => {
  const max = window.innerHeight - 600
  return Math.max(12, Math.min(ballTop.value - 240, max))
})

watch(baseUrl, (v) => {
  localStorage.setItem('agentBall.baseUrl', v)
  checkStatus()
})
watch(allowWrites, (v) => localStorage.setItem('agentBall.allowWrites', v ? '1' : '0'))

function authToken() {
  const local = localStorage.getItem('token') || localStorage.getItem('Admin-Token')
  if (local) return local
  const match = document.cookie.match(/(?:^|;\s*)Admin-Token=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}

function authHeaders() {
  const headers = { 'Content-Type': 'application/json; charset=utf-8' }
  const token = authToken()
  if (token) headers.Authorization = 'Bearer ' + token
  return headers
}

function formatTime(iso) {
  if (!iso) return ''
  try {
    const date = new Date(iso)
    const pad = (n) => String(n).padStart(2, '0')
    return `${date.getMonth() + 1}/${date.getDate()} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  } catch {
    return ''
  }
}

async function loadConversations() {
  historyLoading.value = true
  try {
    const res = await fetch(baseUrl.value.replace(/\/$/, '') + '/api/conversations', { headers: authHeaders() })
    conversations.value = res.ok ? await res.json() : []
  } catch {
    conversations.value = []
  } finally {
    historyLoading.value = false
  }
}

function toggleHistory() {
  showHistory.value = !showHistory.value
  if (showHistory.value) {
    showSettings.value = false
    loadConversations()
  }
}

async function openConversation(id) {
  try {
    const res = await fetch(baseUrl.value.replace(/\/$/, '') + '/api/conversations/' + id, { headers: authHeaders() })
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const data = await res.json()
    conversationId.value = id
    localStorage.setItem('agentBall.conversationId', id)
    messages.value = (data.messages || []).map((m) => ({
      role: String(m.role || '').toLowerCase() === 'user' ? 'user' : 'assistant',
      content: m.content || ''
    }))
    showHistory.value = false
    scrollToBottom()
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '无法加载该会话：' + e.message })
  }
}

async function deleteConversation(id) {
  if (!window.confirm('删除该会话？删除后无法恢复。')) return
  try {
    await fetch(baseUrl.value.replace(/\/$/, '') + '/api/conversations/' + id, {
      method: 'DELETE',
      headers: authHeaders()
    })
  } catch {}
  conversations.value = conversations.value.filter((item) => item.id !== id)
  if (id === conversationId.value) {
    newConversation()
  }
  if (showHistory.value) loadConversations()
}

async function restoreCurrentConversation() {
  const id = localStorage.getItem('agentBall.conversationId')
  if (!id) return
  try {
    const res = await fetch(baseUrl.value.replace(/\/$/, '') + '/api/conversations/' + id, { headers: authHeaders() })
    if (!res.ok) {
      localStorage.removeItem('agentBall.conversationId')
      return
    }
    const data = await res.json()
    conversationId.value = id
    messages.value = (data.messages || []).map((m) => ({
      role: String(m.role || '').toLowerCase() === 'user' ? 'user' : 'assistant',
      content: m.content || ''
    }))
    scrollToBottom()
  } catch {}
}

async function checkStatus() {
  const base = baseUrl.value.replace(/\/$/, '')
  try {
    let res = await fetch(base + '/api/health')
    if (!res.ok) {
      res = await fetch(base + '/actuator/health')
    }
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const json = await res.json()
    status.value = String(json.status || '').toUpperCase() === 'UP' ? 'up' : 'down'
  } catch {
    status.value = 'down'
  }
}

async function newConversation() {
  messages.value = []
  conversationId.value = getOrCreateConversationId(true)
  showHistory.value = false
  recentTools.value = []
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

async function refreshTools() {
  try {
    const res = await fetch(baseUrl.value.replace(/\/$/, '') + '/api/audit/recent?limit=5')
    recentTools.value = await res.json()
  } catch {
    recentTools.value = []
  }
}

async function send(text) {
  const question = (text ?? draft.value).trim()
  if (!question || loading.value) return
  draft.value = ''
  messages.value.push({ role: 'user', content: question })
  messages.value.push({ role: 'assistant', content: '' })
  const reply = messages.value[messages.value.length - 1]
  waitingFirstChunk.value = true
  loading.value = true
  scrollToBottom()

  const url = baseUrl.value.replace(/\/$/, '')
  const headers = { 'Content-Type': 'application/json; charset=utf-8' }
  const token = authToken()
  if (token) headers.Authorization = 'Bearer ' + token
  const body = JSON.stringify({
    message: question,
    allowWrites: allowWrites.value,
    conversationId: conversationId.value
  })
  const startedAt = Date.now()

  try {
    const res = await fetch(url + '/api/chat/stream', { method: 'POST', headers, body })
    if (!res.ok || !res.body) throw new Error('HTTP ' + res.status)
    status.value = 'up'
    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() ?? ''
      for (const part of parts) {
        const dataLines = part.split('\n').filter((l) => l.startsWith('data:'))
        if (dataLines.length === 0) continue
        const raw = dataLines.map((l) => l.slice(5).replace(/^ /, '')).join('\n')
        let chunk = raw
        try {
          chunk = JSON.parse(raw)
        } catch {
          // 兼容非 JSON 片段
        }
        if (typeof chunk === 'string' && chunk.length > 0) {
          waitingFirstChunk.value = false
          reply.content += chunk
          scrollToBottom()
        }
      }
    }
  } catch (e) {
    console.warn('[agent] stream fallback:', e && e.message)
    try {
      const res = await fetch(url + '/api/chat', { method: 'POST', headers, body })
      const json = await res.json()
      status.value = 'up'
      waitingFirstChunk.value = false
      reply.content = json.content || '（空回答）'
    } catch (err) {
      waitingFirstChunk.value = false
      reply.content = '请求失败：' + (e.message || err.message) + '（请检查 Agent 服务是否启动）'
      status.value = 'down'
    }
  } finally {
    waitingFirstChunk.value = false
    loading.value = false
    scrollToBottom()
    refreshTools()
    notifyDataChanged(url, startedAt)
  }
}

/* ---------- 悬浮球拖动 ---------- */
let dragging = false
let startY = 0
let startTop = 0
let moved = false

function onPointerDown(event) {
  dragging = true
  moved = false
  startY = event.clientY
  startTop = ballTop.value
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(event) {
  if (!dragging) return
  const delta = event.clientY - startY
  if (Math.abs(delta) > 4) moved = true
  ballTop.value = Math.max(8, Math.min(window.innerHeight - 60, startTop + delta))
}

function onPointerUp() {
  dragging = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  if (moved) {
    localStorage.setItem('agentBall.top', String(ballTop.value))
  } else {
    open.value = !open.value
    if (open.value) {
      checkStatus()
      refreshTools()
      nextTick(() => inputRef.value && inputRef.value.focus())
    }
  }
}

/* ---------- 轻量 Markdown 渲染 ---------- */
function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function renderInline(text) {
  return text
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\((https?:[^)]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>')
}

function renderMarkdown(source) {
  const normalized = String(source || '').replace(/｜/g, '|')
  const lines = escapeHtml(normalized).split('\n')
  const html = []
  let inCode = false
  let tableBuffer = []
  let tabBuffer = []

  const flushTable = () => {
    if (!tableBuffer.length) return
    const rows = tableBuffer
      .filter((r) => !/^\s*\|?[\s:|-]+\|?\s*$/.test(r))
      .map((r) => r.replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map((c) => c.trim()))
    if (rows.length) {
      html.push('<table><thead><tr>' + rows[0].map((c) => `<th>${renderInline(c)}</th>`).join('') + '</tr></thead><tbody>')
      for (const row of rows.slice(1)) {
        html.push('<tr>' + row.map((c) => `<td>${renderInline(c)}</td>`).join('') + '</tr>')
      }
      html.push('</tbody></table>')
    }
    tableBuffer = []
  }

  // 兼容模型用制表符（Tab）对齐的“伪表格”
  const flushTabTable = () => {
    if (!tabBuffer.length) return
    const rows = tabBuffer.map((r) => r.split('\t').map((c) => c.trim()))
    const width = Math.max(...rows.map((r) => r.length))
    if (width >= 2) {
      html.push('<table><thead><tr>' + rows[0].map((c) => `<th>${renderInline(c)}</th>`).join('') + '</tr></thead><tbody>')
      for (const row of rows.slice(1)) {
        html.push('<tr>' + row.map((c) => `<td>${renderInline(c)}</td>`).join('') + '</tr>')
      }
      html.push('</tbody></table>')
    } else {
      for (const row of rows) {
        html.push(`<div>${renderInline(row.join(' '))}</div>`)
      }
    }
    tabBuffer = []
  }

  for (const raw of lines) {
    const line = raw.replace(/\r$/, '')
    if (line.trim().startsWith('```')) {
      flushTable()
      flushTabTable()
      html.push(inCode ? '</code></pre>' : '<pre><code>')
      inCode = !inCode
      continue
    }
    if (inCode) {
      html.push(line)
      continue
    }
    if (/^\s*\|.*\|\s*$/.test(line)) {
      flushTabTable()
      tableBuffer.push(line)
      continue
    }
    if (line.includes('\t') && line.trim() !== '' && tableBuffer.length === 0) {
      tabBuffer.push(line)
      continue
    }
    flushTable()
    flushTabTable()
    if (/^#{1,4}\s/.test(line)) {
      const level = line.match(/^#+/)[0].length
      html.push(`<h${level + 2}>${renderInline(line.replace(/^#+\s*/, ''))}</h${level + 2}>`)
    } else if (/^\s*[-*]\s+/.test(line)) {
      html.push(`<div class="md-li">• ${renderInline(line.replace(/^\s*[-*]\s+/, ''))}</div>`)
    } else if (/^\s*\d+\.\s+/.test(line)) {
      html.push(`<div class="md-li">${renderInline(line.trim())}</div>`)
    } else if (line.trim() === '') {
      html.push('<div class="md-gap"></div>')
    } else {
      html.push(`<div>${renderInline(line)}</div>`)
    }
  }
  flushTable()
  flushTabTable()
  if (inCode) html.push('</code></pre>')
  return html.join('\n')
}

let statusTimer = null

onMounted(() => {
  checkStatus()
  restoreCurrentConversation()
  statusTimer = setInterval(checkStatus, 30000)
  window.addEventListener('focus', checkStatus)
  window.addEventListener('resize', () => {
    ballTop.value = Math.max(8, Math.min(window.innerHeight - 60, ballTop.value))
  })
})

onUnmounted(() => {
  if (statusTimer) clearInterval(statusTimer)
  window.removeEventListener('focus', checkStatus)
})
</script>

<style scoped>
.agent-ball-root { position: fixed; right: 0; top: 0; z-index: 3000; }

.agent-ball {
  position: fixed;
  right: 14px;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
  box-shadow: 0 6px 20px rgba(37, 99, 235, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  user-select: none;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.agent-ball:hover { transform: scale(1.06); box-shadow: 0 8px 24px rgba(37, 99, 235, 0.55); }
.agent-ball.active { background: linear-gradient(135deg, #1d4ed8 0%, #6d28d9 100%); }
.agent-ball-icon { color: #fff; font-weight: 700; font-size: 15px; letter-spacing: 0.5px; }
.agent-ball-loading {
  position: absolute; inset: -3px; border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.35); border-top-color: #fff;
  animation: agent-spin 0.9s linear infinite;
}
@keyframes agent-spin { to { transform: rotate(360deg); } }

.agent-panel {
  position: fixed;
  right: 14px;
  width: 380px;
  height: 560px;
  max-height: calc(100vh - 24px);
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.28);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.agent-panel-enter-active, .agent-panel-leave-active { transition: opacity 0.18s ease, transform 0.18s ease; }
.agent-panel-enter-from, .agent-panel-leave-to { opacity: 0; transform: translateY(12px) scale(0.98); }

.agent-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px; border-bottom: 1px solid #eef1f6;
  background: linear-gradient(135deg, #f8fafc, #eef2ff);
}
.agent-title { display: flex; align-items: center; gap: 8px; font-weight: 600; color: #1e293b; font-size: 14px; }
.agent-sub { font-size: 12px; color: #94a3b8; font-weight: 400; }
.agent-status {
  display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 400;
  color: #94a3b8; cursor: pointer; padding: 2px 6px; border-radius: 6px;
}
.agent-status:hover { background: #e2e8f0; color: #2563eb; }
.agent-status .agent-dot { width: 8px; height: 8px; border-radius: 50%; background: #cbd5e1; }
.agent-status.ok .agent-dot { background: #22c55e; }
.agent-status.err .agent-dot { background: #ef4444; }
.agent-dot { width: 8px; height: 8px; border-radius: 50%; background: #cbd5e1; }
.agent-dot.ok { background: #22c55e; }
.agent-dot.err { background: #ef4444; }
.agent-header-actions { display: flex; gap: 4px; }
.agent-icon-btn {
  border: none; background: transparent; cursor: pointer; font-size: 14px;
  color: #64748b; width: 26px; height: 26px; border-radius: 6px;
}
.agent-icon-btn:hover { background: #e2e8f0; color: #1e293b; }

.agent-settings { padding: 10px 14px; border-bottom: 1px solid #eef1f6; background: #fbfdff; }
.agent-settings label { display: block; font-size: 12px; color: #64748b; margin-bottom: 4px; }
.agent-settings input[type='text'] {
  width: 100%; padding: 7px 9px; border: 1px solid #dbe3ee; border-radius: 8px;
  font-size: 12px; margin-bottom: 8px; outline: none;
}
.agent-checkbox { display: flex !important; align-items: center; gap: 6px; margin-bottom: 6px; }
.agent-tip { font-size: 11px; color: #94a3b8; margin: 0; }

.agent-history { flex: 1; display: flex; flex-direction: column; background: #f8fafc; }
.agent-history-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px; border-bottom: 1px solid #eef1f6; font-size: 13px;
  color: #334155; font-weight: 600; background: #fff;
}
.agent-history-list { flex: 1; overflow-y: auto; padding: 8px; }
.agent-history-empty { text-align: center; color: #94a3b8; font-size: 12px; padding: 24px 0; }
.agent-history-item {
  display: flex; align-items: center; gap: 8px; padding: 9px 10px; border-radius: 10px;
  background: #fff; border: 1px solid #e8edf5; margin-bottom: 6px; cursor: pointer;
}
.agent-history-item:hover { border-color: #2563eb; }
.agent-history-item.active { border-color: #2563eb; background: #eff6ff; }
.agent-history-main { flex: 1; min-width: 0; }
.agent-history-title { font-size: 13px; color: #0f172a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.agent-history-meta { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.agent-history-delete { opacity: 0; transition: opacity 0.15s; }
.agent-history-item:hover .agent-history-delete { opacity: 1; }

.agent-list { flex: 1; overflow-y: auto; padding: 12px; background: #f8fafc; }
.agent-empty { text-align: center; padding: 18px 8px; color: #64748b; }
.agent-empty-title { font-weight: 600; color: #1e293b; margin: 0 0 6px; }
.agent-empty-desc { font-size: 12px; margin: 0 0 12px; }
.agent-quick { display: flex; flex-direction: column; gap: 6px; }
.agent-quick button {
  border: 1px solid #dbe3ee; background: #fff; color: #334155; border-radius: 8px;
  padding: 7px 10px; font-size: 12px; cursor: pointer; text-align: left;
}
.agent-quick button:hover { border-color: #2563eb; color: #2563eb; }

.agent-msg { display: flex; margin-bottom: 10px; }
.agent-msg.user { justify-content: flex-end; }
.agent-bubble {
  max-width: 86%; padding: 9px 12px; border-radius: 12px; font-size: 13px; line-height: 1.65;
  white-space: pre-wrap; word-break: break-word; color: #0f172a; background: #fff;
  border: 1px solid #e8edf5; box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.agent-msg.user .agent-bubble { background: #2563eb; color: #fff; border-color: #2563eb; white-space: pre-wrap; }
.agent-bubble.md { white-space: normal; }
.agent-bubble.md table { border-collapse: collapse; margin: 6px 0; font-size: 12px; width: 100%; }
.agent-bubble.md th, .agent-bubble.md td { border: 1px solid #e2e8f0; padding: 4px 6px; text-align: left; }
.agent-bubble.md th { background: #f1f5f9; }
.agent-bubble.md pre { background: #0f172a; color: #e2e8f0; padding: 8px 10px; border-radius: 8px; overflow-x: auto; margin: 6px 0; }
.agent-bubble.md code { background: #eef2f7; padding: 1px 4px; border-radius: 4px; font-size: 12px; }
.agent-bubble.md pre code { background: transparent; padding: 0; }
.agent-bubble.md h3, .agent-bubble.md h4, .agent-bubble.md h5 { margin: 6px 0 4px; font-size: 13px; }
.agent-bubble.md .md-li { margin: 2px 0; }
.agent-bubble.md .md-gap { height: 6px; }
.agent-bubble.md a { color: #2563eb; }

.agent-typing { display: inline-flex; gap: 4px; align-items: center; }
.agent-typing span {
  width: 6px; height: 6px; border-radius: 50%; background: #94a3b8;
  animation: agent-blink 1.2s infinite ease-in-out;
}
.agent-typing span:nth-child(2) { animation-delay: 0.15s; }
.agent-typing span:nth-child(3) { animation-delay: 0.3s; }
.agent-typing em { font-style: normal; font-size: 12px; color: #94a3b8; margin-left: 6px; }
@keyframes agent-blink { 0%, 80%, 100% { opacity: 0.3; } 40% { opacity: 1; } }

.agent-tools { border-top: 1px solid #eef1f6; background: #fff; font-size: 12px; color: #64748b; }
.agent-tools-head { padding: 6px 12px; cursor: pointer; user-select: none; }
.agent-tools-head:hover { color: #2563eb; }
.agent-tools ul { margin: 0; padding: 0 12px 8px; list-style: none; max-height: 96px; overflow-y: auto; }
.agent-tools li { display: flex; gap: 6px; align-items: center; padding: 2px 0; }
.agent-tools .ok { color: #22c55e; }
.agent-tools .err { color: #ef4444; }
.agent-tools em { margin-left: auto; font-style: normal; color: #94a3b8; }

.agent-composer { display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid #eef1f6; background: #fff; }
.agent-composer textarea {
  flex: 1; resize: none; max-height: 88px; padding: 8px 10px; border: 1px solid #dbe3ee;
  border-radius: 10px; font-size: 13px; outline: none; font-family: inherit; line-height: 1.5;
}
.agent-composer textarea:focus { border-color: #2563eb; }
.agent-send {
  align-self: flex-end; border: none; background: #2563eb; color: #fff; border-radius: 10px;
  padding: 8px 14px; font-size: 13px; cursor: pointer;
}
.agent-send:disabled { background: #cbd5e1; cursor: not-allowed; }
</style>
