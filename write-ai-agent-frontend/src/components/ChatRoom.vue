<template>
  <div :class="['chat-shell', `theme-${aiType}`]">
    <div class="chat-status-bar">
      <div class="status-dot" :class="connectionStatus"></div>
      <span class="status-text">{{ statusLabel }}</span>
    </div>

    <div
      ref="messagesContainer"
      class="chat-messages"
      @scroll.passive="handleScroll"
    >
      <div v-if="!messages.length" class="empty-state">
        <div class="empty-title">{{ emptyTitle }}</div>
        <div class="empty-description">{{ emptyDescription }}</div>
      </div>

      <template v-else>
        <div
          v-for="(msg, index) in messages"
          :key="msg.id ?? `${msg.time}-${index}-${msg.isUser ? 'user' : 'ai'}`"
          class="message-row"
          :class="{ user: msg.isUser }"
        >
          <div v-if="!msg.isUser" class="message ai-message" :class="[msg.type]">
            <div class="avatar ai-avatar">
              <AiAvatarFallback :type="aiType" />
            </div>
            <div class="message-bubble">
              <div class="message-content">
                {{ msg.content }}
                <span
                  v-if="showTypingIndicator && index === messages.length - 1"
                  class="typing-indicator"
                >
                  ●
                </span>
              </div>
              <div v-if="formatTime(msg.time)" class="message-time">{{ formatTime(msg.time) }}</div>
            </div>
          </div>

          <div v-else class="message user-message" :class="[msg.type]">
            <div class="message-bubble">
              <div class="message-content">{{ msg.content }}</div>
              <div v-if="formatTime(msg.time)" class="message-time">{{ formatTime(msg.time) }}</div>
            </div>
            <div class="avatar user-avatar">
              <div class="avatar-placeholder">我</div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <transition name="scroll-fab">
      <button
        v-if="showScrollToBottom"
        type="button"
        class="scroll-to-bottom"
        @click="scrollToLatest"
      >
        回到底部
        <span class="scroll-to-bottom-arrow">↓</span>
      </button>
    </transition>

    <div class="chat-input-panel">
      <div class="chat-input-box">
        <textarea
          v-model="inputMessage"
          class="input-box"
          :placeholder="placeholder"
          :disabled="isInputDisabled"
          @keydown.enter.exact.prevent="sendMessage"
        ></textarea>
        <button
          type="button"
          class="send-button"
          :disabled="isInputDisabled || !inputMessage.trim()"
          @click="sendMessage"
        >
          发送
        </button>
      </div>
      <div class="input-tip">Enter 发送，Shift + Enter 换行</div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'

const NEAR_BOTTOM_THRESHOLD = 96

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  connectionStatus: {
    type: String,
    default: 'disconnected'
  },
  aiType: {
    type: String,
    default: 'default'
  },
  placeholder: {
    type: String,
    default: '请输入你的问题或任务...'
  },
  emptyTitle: {
    type: String,
    default: '从左侧选择会话或新建一段对话'
  },
  emptyDescription: {
    type: String,
    default: '会话历史会按入口维度持久化，切换后可以继续原来的 chatId。'
  },
  inputDisabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send-message'])

const inputMessage = ref('')
const messagesContainer = ref(null)
const isNearBottom = ref(true)
const lastMessageContent = computed(() => props.messages.at(-1)?.content ?? '')
const lastMessageIsUser = computed(() => props.messages.at(-1)?.isUser ?? false)
const isInputDisabled = computed(() => props.inputDisabled || props.connectionStatus === 'connecting')

let scrollFrameId = 0

const statusLabel = computed(() => {
  if (props.connectionStatus === 'connecting') {
    return '正在建立连接'
  }
  if (props.connectionStatus === 'connected') {
    return '实时输出中'
  }
  if (props.connectionStatus === 'error') {
    return '连接异常'
  }
  return '准备就绪'
})

const showTypingIndicator = computed(() =>
  props.connectionStatus === 'connecting' || props.connectionStatus === 'connected'
)

const showScrollToBottom = computed(() => props.messages.length > 0 && !isNearBottom.value)

const sendMessage = () => {
  const message = inputMessage.value.trim()
  if (!message) {
    return
  }

  emit('send-message', message)
  inputMessage.value = ''
}

const formatTime = (timestamp) => {
  if (!timestamp) {
    return ''
  }
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getDistanceFromBottom = (element = messagesContainer.value) => {
  if (!element) {
    return 0
  }

  return element.scrollHeight - element.scrollTop - element.clientHeight
}

const updateNearBottomState = () => {
  const element = messagesContainer.value
  if (!element) {
    return
  }

  isNearBottom.value = getDistanceFromBottom(element) <= NEAR_BOTTOM_THRESHOLD
}

const queueScrollToBottom = async (behavior = 'auto', force = false) => {
  await nextTick()
  cancelAnimationFrame(scrollFrameId)
  scrollFrameId = requestAnimationFrame(() => {
    const element = messagesContainer.value
    if (!element) {
      return
    }
    if (!force && !isNearBottom.value) {
      return
    }

    element.scrollTo({
      top: element.scrollHeight,
      behavior
    })
    isNearBottom.value = true
  })
}

const scrollToLatest = () => {
  isNearBottom.value = true
  queueScrollToBottom('smooth', true)
}

const handleScroll = () => {
  updateNearBottomState()
}

watch(
  () => props.messages,
  () => {
    isNearBottom.value = true
    queueScrollToBottom('auto', true)
  },
  {
    flush: 'post'
  }
)

watch(
  () => [props.messages.length, lastMessageContent.value],
  (currentValue, previousValue = [0, '']) => {
    const [messageCount] = currentValue
    const [previousMessageCount] = previousValue

    if (messageCount > previousMessageCount && lastMessageIsUser.value) {
      isNearBottom.value = true
      queueScrollToBottom('smooth', true)
      return
    }

    queueScrollToBottom('auto')
  },
  {
    flush: 'post'
  }
)

onMounted(() => {
  queueScrollToBottom('auto', true)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(scrollFrameId)
})
</script>

<style scoped>
.chat-shell {
  --panel-border: rgba(115, 184, 255, 0.16);
  --message-ai-bg: rgba(16, 31, 58, 0.88);
  --message-ai-border: rgba(111, 190, 255, 0.2);
  --message-user-bg: linear-gradient(135deg, #1e82ff, #35c2ff);
  --text-main: #e9f4ff;
  --text-subtle: rgba(208, 227, 255, 0.68);
  --input-bg: rgba(7, 16, 32, 0.92);
  --input-border: rgba(96, 176, 255, 0.22);
  position: relative;
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border-radius: 28px;
  border: 1px solid var(--panel-border);
  background:
    radial-gradient(circle at top right, rgba(71, 148, 255, 0.16), transparent 30%),
    linear-gradient(180deg, rgba(11, 19, 39, 0.98), rgba(5, 12, 28, 0.96));
  box-shadow:
    0 24px 80px rgba(0, 12, 36, 0.45),
    inset 0 0 0 1px rgba(255, 255, 255, 0.03);
}

.chat-status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  padding: 16px 20px 12px;
  color: var(--text-subtle);
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.status-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.28);
  box-shadow: 0 0 0 0 rgba(53, 194, 255, 0.5);
}

.status-dot.connecting,
.status-dot.connected {
  background: #35c2ff;
  box-shadow: 0 0 0 6px rgba(53, 194, 255, 0.12);
}

.status-dot.error {
  background: #ff6d7c;
  box-shadow: 0 0 0 6px rgba(255, 109, 124, 0.12);
}

.chat-messages {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  scrollbar-gutter: stable both-edges;
  padding: 8px 20px 24px;
}

.chat-messages::-webkit-scrollbar {
  width: 8px;
}

.chat-messages::-webkit-scrollbar-track {
  background: rgba(8, 18, 36, 0.32);
}

.chat-messages::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(112, 188, 255, 0.28);
}

.empty-state {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 24px;
  color: rgba(212, 230, 255, 0.72);
}

.empty-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: #eef7ff;
}

.empty-description {
  max-width: 520px;
  margin-top: 12px;
  line-height: 1.8;
}

.message-row {
  display: flex;
  margin-bottom: 18px;
}

.message-row.user {
  justify-content: flex-end;
}

.message {
  display: flex;
  gap: 12px;
  max-width: min(86%, 920px);
  min-width: 0;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.22);
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d7dff, #39d2ff);
  color: #fff;
  font-weight: 700;
}

.message-bubble {
  min-width: 0;
  max-width: 100%;
  padding: 14px 16px 12px;
  border-radius: 20px;
  border: 1px solid transparent;
  backdrop-filter: blur(16px);
}

.ai-message .message-bubble {
  background: var(--message-ai-bg);
  border-color: var(--message-ai-border);
}

.user-message .message-bubble {
  background: var(--message-user-bg);
  color: #fff;
  box-shadow: 0 18px 40px rgba(27, 126, 255, 0.22);
}

.message-content {
  color: var(--text-main);
  font-size: 15px;
  line-height: 1.72;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.user-message .message-content {
  color: #fff;
}

.message-time {
  margin-top: 8px;
  color: rgba(214, 232, 255, 0.58);
  font-size: 12px;
  text-align: right;
}

.typing-indicator {
  margin-left: 6px;
  color: #8fe0ff;
  animation: blink 0.9s infinite;
}

.scroll-to-bottom {
  position: absolute;
  right: 24px;
  bottom: 112px;
  z-index: 3;
  height: 40px;
  padding: 0 14px;
  border: 1px solid rgba(111, 190, 255, 0.22);
  border-radius: 999px;
  background: rgba(8, 18, 36, 0.92);
  color: #dcf2ff;
  font-size: 13px;
  letter-spacing: 0.04em;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  box-shadow:
    0 10px 26px rgba(0, 12, 36, 0.34),
    0 0 26px rgba(66, 164, 255, 0.12);
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.scroll-to-bottom:hover {
  transform: translateY(-1px);
  border-color: rgba(122, 205, 255, 0.38);
  box-shadow:
    0 14px 34px rgba(0, 14, 42, 0.42),
    0 0 30px rgba(66, 164, 255, 0.18);
}

.scroll-fab-enter-active,
.scroll-fab-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.scroll-fab-enter-from,
.scroll-fab-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.chat-input-panel {
  flex: 0 0 auto;
  border-top: 1px solid rgba(123, 184, 255, 0.12);
  background: rgba(4, 11, 24, 0.86);
  padding: 16px 18px 18px;
}

.chat-input-box {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.input-box {
  flex: 1;
  min-height: 56px;
  max-height: 180px;
  resize: vertical;
  border: 1px solid var(--input-border);
  border-radius: 18px;
  background: var(--input-bg);
  color: var(--text-main);
  padding: 14px 16px;
  font-size: 15px;
  line-height: 1.6;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.input-box:focus {
  border-color: rgba(99, 194, 255, 0.52);
  box-shadow: 0 0 0 4px rgba(45, 167, 255, 0.1);
}

.input-box::placeholder {
  color: rgba(189, 214, 248, 0.45);
}

.send-button {
  min-width: 112px;
  height: 52px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, #1f89ff, #37c8ff);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.08em;
  cursor: pointer;
  box-shadow: 0 16px 34px rgba(31, 137, 255, 0.24);
  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

.send-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 20px 40px rgba(31, 137, 255, 0.28);
}

.send-button:disabled,
.input-box:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.input-tip {
  margin-top: 10px;
  color: rgba(197, 220, 255, 0.45);
  font-size: 12px;
}

@keyframes blink {
  0%,
  100% {
    opacity: 0.15;
  }
  50% {
    opacity: 1;
  }
}

@media (max-width: 768px) {
  .chat-shell {
    border-radius: 22px;
  }

  .chat-status-bar,
  .chat-messages {
    padding-left: 14px;
    padding-right: 14px;
  }

  .chat-input-panel {
    padding: 14px;
  }

  .chat-input-box {
    flex-direction: column;
    align-items: stretch;
  }

  .send-button {
    width: 100%;
  }

  .message {
    max-width: 100%;
  }

  .scroll-to-bottom {
    right: 16px;
    bottom: 148px;
  }
}
</style>
