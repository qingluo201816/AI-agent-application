<template>
  <div class="workbench-page">
    <div class="ambient ambient-left"></div>
    <div class="ambient ambient-right"></div>

    <header class="page-header">
      <button class="back-button" @click="goBack">返回首页</button>

      <div class="header-copy">
        <div class="header-label">NOVEL WORKBENCH</div>
        <h1 class="header-title">AI 小说写作工作台</h1>
        <p class="header-subtitle">
          左侧负责模式与会话选择，右侧专注聊天内容本身。
        </p>
      </div>
    </header>

    <main class="page-content">
      <NovelSessionSidebar
        :view-mode="sidebarViewMode"
        :abilities="abilities"
        :active-mode-key="activeModeKey"
        :sessions="currentSessions"
        :selected-session-id="currentSessionId"
        :loading="currentSessionListLoading"
        @select-ability="handleSelectAbility"
        @back-to-modes="handleBackToModes"
        @create-session="handleCreateSession"
        @select-session="handleSelectSession"
        @rename-session="handleRenameSession"
        @delete-session="handleDeleteSession"
      />

      <section class="chat-panel">
        <div class="chat-mini-header">
          <div class="chat-mini-copy">
            <div class="chat-mini-label">{{ chatHeaderLabel }}</div>
            <div class="chat-mini-title">{{ chatHeaderTitle }}</div>
            <div class="chat-mini-meta">{{ chatHeaderMeta }}</div>
          </div>
        </div>

        <ChatRoom
          :messages="currentMessages"
          :connection-status="currentConnectionStatus"
          :placeholder="chatPlaceholder"
          :empty-title="chatEmptyTitle"
          :empty-description="chatEmptyDescription"
          :input-disabled="!currentAbility"
          ai-type="writing"
          @send-message="sendMessage"
        />
      </section>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useHead } from '@vueuse/head'
import { useRouter } from 'vue-router'
import AppFooter from '../components/AppFooter.vue'
import ChatRoom from '../components/ChatRoom.vue'
import NovelSessionSidebar from '../components/NovelSessionSidebar.vue'
import {
  createNovelSession,
  deleteNovelSession,
  getNovelSessionDetail,
  listNovelSessions,
  renameNovelSession
} from '../api'
import { novelAbilities, novelAbilityMap } from '../constants/novelAbilities'

useHead({
  title: 'AI 小说写作工作台 - 晴落 AI 写作平台',
  meta: [
    {
      name: 'description',
      content: '小说写作工作台提供状态回溯、灵感辅助、关键词续写三个入口，并支持按入口持久化历史会话、自动命名和继续对话。'
    },
    {
      name: 'keywords',
      content: '小说写作工作台,状态回溯,灵感辅助,关键词续写,历史会话,AI写作助手'
    }
  ]
})

const router = useRouter()
const abilities = novelAbilities
const abilityKeys = abilities.map((ability) => ability.key)

const sidebarViewMode = ref('mode-picker')
const activeModeKey = ref('')

const sessionLists = reactive(Object.fromEntries(abilityKeys.map((key) => [key, []])))
const selectedSessionIdByAbility = reactive(Object.fromEntries(abilityKeys.map((key) => [key, ''])))
const sessionListLoading = reactive(Object.fromEntries(abilityKeys.map((key) => [key, false])))
const sessionListLoaded = reactive(Object.fromEntries(abilityKeys.map((key) => [key, false])))
const messagesBySessionId = reactive({})
const connectionStatusBySessionId = reactive({})

let activeConnection = null
let activeSessionId = ''

const currentAbility = computed(() => {
  return activeModeKey.value ? (novelAbilityMap[activeModeKey.value] ?? null) : null
})

const currentSessions = computed(() => {
  if (!currentAbility.value) {
    return []
  }
  return sessionLists[activeModeKey.value] ?? []
})

const currentSessionId = computed(() => {
  if (!currentAbility.value) {
    return ''
  }
  return selectedSessionIdByAbility[activeModeKey.value] ?? ''
})

const currentSessionSummary = computed(() => {
  if (!currentSessionId.value) {
    return null
  }
  return currentSessions.value.find((session) => session.chatId === currentSessionId.value) ?? null
})

const currentMessages = computed(() => {
  if (!currentSessionId.value) {
    return []
  }
  return messagesBySessionId[currentSessionId.value] ?? []
})

const currentConnectionStatus = computed(() => {
  if (!currentSessionId.value) {
    return 'disconnected'
  }
  return connectionStatusBySessionId[currentSessionId.value] ?? 'disconnected'
})

const currentSessionListLoading = computed(() => {
  if (!currentAbility.value) {
    return false
  }
  return sessionListLoading[activeModeKey.value]
})

const chatHeaderLabel = computed(() => {
  if (currentSessionSummary.value) {
    return currentAbility.value?.subtitle ?? 'Conversation'
  }
  if (currentAbility.value) {
    return currentAbility.value.subtitle
  }
  return 'Mode Selection'
})

const chatHeaderTitle = computed(() => {
  if (currentSessionSummary.value) {
    return currentSessionSummary.value.title
  }
  if (currentAbility.value) {
    return `${currentAbility.value.title}模式`
  }
  return '选择左侧模式开始创作'
})

const chatHeaderMeta = computed(() => {
  if (currentSessionSummary.value) {
    const metaParts = [
      currentAbility.value?.title,
      currentSessionSummary.value.chatId
    ]

    if (currentSessionSummary.value.updatedAt) {
      metaParts.push(`更新于 ${formatDateTime(currentSessionSummary.value.updatedAt)}`)
    }

    return metaParts.filter(Boolean).join(' · ')
  }

  if (currentAbility.value) {
    return '左侧已经切换到该模式专属历史会话页，你可以选择历史会话、左侧新建会话，或直接开始输入。'
  }

  return '点击任意模式卡片后，左侧会切换成该模式专属的历史会话列表。'
})

const chatPlaceholder = computed(() => {
  return currentAbility.value?.placeholder ?? '请先从左侧选择一种创作模式...'
})

const chatEmptyTitle = computed(() => {
  if (currentSessionSummary.value) {
    return currentAbility.value?.emptyTitle ?? '开始新的会话'
  }
  if (currentAbility.value) {
    return `进入${currentAbility.value.title}后开始新的会话`
  }
  return '左侧选择模式后开始聊天'
})

const chatEmptyDescription = computed(() => {
  if (currentSessionSummary.value) {
    return currentAbility.value?.emptyDescription ?? '会话历史会按入口维度持久化，切换后可以继续原来的 chatId。'
  }
  if (currentAbility.value) {
    return '左侧现在只展示该模式下的历史会话。你可以选择已有会话、点击“新建会话”，或者直接发送消息开始。'
  }
  return '当前左侧是模式选择页。点击一种模式后，会进入该模式专属的历史会话页。'
})

const sortSessions = (sessions) => {
  return [...sessions].sort((left, right) => {
    const leftTime = new Date(left.updatedAt || left.createdAt || 0).getTime()
    const rightTime = new Date(right.updatedAt || right.createdAt || 0).getTime()
    return rightTime - leftTime
  })
}

const previewContent = (content) => {
  const normalized = String(content || '').replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return ''
  }
  return normalized.length > 42 ? `${normalized.slice(0, 42)}...` : normalized
}

const formatDateTime = (value) => {
  if (!value) {
    return '刚刚'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '刚刚'
  }

  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const mapHistoryMessages = (messages = []) => {
  const baseTime = Date.now()
  return messages.map((message, index) => ({
    id: message.id ?? `${message.role}-${index}`,
    content: message.content ?? '',
    isUser: message.role === 'user',
    type: message.role === 'user' ? 'user-question' : 'ai-answer',
    time: message.timestamp ?? baseTime + index * 1000
  }))
}

const createLocalMessage = (role, content = '') => ({
  id: `${role}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
  content,
  isUser: role === 'user',
  type: role === 'user' ? 'user-question' : 'ai-answer',
  time: Date.now()
})

const replaceSessionList = (mode, sessions) => {
  sessionLists[mode] = sortSessions(sessions.map((session) => ({ ...session })))

  if (
    selectedSessionIdByAbility[mode] &&
    !sessionLists[mode].some((session) => session.chatId === selectedSessionIdByAbility[mode])
  ) {
    selectedSessionIdByAbility[mode] = sessionLists[mode][0]?.chatId ?? ''
  }
}

const upsertSessionSummary = (session) => {
  if (!session?.mode) {
    return
  }

  const mode = session.mode
  const nextList = [...(sessionLists[mode] ?? [])]
  const index = nextList.findIndex((item) => item.chatId === session.chatId)

  if (index >= 0) {
    nextList[index] = { ...nextList[index], ...session }
  } else {
    nextList.unshift({ ...session })
  }

  sessionLists[mode] = sortSessions(nextList)
}

const removeSessionSummary = (mode, chatId) => {
  sessionLists[mode] = (sessionLists[mode] ?? []).filter((session) => session.chatId !== chatId)
}

const closeActiveConnection = () => {
  if (!activeConnection) {
    return
  }

  activeConnection.close()

  if (activeSessionId) {
    connectionStatusBySessionId[activeSessionId] = 'disconnected'
  }

  activeConnection = null
  activeSessionId = ''
}

const loadSessions = async (mode, { force = false } = {}) => {
  if (!mode) {
    return
  }

  if (sessionListLoading[mode]) {
    return
  }

  if (!force && sessionListLoaded[mode]) {
    return
  }

  sessionListLoading[mode] = true

  try {
    const sessions = await listNovelSessions(mode)
    replaceSessionList(mode, sessions)
    sessionListLoaded[mode] = true
  } catch (error) {
    console.error(`Failed to load sessions for ${mode}:`, error)
    if (!sessionListLoaded[mode]) {
      replaceSessionList(mode, [])
    }
  } finally {
    sessionListLoading[mode] = false
  }
}

const loadSessionDetail = async (chatId, { replaceMessages = true } = {}) => {
  const detail = await getNovelSessionDetail(chatId)
  upsertSessionSummary(detail.session)

  if (replaceMessages || !messagesBySessionId[chatId]) {
    messagesBySessionId[chatId] = mapHistoryMessages(detail.messages)
  }

  return detail
}

const ensureSessionSelection = async (mode) => {
  const list = sessionLists[mode] ?? []
  let selectedChatId = selectedSessionIdByAbility[mode]

  if (!selectedChatId && list.length) {
    selectedChatId = list[0].chatId
    selectedSessionIdByAbility[mode] = selectedChatId
  }

  if (selectedChatId && !messagesBySessionId[selectedChatId]) {
    await loadSessionDetail(selectedChatId)
  }
}

const handleSelectAbility = async (abilityKey) => {
  closeActiveConnection()
  activeModeKey.value = abilityKey
  sidebarViewMode.value = 'session-list'

  await loadSessions(abilityKey)
  await ensureSessionSelection(abilityKey)
}

const handleBackToModes = () => {
  sidebarViewMode.value = 'mode-picker'
}

const handleSelectSession = async ({ mode, chatId }) => {
  closeActiveConnection()
  activeModeKey.value = mode
  sidebarViewMode.value = 'session-list'
  selectedSessionIdByAbility[mode] = chatId

  try {
    await loadSessionDetail(chatId)
  } catch (error) {
    console.error(`Failed to load session detail for ${chatId}:`, error)
  }
}

const handleCreateSession = async (mode = activeModeKey.value) => {
  if (!mode) {
    return null
  }

  closeActiveConnection()

  try {
    const detail = await createNovelSession(mode)
    activeModeKey.value = mode
    sidebarViewMode.value = 'session-list'
    sessionListLoaded[mode] = true
    upsertSessionSummary(detail.session)
    messagesBySessionId[detail.session.chatId] = mapHistoryMessages(detail.messages)
    connectionStatusBySessionId[detail.session.chatId] = 'disconnected'
    selectedSessionIdByAbility[mode] = detail.session.chatId
    return detail
  } catch (error) {
    console.error(`Failed to create session for ${mode}:`, error)
    return null
  }
}

const handleRenameSession = async ({ chatId, title }) => {
  const existingSession = Object.values(sessionLists)
    .flat()
    .find((session) => session.chatId === chatId)

  if (existingSession) {
    upsertSessionSummary({
      ...existingSession,
      title,
      userRenamed: true,
      updatedAt: new Date().toISOString()
    })
  }

  try {
    const session = await renameNovelSession(chatId, title)
    upsertSessionSummary(session)
  } catch (error) {
    console.error(`Failed to rename session ${chatId}:`, error)
    if (existingSession?.mode) {
      await loadSessions(existingSession.mode, { force: true })
    }
  }
}

const handleDeleteSession = async (session) => {
  const confirmed = window.confirm(`确定删除会话“${session.title}”吗？这会同时删除对应的历史上下文。`)
  if (!confirmed) {
    return
  }

  closeActiveConnection()

  try {
    await deleteNovelSession(session.chatId)
    removeSessionSummary(session.mode, session.chatId)
    delete messagesBySessionId[session.chatId]
    delete connectionStatusBySessionId[session.chatId]

    if (selectedSessionIdByAbility[session.mode] === session.chatId) {
      selectedSessionIdByAbility[session.mode] = sessionLists[session.mode][0]?.chatId ?? ''

      if (activeModeKey.value === session.mode && selectedSessionIdByAbility[session.mode]) {
        await loadSessionDetail(selectedSessionIdByAbility[session.mode])
      }
    }
  } catch (error) {
    console.error(`Failed to delete session ${session.chatId}:`, error)
  }
}

const touchSessionBeforeStream = (session, message) => {
  if (!session) {
    return
  }

  upsertSessionSummary({
    ...session,
    updatedAt: new Date().toISOString(),
    preview: previewContent(message)
  })
}

const refreshSessionAfterStream = async (chatId) => {
  try {
    const detail = await loadSessionDetail(chatId, { replaceMessages: false })
    upsertSessionSummary(detail.session)
  } catch (error) {
    console.error(`Failed to refresh session ${chatId}:`, error)
  }
}

const sendMessage = async (rawMessage) => {
  const message = rawMessage.trim()
  if (!message || !currentAbility.value) {
    return
  }

  let session = currentSessionSummary.value

  if (!session) {
    const detail = await handleCreateSession(currentAbility.value.key)
    session = detail?.session ?? null
  }

  if (!session) {
    return
  }

  const chatId = session.chatId
  closeActiveConnection()

  if (!messagesBySessionId[chatId]) {
    try {
      await loadSessionDetail(chatId)
    } catch (error) {
      console.error(`Failed to preload session ${chatId}:`, error)
      messagesBySessionId[chatId] = []
    }
  }

  messagesBySessionId[chatId].push(createLocalMessage('user', message))
  messagesBySessionId[chatId].push(createLocalMessage('assistant', ''))
  connectionStatusBySessionId[chatId] = 'connecting'
  selectedSessionIdByAbility[currentAbility.value.key] = chatId
  touchSessionBeforeStream(session, message)

  let hasReceivedChunk = false
  const connection = currentAbility.value.createConnection(message, chatId)
  activeConnection = connection
  activeSessionId = chatId

  connection.onopen = () => {
    connectionStatusBySessionId[chatId] = 'connected'
  }

  connection.onmessage = async (event) => {
    const data = event.data ?? ''

    if (data === '[DONE]') {
      connectionStatusBySessionId[chatId] = 'disconnected'

      if (activeConnection === connection) {
        connection.close()
        activeConnection = null
        activeSessionId = ''
      }

      await refreshSessionAfterStream(chatId)
      return
    }

    hasReceivedChunk = true
    const currentMessageList = messagesBySessionId[chatId]
    if (!currentMessageList?.length) {
      return
    }

    currentMessageList[currentMessageList.length - 1].content += data
  }

  connection.onerror = async (error) => {
    console.error('Novel workbench SSE error:', error)

    const currentMessageList = messagesBySessionId[chatId]
    if (!hasReceivedChunk && currentMessageList?.length) {
      currentMessageList[currentMessageList.length - 1].content = '当前连接异常，请确认后端服务正常运行后重试。'
    }

    connectionStatusBySessionId[chatId] = hasReceivedChunk ? 'disconnected' : 'error'

    if (activeConnection === connection) {
      activeConnection = null
      activeSessionId = ''
    }

    await refreshSessionAfterStream(chatId)
  }
}

const goBack = () => {
  closeActiveConnection()
  router.push('/')
}

onBeforeUnmount(() => {
  closeActiveConnection()
})
</script>

<style scoped>
.workbench-page {
  min-height: 100vh;
  height: 100vh;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(54, 113, 255, 0.16), transparent 24%),
    radial-gradient(circle at 88% 10%, rgba(57, 205, 255, 0.14), transparent 20%),
    linear-gradient(180deg, #050a14 0%, #07101d 46%, #03070e 100%);
  color: #eef7ff;
}

.ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(16px);
  pointer-events: none;
}

.ambient-left {
  width: 280px;
  height: 280px;
  left: -80px;
  top: 240px;
  background: rgba(45, 116, 255, 0.16);
}

.ambient-right {
  width: 320px;
  height: 320px;
  right: -90px;
  top: 420px;
  background: rgba(46, 232, 255, 0.12);
}

.page-header {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  max-width: 1440px;
  width: 100%;
  margin: 0 auto;
  padding: 20px 24px 12px;
}

.back-button {
  height: 42px;
  padding: 0 18px;
  border-radius: 14px;
  border: 1px solid rgba(123, 184, 255, 0.16);
  background: rgba(10, 18, 36, 0.86);
  color: #dff0ff;
  cursor: pointer;
}

.header-copy {
  min-width: 0;
}

.header-label {
  color: #91ccff;
  font-size: 12px;
  letter-spacing: 0.22em;
}

.header-title {
  margin: 8px 0 0;
  font-size: clamp(1.6rem, 3vw, 2.4rem);
  line-height: 1.08;
}

.header-subtitle {
  margin: 8px 0 0;
  max-width: 680px;
  color: rgba(215, 232, 255, 0.64);
  line-height: 1.7;
  font-size: 13px;
}

.page-content {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 396px minmax(0, 1fr);
  gap: 24px;
  min-height: 0;
  overflow: hidden;
  max-width: 1440px;
  width: 100%;
  margin: 0 auto;
  padding: 8px 24px 20px;
}

.chat-panel {
  min-height: 0;
  overflow: hidden;
  border-radius: 28px;
  border: 1px solid rgba(123, 184, 255, 0.14);
  background: rgba(8, 16, 32, 0.82);
  box-shadow:
    0 24px 70px rgba(0, 10, 30, 0.36),
    inset 0 0 0 1px rgba(255, 255, 255, 0.03);
  padding: 14px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-mini-header {
  flex: 0 0 auto;
  padding: 6px 8px 14px;
}

.chat-mini-copy {
  min-width: 0;
}

.chat-mini-label {
  color: #94d0ff;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.chat-mini-title {
  margin-top: 6px;
  font-size: 1.15rem;
  font-weight: 700;
  line-height: 1.4;
  color: #eef7ff;
  word-break: break-word;
}

.chat-mini-meta {
  margin-top: 6px;
  color: rgba(212, 230, 255, 0.56);
  font-size: 12px;
  line-height: 1.7;
  word-break: break-word;
}

@media (max-width: 1240px) {
  .workbench-page {
    height: auto;
    min-height: 100vh;
    overflow: auto;
  }

  .page-content {
    grid-template-columns: 1fr;
    overflow: visible;
  }
}

@media (max-width: 768px) {
  .page-header,
  .page-content {
    padding-left: 16px;
    padding-right: 16px;
  }

  .page-header {
    grid-template-columns: 1fr;
  }

  .chat-panel {
    padding: 12px;
  }
}
</style>
