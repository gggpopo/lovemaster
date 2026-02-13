import assert from 'node:assert/strict'
import { parseMessage } from '../src/utils/messageParser.js'

{
  const rawMessage = `给你两家店：
![实景图1](https://example.com/a.jpg) | ![实景图2](
另外再推荐一家。`

  const segments = parseMessage(rawMessage)
  const imageSegments = segments.filter((segment) => segment.type === 'image')
  const textContent = segments
    .filter((segment) => segment.type === 'text')
    .map((segment) => segment.content)
    .join('\n')

  assert.equal(imageSegments.length, 1, 'should keep only the valid markdown image')
  assert.equal(imageSegments[0].content, 'https://example.com/a.jpg')
  assert.equal(textContent.includes('![实景图2]('), false, 'should remove malformed markdown image syntax')
}

{
  const rawMessage = `推荐店铺：
[实景图1](http://store.is.autonavi.com/showpic/abc123) | [实景图2](http://store.is.autonavi.com/query_pic?id=123)
已帮你整理好。`

  const segments = parseMessage(rawMessage)
  const imageSegments = segments.filter((segment) => segment.type === 'image')
  const textContent = segments
    .filter((segment) => segment.type === 'text')
    .map((segment) => segment.content)
    .join('\n')

  assert.equal(imageSegments.length, 2, 'plain markdown links should be converted into image segments')
  assert.equal(imageSegments[0].content.startsWith('/api/proxy/image?url='), true, 'amap image link should be proxied')
  assert.equal(imageSegments[1].content.startsWith('/api/proxy/image?url='), true, 'amap image link should be proxied')
  assert.equal(textContent.includes('[实景图1]('), false, 'should not leak markdown link text for image links')
  assert.equal(textContent.includes('[实景图2]('), false, 'should not leak markdown link text for image links')
}

console.log('messageParser malformed markdown image test passed')
