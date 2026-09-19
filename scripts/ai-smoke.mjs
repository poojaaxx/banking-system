#!/usr/bin/env node
/**
 * Live Groq smoke test. Sends ONE tiny synthetic request -- no customer data --
 * shaped like the app's own calls (same model, JSON mode, reasoning effort),
 * and reports only status, latency and structural checks.
 *
 *   GROQ_API_KEY=... node scripts/ai-smoke.mjs            # skips (exit 0) when no key
 *   GROQ_API_KEY=... node scripts/ai-smoke.mjs --require   # exit 2 when no key
 *
 * The key is read from the environment only and is never printed, logged or
 * written anywhere. When no key is configured this script says so plainly:
 * live-provider verification has NOT been performed.
 */
const key = process.env.GROQ_API_KEY
const require = process.argv.includes('--require')

if (!key) {
  console.log('SKIPPED: GROQ_API_KEY is not set. Live-provider verification is INCOMPLETE (nothing was sent).')
  process.exit(require ? 2 : 0)
}

const endpoint = process.env.GROQ_ENDPOINT ?? 'https://api.groq.com/openai/v1/chat/completions'
const model = process.env.GROQ_MODEL ?? 'openai/gpt-oss-20b'
const body = {
  model,
  temperature: 0.2,
  max_completion_tokens: 300,
  response_format: { type: 'json_object' },
  messages: [
    { role: 'system', content: 'Reply with ONLY a JSON object of the form {"answer": "<one short sentence>"}.' },
    { role: 'user', content: 'Say hello.' },
  ],
}
if (model.startsWith('openai/gpt-oss')) {
  body.reasoning_effort = process.env.GROQ_REASONING_EFFORT ?? 'low'
}

async function main() {
  const started = performance.now()
  let response
  try {
    response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15_000),
    })
  } catch (error) {
    console.log(`FAILED: request did not complete (${error.name}). Live-provider verification NOT achieved.`)
    return 1
  }
  const latencyMs = Math.round(performance.now() - started)

  const h = (name) => response.headers.get(name) ?? '-'
  console.log(`status=${response.status} latencyMs=${latencyMs} model=${model}`)
  console.log(`rate-limit headers: remaining-requests=${h('x-ratelimit-remaining-requests')} remaining-tokens=${h('x-ratelimit-remaining-tokens')} retry-after=${h('retry-after')}`)

  if (!response.ok) {
    console.log('FAILED: provider returned an error status. Live-provider verification NOT achieved.')
    return 1
  }

  const payload = await response.json().catch(() => null)
  const content = payload?.choices?.[0]?.message?.content
  let parsed = null
  try {
    parsed = JSON.parse(content)
  } catch {
    /* handled below */
  }
  const checks = {
    hasChoice: Boolean(content),
    contentIsJson: parsed !== null && typeof parsed === 'object',
    hasAnswerString: typeof parsed?.answer === 'string' && parsed.answer.length > 0,
  }
  console.log('checks:', JSON.stringify(checks))
  if (Object.values(checks).every(Boolean)) {
    console.log('PASSED: the provider accepted the request shape and returned parseable JSON.')
    return 0
  }
  console.log('FAILED: reply did not have the expected structure. Live-provider verification NOT achieved.')
  return 1
}

// Set exitCode instead of calling process.exit(): exiting with fetch sockets still closing trips a libuv
// assertion on Windows (exit code 127 after the result had already printed).
process.exitCode = await main()
