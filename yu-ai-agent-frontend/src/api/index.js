import axios from 'axios'

// 根据环境变量设置 API 基础 URL
const API_BASE_URL = process.env.NODE_ENV === 'production' 
 ? '/api' // 生产环境使用相对路径，适用于前后端部署在同一域名下
 : 'http://localhost:8123/api' // 开发环境指向本地后端服务

// 创建axios实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// POST + SSE（fetch 流式）通用封装
export const connectSSEByPost = (url, body, onMessage, onError) => {
  const fullUrl = `${API_BASE_URL}${url}`
  const abortController = new AbortController()

  fetch(fullUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body || {}),
    signal: abortController.signal
  }).then(async (response) => {
    if (!response.ok) {
      const errorText = await response.text().catch(() => '')
      throw new Error(`SSE API HTTP ${response.status}: ${errorText || response.statusText}`)
    }

    if (!response.body) {
      const text = await response.text()
      if (text && onMessage) onMessage(text)
      if (onMessage) onMessage('[DONE]')
      return
    }

    const contentType = String(response.headers.get('content-type') || '').toLowerCase()
    const isSseLike = contentType.includes('text/event-stream')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let eventDataLines = []

    const flushEvent = () => {
      if (eventDataLines.length === 0) return
      const data = eventDataLines.join('\n')
      eventDataLines = []
      if (onMessage) onMessage(data)
    }

    const processSseLine = (rawLine) => {
      const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
      if (line === '') {
        flushEvent()
        return
      }

      if (line.startsWith('data:')) {
        eventDataLines.push(line.slice(5).trimStart())
        return
      }

      if (
        line.startsWith('event:') ||
        line.startsWith('id:') ||
        line.startsWith('retry:') ||
        line.startsWith(':')
      ) {
        return
      }

      // 兼容后端未按 SSE data: 前缀输出的场景
      eventDataLines.push(line)
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        if (isSseLike) {
          // 处理最后一段可能未换行的数据
          if (buffer) {
            processSseLine(buffer)
            buffer = ''
          }
          flushEvent()
        } else if (buffer && onMessage) {
          onMessage(buffer)
          buffer = ''
        }
        if (onMessage) onMessage('[DONE]')
        return
      }

      const chunkText = decoder.decode(value, { stream: true })
      if (!isSseLike) {
        if (chunkText && onMessage) onMessage(chunkText)
        continue
      }

      buffer += chunkText
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''
      for (let rawLine of lines) {
        processSseLine(rawLine)
      }
    }
  }).catch((error) => {
    if (error?.name === 'AbortError') return
    if (onError) onError(error)
  })

  return {
    close: () => abortController.abort()
  }
}

// 封装SSE连接
export const connectSSE = (url, params, onMessage, onError) => {
  // 构建带参数的URL
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  
  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  
  // 创建EventSource
  const eventSource = new EventSource(fullUrl)
  
  eventSource.onmessage = event => {
    let data = event.data
    
    // 检查是否是特殊标记
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      // 处理普通消息
      if (onMessage) onMessage(data)
    }
  }
  
  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }
  
  // 返回eventSource实例，以便后续可以关闭连接
  return eventSource
}

// AI恋爱大师聊天
export const chatWithLoveApp = (message, chatId) => {
  return connectSSE('/ai/love_app/chat/sse', { message, chatId })
}

// AI恋爱大师聊天（支持图片）
export const chatWithLoveAppVision = (message, chatId, images, onMessage, onError) => {
  return connectSSEByPost('/ai/love_app/chat/vision', { message, chatId, images }, onMessage, onError)
}

// 统一编排聊天（支持路由/策略/步骤事件）
export const chatWithOrchestrated = (message, chatId, images = [], forceMode = '', sceneId = '', onMessage, onError) => {
  return connectSSEByPost('/ai/orchestrated/chat', { message, chatId, images, forceMode, sceneId }, onMessage, onError)
}

// AI超级智能体聊天
export const chatWithManus = (message) => {
  return connectSSE('/ai/manus/chat', { message })
}

export default {
  chatWithLoveApp,
  chatWithLoveAppVision,
  chatWithOrchestrated,
  chatWithManus
}
