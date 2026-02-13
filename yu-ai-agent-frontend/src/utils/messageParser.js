/**
 * 解析后端输出的结构化标记（例如 LOCATION_CARD），并将文本中的图片 URL 拆分为可渲染的片段。
 *
 * @param {string} rawMessage
 * @returns {{type: 'text' | 'location_card' | 'image', content: string}[]}
 */
export function parseMessage(rawMessage) {
  const text = String(rawMessage || '')
  const segments = []

  const cleanupSegments = (inputSegments) => {
    const merged = []
    const list = Array.isArray(inputSegments) ? inputSegments : []

    for (const segment of list) {
      if (!segment || !segment.type) continue

      if (segment.type === 'text') {
        const content = String(segment.content || '')
        if (!content) continue
        if (/^\s*\|\s*$/.test(content)) continue

        const prev = merged[merged.length - 1]
        if (prev && prev.type === 'text') {
          prev.content += content
        } else {
          merged.push({ type: 'text', content })
        }
        continue
      }

      merged.push(segment)
    }

    return merged
  }

  const trimTrailingPunctuation = (url) => {
    return String(url || '').replace(/[)\],。；;！!？?]+$/g, '')
  }

  const isAmapImageHost = (host) => {
    const value = String(host || '').toLowerCase()
    return value === 'store.is.autonavi.com' || value === 'aos-comment.amap.com'
  }

  const isLikelyImageUrl = (url) => {
    const value = trimTrailingPunctuation(url)
    if (!value) return false

    if (value.startsWith('/api/proxy/image?url=')) return true
    if (/\.(?:png|jpe?g|webp|gif)(?:\?|$)/i.test(value)) return true

    try {
      const parsed = new URL(value)
      if (isAmapImageHost(parsed.hostname)) return true
      return /\/(?:showpic|query_pic)(?:\/|$|\?)/i.test(parsed.pathname + parsed.search)
    } catch (e) {
      return false
    }
  }

  const toRenderableImageUrl = (url) => {
    const value = trimTrailingPunctuation(url)
    if (!value) return ''
    if (value.startsWith('/api/proxy/image?url=')) return value

    try {
      const parsed = new URL(value)
      if (isAmapImageHost(parsed.hostname)) {
        return `/api/proxy/image?url=${encodeURIComponent(value)}`
      }
    } catch (e) {
      // ignore
    }
    return value
  }

  const isLikelyImageLabel = (label) => {
    const value = String(label || '')
    return /(实景图|图片|配图|图\s*\d+|photo|image|img)/i.test(value)
  }

  const normalizeMarkdownImageLinks = (plainText) => {
    const input = String(plainText || '')
    if (!input) return input

    // 将 [实景图1](url) 这类普通 markdown 链接转换为图片，避免显示原始链接文本
    return input.replace(/(?<!!)\[([^\]]*)\]\((https?:\/\/[^\s)]+)\)/gi, (raw, label, url) => {
      if (!isLikelyImageLabel(label) && !isLikelyImageUrl(url)) {
        return raw
      }
      const renderableUrl = toRenderableImageUrl(url)
      return renderableUrl ? `![](${renderableUrl})` : ''
    })
  }

  const sanitizeMalformedImageMarkdown = (plainText) => {
    let output = String(plainText || '')
    if (!output) return output

    output = normalizeMarkdownImageLinks(output)
    // 清理未闭合的 markdown 图片 / 链接片段，避免在 UI 中泄漏原始语法
    output = output.replace(/!?\[[^\]]*]\([^)\n]*(?=\s*\||\n|$)/g, '')
    // 清理仅用于分隔图片的残留竖线
    output = output.replace(/[ \t]*\|[ \t]*(?=\n|$)/g, '')
    return output
  }

  /**
   * 将纯文本按图片链接拆分为 text/image 段。
   * 支持：
   * - Markdown 图片：![](...)
   * - 直链图片：https://xx/yy.jpg
   */
  const splitTextByImages = (plainText) => {
    const result = []
    const input = sanitizeMalformedImageMarkdown(plainText)
    if (!input) return result

    // 1) Markdown 图片语法（含代理链接）
    // 2) 直链图片 URL（扩展名或高德图片域名）
    const imageRegex = /!\[[^\]]*\]\((\/api\/proxy\/image\?url=[^\s)]+|https?:\/\/[^\s)]+)\)|((?:https?:\/\/)[^\s]+?\.(?:png|jpe?g|webp|gif)(?:\?[^\s]*)?|https?:\/\/(?:store\.is\.autonavi\.com|aos-comment\.amap\.com)[^\s)]*)/gi

    let last = 0
    let m
    while ((m = imageRegex.exec(input)) !== null) {
      if (m.index > last) {
        const before = input.slice(last, m.index)
        if (before) result.push({ type: 'text', content: before })
      }

      let url = m[1] || m[2] || ''
      url = toRenderableImageUrl(url)
      if (url && isLikelyImageUrl(url)) {
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

  return cleanupSegments(segments)
}
