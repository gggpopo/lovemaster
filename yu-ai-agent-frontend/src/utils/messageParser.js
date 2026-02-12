/**
 * 解析后端输出的结构化标记（例如 LOCATION_CARD），并将文本中的图片 URL 拆分为可渲染的片段。
 *
 * @param {string} rawMessage
 * @returns {{type: 'text' | 'location_card' | 'image', content: string}[]}
 */
export function parseMessage(rawMessage) {
  const text = String(rawMessage || '')
  const segments = []

  /**
   * 将纯文本按图片链接拆分为 text/image 段。
   * 支持：
   * - Markdown 图片：![](...)
   * - 直链图片：https://xx/yy.jpg
   */
  const splitTextByImages = (plainText) => {
    const result = []
    const input = String(plainText || '')
    if (!input) return result

    // 1) Markdown 图片语法
    // 2) 直链图片 URL（常见格式）
    const imageRegex = /!\[[^\]]*\]\((https?:\/\/[^\s)]+)\)|((?:https?:\/\/)[^\s]+?\.(?:png|jpe?g|webp|gif)(?:\?[^\s]*)?)/gi

    let last = 0
    let m
    while ((m = imageRegex.exec(input)) !== null) {
      if (m.index > last) {
        const before = input.slice(last, m.index)
        if (before) result.push({ type: 'text', content: before })
      }

      let url = m[1] || m[2] || ''
      // 去掉常见的尾随标点（避免粘连中文句号、右括号等）
      url = url.replace(/[)\],。；;！!？?]+$/g, '')
      if (url) {
        result.push({ type: 'image', content: url })
      } else {
        result.push({ type: 'text', content: m[0] })
      }

      last = m.index + m[0].length
    }

    if (last < input.length) {
      const remaining = input.slice(last)
      if (remaining) result.push({ type: 'text', content: remaining })
    }

    return result
  }

  const locationCardRegex = /<!--LOCATION_CARD:(.*?)-->/gs
  let lastIndex = 0
  let match

  while ((match = locationCardRegex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      const before = text.slice(lastIndex, match.index)
      if (before) {
        segments.push(...splitTextByImages(before))
      }
    }

    const jsonPart = match[1]
    try {
      JSON.parse(jsonPart)
      segments.push({ type: 'location_card', content: jsonPart })
    } catch (e) {
      // JSON 不合法时当作纯文本渲染
      segments.push({ type: 'text', content: match[0] })
    }

    lastIndex = match.index + match[0].length
  }

  if (lastIndex < text.length) {
    const remaining = text.slice(lastIndex)
    if (remaining) {
      segments.push(...splitTextByImages(remaining))
    }
  }

  return segments
}
