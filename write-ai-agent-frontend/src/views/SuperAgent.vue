<template>
  <div class="super-page">
    <div class="ambient ambient-left"></div>
    <div class="ambient ambient-right"></div>

    <header class="page-header">
      <button class="back-button" @click="goBack">返回首页</button>
      <div class="header-copy">
        <div class="header-label">TASK EXECUTION</div>
        <h1 class="header-title">AI 超级写作智能体</h1>
        <p class="header-subtitle">
          面向小说创作管理与归档的任务执行入口，适合整理角色资料、汇总剧情、生成大纲并导出 PDF。
        </p>
      </div>
      <div class="session-pill">任务会话：{{ chatId }}</div>
    </header>

    <main class="page-content">
      <section class="briefing-strip">
        <div class="briefing-core">
          <span class="briefing-dot"></span>
          <span class="briefing-summary">任务执行与 PDF 归档工作流</span>
        </div>
        <div class="briefing-tags">
          <span class="briefing-tag">角色档案</span>
          <span class="briefing-tag">剧情汇总</span>
          <span class="briefing-tag">大纲整理</span>
          <span class="briefing-tag">PDF 导出</span>
        </div>
      </section>

      <section class="chat-panel">
        <ChatRoom
          :messages="messages"
          :connection-status="connectionStatus"
          placeholder="例如：整理主角团角色档案，附上关系摘要，并导出为 PDF。"
          ai-type="super"
          @send-message="sendMessage"
        />
      </section>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useHead } from '@vueuse/head'
import { useRouter } from 'vue-router'
import AppFooter from '../components/AppFooter.vue'
import ChatRoom from '../components/ChatRoom.vue'
import { chatWithSuperAgent } from '../api'

useHead({
  title: 'AI 超级写作智能体 - 晴落 AI 写作智能体平台',
  meta: [
    {
      name: 'description',
      content: 'AI 超级写作智能体负责小说资料整理、任务执行、归档输出和 PDF 生成。'
    },
    {
      name: 'keywords',
      content: 'AI超级写作智能体,任务执行,PDF生成,剧情归档,角色档案,小说资料整理'
    }
  ]
})

const router = useRouter()

const generateChatId = () => `task_execution_${Math.random().toString(36).slice(2, 10)}`
const generateMessageId = () => `task_message_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

const chatId = ref(generateChatId())
const messages = ref([])
const connectionStatus = ref('disconnected')
let activeConnection = null

const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    id: generateMessageId(),
    content,
    isUser,
    type,
    time: Date.now()
  })
}

const closeConnection = () => {
  if (activeConnection) {
    activeConnection.close()
    activeConnection = null
  }
}

const sendMessage = (message) => {
  closeConnection()
  addMessage(message, true, 'user-question')

  const aiMessageIndex = messages.value.length
  addMessage('', false, 'ai-answer')
  connectionStatus.value = 'connecting'

  let hasReceivedChunk = false
  const connection = chatWithSuperAgent(message, chatId.value)
  activeConnection = connection

  connection.onopen = () => {
    connectionStatus.value = 'connected'
  }

  connection.onmessage = (event) => {
    const data = event.data ?? ''

    if (data === '[DONE]') {
      connectionStatus.value = 'disconnected'
      if (activeConnection === connection) {
        connection.close()
        activeConnection = null
      }
      return
    }

    hasReceivedChunk = true
    messages.value[aiMessageIndex].content += data
  }

  connection.onclose = () => {
    console.log('SSE连接已关闭')
    console.log('前端收到 done 事件，关闭 SSE 连接')
    connectionStatus.value = 'disconnected'
    if (activeConnection === connection) {
      activeConnection = null
    }
  }

  connection.onerror = (error) => {
    console.error('Task execution SSE error:', error)
    if (!hasReceivedChunk && !messages.value[aiMessageIndex].content) {
      messages.value[aiMessageIndex].content = '当前连接异常，请确认后端已启动，且任务执行与 PDF 生成接口可正常访问。'
    }
    connectionStatus.value = hasReceivedChunk ? 'disconnected' : 'error'
    if (activeConnection === connection) {
      activeConnection = null
    }
  }
}

const goBack = () => {
  closeConnection()
  router.push('/')
}

onMounted(() => {
  addMessage('这里是“任务执行与 PDF 生成”入口。你可以直接让我整理角色资料、汇总剧情、生成大纲，或导出 PDF。', false, 'ai-welcome')
})

onBeforeUnmount(() => {
  closeConnection()
})
</script>

<style scoped>
.super-page {
  min-height: 100vh;
  height: 100vh;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(38, 155, 255, 0.16), transparent 24%),
    radial-gradient(circle at 82% 14%, rgba(40, 255, 220, 0.12), transparent 22%),
    linear-gradient(180deg, #041018 0%, #04131e 44%, #02070d 100%);
  color: #eefaff;
}

.ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(14px);
  pointer-events: none;
}

.ambient-left {
  top: 260px;
  left: -80px;
  width: 260px;
  height: 260px;
  background: rgba(30, 175, 255, 0.14);
}

.ambient-right {
  top: 430px;
  right: -100px;
  width: 320px;
  height: 320px;
  background: rgba(42, 255, 222, 0.12);
}

.page-header {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 18px;
  align-items: center;
  max-width: 1320px;
  width: 100%;
  margin: 0 auto;
  padding: 28px 24px 18px;
}

.back-button {
  height: 44px;
  padding: 0 18px;
  border-radius: 14px;
  border: 1px solid rgba(114, 228, 255, 0.16);
  background: rgba(7, 22, 34, 0.84);
  color: #def7ff;
  cursor: pointer;
}

.header-label {
  color: #8de8ff;
  font-size: 12px;
  letter-spacing: 0.22em;
}

.header-title {
  margin: 10px 0 8px;
  font-size: clamp(2.2rem, 4vw, 3.4rem);
  line-height: 1.08;
}

.header-subtitle {
  margin: 0;
  max-width: 780px;
  color: rgba(214, 238, 255, 0.72);
  line-height: 1.82;
}

.session-pill {
  justify-self: end;
  padding: 12px 16px;
  border-radius: 16px;
  border: 1px solid rgba(114, 228, 255, 0.16);
  background: rgba(7, 22, 34, 0.82);
  color: rgba(222, 247, 255, 0.76);
  font-size: 13px;
}

.page-content {
  position: relative;
  z-index: 1;
  min-height: 0;
  overflow: hidden;
  max-width: 1320px;
  width: 100%;
  margin: 0 auto;
  padding: 8px 24px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.briefing-strip {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-radius: 22px;
  border: 1px solid rgba(114, 228, 255, 0.14);
  background: rgba(6, 22, 35, 0.82);
  box-shadow:
    0 24px 70px rgba(0, 12, 25, 0.22),
    inset 0 0 0 1px rgba(255, 255, 255, 0.03);
}

.briefing-core {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.briefing-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #72e4ff;
  box-shadow: 0 0 0 8px rgba(114, 228, 255, 0.12);
}

.briefing-summary {
  color: #e7fbff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.briefing-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.briefing-tag {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(114, 228, 255, 0.16);
  background: rgba(6, 20, 32, 0.82);
  color: #dbf9ff;
  font-size: 13px;
}

.chat-panel {
  min-height: 0;
  overflow: hidden;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
}

@media (max-width: 1080px) {
  .super-page {
    height: auto;
    min-height: 100vh;
    overflow: auto;
  }

  .page-header {
    grid-template-columns: 1fr;
  }

  .session-pill {
    justify-self: start;
  }

  .page-content {
    overflow: visible;
  }

  .chat-panel {
    overflow: visible;
  }

  .briefing-strip {
    flex-direction: column;
    align-items: flex-start;
  }

  .briefing-tags {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .page-header,
  .page-content {
    padding-left: 16px;
    padding-right: 16px;
  }

  .briefing-strip {
    padding: 14px;
    border-radius: 18px;
  }
}
</style>
