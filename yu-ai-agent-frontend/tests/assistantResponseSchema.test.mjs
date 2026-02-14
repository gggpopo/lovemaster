import assert from 'node:assert/strict'
import { validateAssistantResponse } from '../src/schema/assistantResponseSchema.js'

const validResponse = {
  schemaVersion: 'assistant_response_v2',
  responseId: 'resp_1',
  chatId: 'love_xxx',
  intent: 'DATE_PLANNING',
  mode: 'TOOL',
  summary: '已整理地点建议',
  safety: { level: 'safe', flags: [] },
  confidence: { overall: 0.92 },
  blocks: [
    {
      type: 'location_cards',
      id: 'blk_loc_1',
      title: '地点推荐',
      data: {
        items: [
          { name: '聚宝源', photos: ['/api/proxy/image?url=xxx'], mapUrl: 'https://uri.amap.com/marker' }
        ]
      }
    }
  ],
  followUp: {
    question: '你更喜欢正餐还是小吃？',
    choices: ['正餐', '小吃']
  }
}

const result = validateAssistantResponse(validResponse)
assert.equal(result.valid, true)

const invalidResult = validateAssistantResponse({
  schemaVersion: 'assistant_response_v2',
  blocks: [{ type: 'location_cards', data: {} }]
})
assert.equal(invalidResult.valid, false)

console.log('assistantResponse schema validator test passed')
