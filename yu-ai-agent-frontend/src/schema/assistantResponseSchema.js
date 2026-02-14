export const ASSISTANT_RESPONSE_SCHEMA_VERSION = 'assistant_response_v2'

const BLOCK_TYPES = new Set([
  'text',
  'location_cards',
  'action_plan',
  'message_options',
  'risk_alert'
])

const isObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value)

export const validateAssistantResponse = (value) => {
  const errors = []

  if (!isObject(value)) {
    return { valid: false, errors: ['response must be an object'] }
  }

  if (value.schemaVersion !== ASSISTANT_RESPONSE_SCHEMA_VERSION) {
    errors.push(`schemaVersion must be ${ASSISTANT_RESPONSE_SCHEMA_VERSION}`)
  }
  if (!Array.isArray(value.blocks)) {
    errors.push('blocks must be an array')
  }

  if (Array.isArray(value.blocks)) {
    value.blocks.forEach((block, index) => {
      if (!isObject(block)) {
        errors.push(`blocks[${index}] must be an object`)
        return
      }
      if (!BLOCK_TYPES.has(block.type)) {
        errors.push(`blocks[${index}].type is unsupported`)
      }
      if (!isObject(block.data)) {
        errors.push(`blocks[${index}].data must be an object`)
        return
      }

      if (block.type === 'text' && typeof block.data.text !== 'string') {
        errors.push(`blocks[${index}].data.text must be a string`)
      }
      if (block.type === 'location_cards' && !Array.isArray(block.data.items)) {
        errors.push(`blocks[${index}].data.items must be an array`)
      }
      if (block.type === 'action_plan' && !Array.isArray(block.data.steps)) {
        errors.push(`blocks[${index}].data.steps must be an array`)
      }
      if (block.type === 'message_options' && !Array.isArray(block.data.options)) {
        errors.push(`blocks[${index}].data.options must be an array`)
      }
      if (block.type === 'risk_alert' && typeof block.data.message !== 'string') {
        errors.push(`blocks[${index}].data.message must be a string`)
      }
    })
  }

  if (value.followUp && !isObject(value.followUp)) {
    errors.push('followUp must be an object')
  }

  return {
    valid: errors.length === 0,
    errors
  }
}
