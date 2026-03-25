import axios from 'axios'

const API_BASE_URL = process.env.NODE_ENV === 'production'
  ? '/api'
  : 'http://localhost:8123/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

const normalizeSseBuffer = (buffer) => buffer.replace(/\r\n/g, '\n')

const parseSseEventData = (rawEvent) => {
  if (!rawEvent) {
    return null
  }

  const lines = rawEvent.split('\n')
  const dataLines = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())

  if (!dataLines.length) {
    return null
  }

  return dataLines.join('\n')
}

export const connectPostSSE = (url, payload = {}) => {
  const abortController = new AbortController()
  let manuallyClosed = false

  const connection = {
    onopen: null,
    onmessage: null,
    onerror: null,
    close() {
      manuallyClosed = true
      abortController.abort()
    }
  }

  const emitEvent = (data) => {
    if (data === null || data === undefined) {
      return
    }
    if (connection.onmessage) {
      connection.onmessage({ data })
    }
  }

  ;(async () => {
    try {
      const response = await fetch(`${API_BASE_URL}${url}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream'
        },
        body: JSON.stringify(payload),
        signal: abortController.signal
      })

      if (!response.ok) {
        throw new Error(`SSE request failed with status ${response.status}`)
      }

      if (!response.body) {
        throw new Error('SSE response body is empty')
      }

      if (connection.onopen) {
        connection.onopen(response)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }

        buffer = normalizeSseBuffer(buffer + decoder.decode(value, { stream: true }))

        while (buffer.includes('\n\n')) {
          const delimiterIndex = buffer.indexOf('\n\n')
          const rawEvent = buffer.slice(0, delimiterIndex)
          buffer = buffer.slice(delimiterIndex + 2)
          emitEvent(parseSseEventData(rawEvent))
        }
      }

      buffer = normalizeSseBuffer(buffer + decoder.decode())
      if (buffer.trim()) {
        emitEvent(parseSseEventData(buffer.trim()))
      }
    } catch (error) {
      if (!manuallyClosed && error.name !== 'AbortError' && connection.onerror) {
        connection.onerror(error)
      }
    }
  })()

  return connection
}

const createNovelStreamConnector = (endpoint) => (message, chatId = '') => {
  return connectPostSSE(endpoint, { message, chatId })
}

export const chatWithNovelStateMemory = createNovelStreamConnector('/ai/novel/state-memory/sse')
export const chatWithNovelInspirationAssist = createNovelStreamConnector('/ai/novel/inspiration-assist/sse')
export const chatWithNovelKeywordContinuation = createNovelStreamConnector('/ai/novel/keyword-continuation/sse')
export const chatWithNovelTaskExecutionPdf = createNovelStreamConnector('/ai/novel/task-execution-pdf/sse')

export const listNovelSessions = async (mode) => {
  const response = await request.get('/ai/novel/sessions', {
    params: { mode }
  })
  return response.data
}

export const createNovelSession = async (mode) => {
  const response = await request.post('/ai/novel/sessions', { mode })
  return response.data
}

export const getNovelSessionDetail = async (chatId) => {
  const response = await request.get(`/ai/novel/sessions/${chatId}`)
  return response.data
}

export const renameNovelSession = async (chatId, title) => {
  const response = await request.put(`/ai/novel/sessions/${chatId}/title`, { title })
  return response.data
}

export const deleteNovelSession = async (chatId) => {
  await request.delete(`/ai/novel/sessions/${chatId}`)
}

export const chatWithManus = (message, chatId = '') => {
  return chatWithNovelTaskExecutionPdf(message, chatId)
}

export { request }

export default {
  chatWithNovelStateMemory,
  chatWithNovelInspirationAssist,
  chatWithNovelKeywordContinuation,
  chatWithNovelTaskExecutionPdf,
  chatWithManus,
  listNovelSessions,
  createNovelSession,
  getNovelSessionDetail,
  renameNovelSession,
  deleteNovelSession
}
