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
  const fullUrl = `${API_BASE_URL}/ai/love_app/chat/vision`

  const abortController = new AbortController()

  fetch(fullUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message, chatId, images }),
    signal: abortController.signal
  }).then(async (response) => {
    if (!response.ok) {
      const errorText = await response.text().catch(() => '')
      throw new Error(`Vision API HTTP ${response.status}: ${errorText || response.statusText}`)
    }

    // 某些环境下 response.body 可能为空（不支持流式），回退为普通文本读取
    if (!response.body) {
      const text = await response.text()
      if (text && onMessage) onMessage(text)
      if (onMessage) onMessage('[DONE]')
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    // 缓冲区：解决 chunk 边界把 "data:" 切断导致前端解析不到的问题
    let buffer = ''
    // SSE 事件可能由多行 data: 组成，遇到空行再一次性提交
    let eventDataLines = []

    const flushEvent = () => {
      if (eventDataLines.length === 0) return
      const data = eventDataLines.join('\n')
      eventDataLines = []
      if (onMessage) onMessage(data)
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        flushEvent()
        if (onMessage) onMessage('[DONE]')
        return
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''

      for (let rawLine of lines) {
        // 兼容 \r\n
        const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine

        // 空行：一个 SSE event 结束
        if (line === '') {
          flushEvent()
          continue
        }

        // 只处理 data: 行，忽略 event/id/retry 等字段
        if (line.startsWith('data:')) {
          eventDataLines.push(line.slice(5).trimStart())
        }
      }
    }
  }).catch((error) => {
    // 主动取消不算错误
    if (error?.name === 'AbortError') return
    if (onError) onError(error)
  })

  // 返回一个可以取消的对象
  return {
    close: () => {
      abortController.abort()
    }
  }
}

// AI超级智能体聊天
export const chatWithManus = (message) => {
  return connectSSE('/ai/manus/chat', { message })
}

export default {
  chatWithLoveApp,
  chatWithLoveAppVision,
  chatWithManus
} 
