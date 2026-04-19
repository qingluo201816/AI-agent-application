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
const WORKFLOW_STEP_ORDER = ['intent', 'retrieve', 'generate', 'evaluate', 'deliver']
const WORKFLOW_STEP_LABELS = {
  intent: '意图识别',
  retrieve: '知识检索',
  generate: '内容生成',
  evaluate: '质量评估',
  deliver: '交付结果'
}

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

const createWorkflowTrace = () => ({
  isRunning: true,
  currentStepKey: 'intent',
  currentSummary: '等待任务启动',
  steps: WORKFLOW_STEP_ORDER.map((key, index) => ({
    key,
    title: WORKFLOW_STEP_LABELS[key],
    status: index === 0 ? 'running' : 'pending',
    summary: index === 0 ? '等待进入意图识别' : '等待执行'
  })),
  expectedPayload: '',
  hasError: false
})

const getStepIndex = (key) => WORKFLOW_STEP_ORDER.indexOf(key)

const setWorkflowStepStatus = (trace, stepKey, status, summary = '') => {
  const targetIndex = getStepIndex(stepKey)
  if (targetIndex < 0) {
    return
  }

  trace.steps.forEach((step, index) => {
    if (index < targetIndex && step.status === 'running') {
      step.status = 'done'
    }
    if (index > targetIndex && status === 'running' && step.status !== 'error') {
      step.status = 'pending'
    }
  })

  const target = trace.steps[targetIndex]
  if (status === 'running') {
    for (let i = 0; i < targetIndex; i += 1) {
      if (trace.steps[i].status !== 'error') {
        trace.steps[i].status = 'done'
      }
    }
  }
  target.status = status
  if (summary) {
    target.summary = summary
    trace.currentSummary = summary
  }
  trace.currentStepKey = stepKey
}

const markWorkflowDone = (trace, summary = '任务已完成') => {
  trace.steps.forEach((step) => {
    if (step.status !== 'error') {
      step.status = 'done'
    }
  })
  trace.isRunning = false
  trace.currentStepKey = 'deliver'
  trace.currentSummary = summary
}

const markWorkflowError = (trace, message) => {
  const currentIndex = Math.max(0, getStepIndex(trace.currentStepKey))
  trace.steps.forEach((step, index) => {
    if (index < currentIndex && step.status !== 'error') {
      step.status = 'done'
    }
  })
  trace.steps[currentIndex].status = 'error'
  trace.steps[currentIndex].summary = message
  trace.isRunning = false
  trace.hasError = true
  trace.currentSummary = message
}

const resolveStepKeyByText = (text = '') => {
  if (text.includes('意图')) return 'intent'
  if (text.includes('检索')) return 'retrieve'
  if (text.includes('生成')) return 'generate'
  if (text.includes('评估')) return 'evaluate'
  if (text.includes('交付') || text.includes('任务完成')) return 'deliver'
  return ''
}

const applyWorkflowEvent = (message, rawData) => {
  const trace = message.workflowTrace
  const data = (rawData ?? '').trim()
  if (!trace || !data) {
    return
  }

  if (data === '[HEARTBEAT]') {
    return
  }

  if (data === '[DONE]') {
    markWorkflowDone(trace, '任务流已结束')
    return
  }

  if (trace.expectedPayload === 'draft') {
    message.content = data
    trace.expectedPayload = ''
    setWorkflowStepStatus(trace, 'generate', 'done', `生成完成，共 ${data.length} 字`)
    return
  }

  if (trace.expectedPayload === 'evaluation_feedback') {
    trace.expectedPayload = ''
    setWorkflowStepStatus(trace, 'evaluate', 'running', data)
    return
  }

  if (data === '【生成内容】') {
    setWorkflowStepStatus(trace, 'generate', 'running', '正在生成正文内容...')
    trace.expectedPayload = 'draft'
    return
  }

  if (data === '【评估反馈】') {
    setWorkflowStepStatus(trace, 'evaluate', 'running', '收到评估反馈，正在修订...')
    trace.expectedPayload = 'evaluation_feedback'
    return
  }

  if (data.startsWith('【阶段】')) {
    const stageText = data.replace('【阶段】', '')
    const nextStep = resolveStepKeyByText(stageText)
    if (nextStep) {
      setWorkflowStepStatus(trace, nextStep, 'running', stageText)
    }
    return
  }

  if (data.startsWith('【错误】')) {
    markWorkflowError(trace, data.replace('【错误】', '').trim() || '执行出错')
    return
  }

  if (data.startsWith('【交付报告】')) {
    setWorkflowStepStatus(trace, 'deliver', 'done', '交付报告已生成')
    trace.isRunning = false
    return
  }

  if (data === '【任务完成】' || data.includes('工作流执行完成')) {
    setWorkflowStepStatus(trace, 'deliver', 'done', '任务执行完成')
    trace.isRunning = false
    trace.currentSummary = '任务执行完成'
    return
  }

  if (data.includes('✅ 内容通过评估')) {
    setWorkflowStepStatus(trace, 'evaluate', 'done', '内容通过评估')
    return
  }

  const inferredStep = resolveStepKeyByText(data)
  if (inferredStep) {
    setWorkflowStepStatus(trace, inferredStep, 'running', data)
  }
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
  messages.value[aiMessageIndex].workflowTrace = createWorkflowTrace()
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
      const trace = messages.value[aiMessageIndex]?.workflowTrace
      if (trace) {
        markWorkflowDone(trace, trace.hasError ? '任务结束（含错误）' : '任务执行完成')
      }
      connectionStatus.value = 'disconnected'
      if (activeConnection === connection) {
        connection.close()
        activeConnection = null
      }
      return
    }

    hasReceivedChunk = true
    const aiMessage = messages.value[aiMessageIndex]
    if (aiMessage) {
      applyWorkflowEvent(aiMessage, data)
    }
  }

  connection.onclose = () => {
    console.log('SSE连接已关闭')
    console.log('前端收到 done 事件，关闭 SSE 连接')
    const aiMessage = messages.value[aiMessageIndex]
    if (aiMessage?.workflowTrace?.isRunning) {
      markWorkflowDone(aiMessage.workflowTrace, '连接关闭，流程结束')
    }
    connectionStatus.value = 'disconnected'
    if (activeConnection === connection) {
      activeConnection = null
    }
  }

  connection.onerror = (error) => {
    console.error('Task execution SSE error:', error)
    const aiMessage = messages.value[aiMessageIndex]
    if (!hasReceivedChunk && aiMessage && !aiMessage.content) {
      aiMessage.content = '当前连接异常，请确认后端已启动，且任务执行与 PDF 生成接口可正常访问。'
    }
    if (aiMessage?.workflowTrace) {
      markWorkflowError(aiMessage.workflowTrace, '连接异常，流程中断')
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
