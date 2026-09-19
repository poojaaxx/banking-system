# AI features (optional)

SecureBank works fully without any AI. Two features can use a language model —
the read-only **assistant** and the **on-demand category suggestion** — and both
have a labelled, deterministic fallback. Insights, unusual-activity flags and
forecasts (see [insights.md](insights.md)) do **not** use a model at all.

## What the model can and cannot do

- It can return text. It has **no tools**: it cannot move money, change
  balances, freeze accounts, or run SQL. Its output only ever becomes display
  text, after verification.
- It only sees a small, pre-computed, single-customer context (below), never a
  database handle.
- Prompts treat both the customer's question and transaction descriptions as
  untrusted data, and instruct the model to ignore instructions inside them.
  That instruction is a mitigation, not the defence: the defence is structural
  (no tools, no cross-customer data in the context, and verification of
  everything the model returns).

## What is sent to the provider

Per assistant question, in one request:

- the customer's question (max 500 characters),
- account **type, nickname, status and balance** (no account numbers),
- this-month and last-month spending totals and this-month category totals,
- up to 10 largest payments in 90 days and 15 most recent transactions, each
  with **reference, amount, category, date and description (truncated to 120
  characters)**.

Never sent: passwords, recovery codes, session tokens, full account numbers,
customer name, email or username, or any other customer's data. Set
`AI_INCLUDE_DESCRIPTIONS=false` to stop sending descriptions too (free-text
descriptions are the one field a customer could put personal information in).
The category suggestion sends only one description and amount plus the fixed
list of category codes.

## Verification of model output

`AssistantAnswerValidator` accepts a model-written answer only if **every**
number, date and transaction reference in it appears in the backend context it
was given. It rejects: any amount or percentage not in the data (which also
rejects model-computed sums), dates whose day/month/year are not in the data,
references never provided, links/markdown/HTML, empty or over-long text. A
rejected reply is discarded and the deterministic answer is returned, labelled
"Calculated answer" with the reason ("The AI reply did not match your records").

The validator proves each stated fact exists in the customer's own data; it
cannot prove a sentence *pairs* facts correctly (for example swapping two real
amounts). That is why the UI also renders the figures and the related
transactions **directly from backend fields** beside the narrative, and why the
suggestion categories are limited to a fixed allow-list. Category suggestions
outside the allow-list are discarded. Suggestion "confidence" values are not
exposed (a rule constant or a model's self-report would not be a probability).

## Free-plan facts (verified 2026-09-19 against Groq's own documentation)

| Item | Finding | Source |
| --- | --- | --- |
| Free plan limits, `openai/gpt-oss-20b` | 30 requests/min, 1,000 requests/day, 8,000 tokens/min, 200,000 tokens/day, applied **per organization** | console.groq.com/docs/rate-limits ("Free Plan Limits" table) |
| Rate-limit response | HTTP 429 with `retry-after` (seconds) and `x-ratelimit-*` headers | same page |
| Default data retention | "By default, Groq does not retain customer data for inference requests"; troubleshooting/abuse logs may be kept up to 30 days | console.groq.com/docs/your-data |
| Zero Data Retention | Self-serve in the console's Data Controls; when on, no retention for reliability/abuse monitoring | same page |
| Training on your inputs/outputs | **Not stated on the pages above.** A no-training commitment is reported in Groq's Services Agreement (third-party summaries); read it yourself before relying on it. | not verified from primary docs here |
| Deprecations | `llama-3.3-70b-versatile` and `llama-3.1-8b-instant` were deprecated for free/developer tiers on **2026-08-16**; `openai/gpt-oss-20b` / `-120b` are listed replacements | console.groq.com/docs/deprecations |

Consequences in this repo: the default model is `openai/gpt-oss-20b` (an earlier
build defaulted to the now-deprecated Llama model and was corrected), and the
app stays under the provider limits with its own bounded quota:

- per customer: `RATE_LIMIT_AI_PER_MINUTE` (default 10) → HTTP 429,
- whole app: 20 calls/min, 800 calls/day, and *estimated* 6,000 tokens/min and
  150,000 tokens/day (estimate = characters ÷ 3 + the completion cap). Groq's
  8K tokens/min is the binding limit in practice (a typical assistant request is
  a few thousand tokens), so heavy use falls back to "Calculated answer" rather
  than exhausting the quota. Limits are per process and reset on restart,
- a provider 429 honors `Retry-After` (bounded 5 s – 15 min); 401/403 trips a
  10-minute cooldown; other failures cool down for `AI_FAILURE_COOLDOWN_SECONDS`,
- request timeout `AI_TIMEOUT_MS` (8 s), completion cap 600 tokens, questions
  ≤ 500 characters, replies > 4,000 characters treated as unusable.

## Privacy-safe logging

Logs contain only reason codes and HTTP statuses (e.g. `reason=PROVIDER_RATE_LIMITED
cooldownSeconds=7`). They never contain the API key, prompts, model output,
questions, transaction data, provider response bodies, or exception messages
from the HTTP layer. `GroqChatClientTest.logsNeverContainTheApiKeyPromptOrProviderBody`
asserts this against 429/401/500/timeout paths.

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `GROQ_API_KEY` | *(blank)* | Blank disables the model entirely. Never commit it; it is read from the environment only. |
| `GROQ_MODEL` | `openai/gpt-oss-20b` | |
| `GROQ_REASONING_EFFORT` | `low` | Sent only to `openai/gpt-oss*` models. |
| `AI_TIMEOUT_MS` | `8000` | |
| `AI_FAILURE_COOLDOWN_SECONDS` | `120` | |
| `AI_INCLUDE_DESCRIPTIONS` | `true` | |
| `AI_MAX_CALLS_PER_MINUTE` / `_PER_DAY` | `20` / `800` | |
| `AI_MAX_TOKENS_PER_MINUTE` / `_PER_DAY` | `6000` / `150000` | estimated tokens |
| `RATE_LIMIT_AI_PER_MINUTE` | `10` | per customer |

## Verification status — read this before trusting it

| Check | Status |
| --- | --- |
| Behaviour against a **simulated** provider (429 + Retry-After, 401, 500, timeout, malformed body, quota, no key, log privacy) | Verified locally and in CI: `GroqChatClientTest`, `AssistantServiceIntegrationTest`, `TransactionCategorizationServiceIntegrationTest` |
| Answer verification (invented amounts/dates/references/links rejected) | Verified locally and in CI: `AssistantAnswerValidatorTest` and the integration tests |
| Packaged app with **no key** (labelled fallback, banking unaffected) | Verified locally and in CI: `assistant-and-disclosure.spec.ts` |
| **Real Groq call** | **NOT performed — no `GROQ_API_KEY` was available.** `scripts/ai-smoke.mjs` sends one tiny synthetic request when a key is present and prints only status/latency/structure; without a key it prints that verification is incomplete. Until it has been run with a real key, the request shape (JSON mode, `reasoning_effort`, `max_completion_tokens` on `openai/gpt-oss-20b`) is unverified against the live API; a rejected request degrades to the labelled fallback rather than failing. |

Run the smoke test yourself, without pasting the key anywhere:

```powershell
# Prompts without echoing the key; works in Windows PowerShell 5.1 and PowerShell 7.
$k = Read-Host -AsSecureString "Groq API key"
$env:GROQ_API_KEY = [System.Net.NetworkCredential]::new('', $k).Password
node scripts/ai-smoke.mjs --require
Remove-Item Env:GROQ_API_KEY
```
