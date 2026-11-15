import http from 'node:http';

export const SUPPORTED_GEMINI_MODELS = [
  'gemini-3.5-flash-lite',
  'gemini-3-flash-preview',
  'gemini-3.8-flash'
];

const DEFAULT_GENERATION_SCHEMA = {
  type: 'OBJECT',
  required: [
    'replyText',
    'problemTitle',
    'matchedProblemId',
    'confidenceLevel',
    'confirmedEvidence',
    'possibleCauses',
    'diagnosticQuestions',
    'unverifiedAssumptions',
    'recommendedActionIds',
    'hardwareFaultIndicators',
    'escalationInstructions',
    'verificationMethod'
  ],
  properties: {
    replyText: { type: 'STRING' },
    problemTitle: { type: 'STRING' },
    matchedProblemId: { type: 'STRING' },
    confidenceLevel: { type: 'STRING' },
    confirmedEvidence: { type: 'ARRAY', items: { type: 'STRING' } },
    possibleCauses: { type: 'ARRAY', items: { type: 'STRING' } },
    diagnosticQuestions: { type: 'ARRAY', items: { type: 'STRING' } },
    unverifiedAssumptions: { type: 'ARRAY', items: { type: 'STRING' } },
    recommendedActionIds: { type: 'ARRAY', items: { type: 'STRING' } },
    hardwareFaultIndicators: { type: 'ARRAY', items: { type: 'STRING' } },
    escalationInstructions: { type: 'STRING' },
    verificationMethod: { type: 'STRING' }
  }
};

export function stripMarkdownJsonFences(raw) {
  const trimmed = String(raw || '').trim();
  if (!trimmed.startsWith('```')) return trimmed;
  const firstNewline = trimmed.indexOf('\n');
  const body = firstNewline !== -1 ? trimmed.slice(firstNewline + 1) : trimmed.slice(3);
  return body.replace(/```$/m, '').trim();
}

export function buildLanguageInstruction(language) {
  if (language === 'Hindi') {
    return (
      'Target Response Language: Hindi (100% Pure Devanagari Hindi / शुद्ध देवनागरी हिंदी).\n' +
      '- Write EVERY word of replyText, problemTitle, confirmedEvidence, possibleCauses, diagnosticQuestions, ' +
      'unverifiedAssumptions, hardwareFaultIndicators, escalationInstructions, and verificationMethod in pure Devanagari Hindi.\n' +
      '- Do NOT mix English sentences or Hinglish. Only keep essential short technical nouns (e.g., Wi-Fi, RAM, Bluetooth, GPS, USB) transliterated or cleanly explained in Hindi.'
    );
  }
  if (language === 'Hinglish') {
    return (
      'Target Response Language: Hinglish (100% Natural Conversational Hinglish in Latin script).\n' +
      '- Write ALL JSON string fields (replyText, problemTitle, confirmedEvidence, possibleCauses, diagnosticQuestions, ' +
      'unverifiedAssumptions, hardwareFaultIndicators, escalationInstructions, verificationMethod) consistently in natural Hinglish.\n' +
      '- Do NOT switch mid-response into pure formal English or Devanagari script.'
    );
  }
  return (
    'Target Response Language: English (100% Professional Clear English).\n' +
    '- Write ALL JSON string fields strictly in clear, practical English with zero Hindi/Hinglish mixing.'
  );
}

export function buildDiagnosisPrompt(body) {
  const {
    userProblem = '',
    vitalsSummary = '',
    ragContext = '',
    validActionCatalog = '',
    oemContext = '',
    history = [],
    language = 'English'
  } = body || {};

  const historyBlock = Array.isArray(history) && history.length > 0
    ? 'RECENT CONVERSATION:\n' +
      history.slice(-4).map(h => `${h.role === 'user' ? 'User' : 'Doctor'}: ${h.text || ''}`).join('\n') +
      '\n\n'
    : '';

  return [
    'You are AI Smartphone Doctor, an evidence-based Android hardware & software diagnostic expert.',
    'CORE RULES:',
    '1. Telemetry Honesty: NEVER invent or hallucinate sensor numbers. Treat readings marked [NOT COLLECTED] or Unavailable as uncollected; place missing/sandboxed items inside unverifiedAssumptions, NEVER inside confirmedEvidence.',
    '2. Evidence vs Hypothesis: Strictly separate Confirmed Telemetry Evidence (from verified collectors below) from Possible Causes.',
    '3. Clarify When Vague: If the complaint is too broad (or marked AMBIGUOUS COMPLAINT NOTICE), set matchedProblemId to "CLARIFY-REQUIRED", confidenceLevel to "Low", and ask concise diagnosticQuestions.',
    '4. Autonomous Execution First: Populate recommendedActionIds ONLY from the Approved Action Catalog IDs below. When an AUTO IN-APP action handles a check/fix, instruct the user to authorize the in-app Autonomous Repair Plan instead of manually following steps. Never pretend an instruction or Settings link repaired a bug.',
    `5. ${buildLanguageInstruction(language)}`,
    oemContext ? `OEM & COMPATIBILITY:\n${oemContext}` : '',
    ragContext ? `RETRIEVED KNOWLEDGE:\n${ragContext}` : '',
    validActionCatalog ? `APPROVED ACTION CATALOG:\n${validActionCatalog}` : '',
    historyBlock + `VERIFIED DEVICE TELEMETRY:\n${vitalsSummary}`,
    `USER PROBLEM:\n${userProblem}`
  ].filter(Boolean).join('\n\n');
}

export async function callGeminiModels({
  apiKey,
  prompt,
  screenshotBase64 = null,
  models = SUPPORTED_GEMINI_MODELS,
  fetchImpl = globalThis.fetch
}) {
  if (!apiKey || apiKey === 'YOUR_SERVER_SIDE_GEMINI_API_KEY' || apiKey === 'YOUR_GEMINI_API_KEY') {
    const err = new Error('Server GEMINI_API_KEY is not configured');
    err.statusCode = 503;
    throw err;
  }

  const parts = [{ text: prompt }];
  if (screenshotBase64) {
    parts.push({
      inline_data: {
        mime_type: 'image/jpeg',
        data: screenshotBase64
      }
    });
  }

  const payload = {
    contents: [{ role: 'user', parts }],
    generationConfig: {
      responseMimeType: 'application/json',
      responseSchema: DEFAULT_GENERATION_SCHEMA,
      temperature: 0.3
    }
  };

  let lastStatus = 502;
  let lastError = 'Unable to reach Gemini generateContent endpoint';

  for (const model of models) {
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
    let res;
    let rawText = '';
    try {
      res = await fetchImpl(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': apiKey
        },
        signal: typeof AbortSignal !== 'undefined' && AbortSignal.timeout ? AbortSignal.timeout(18000) : undefined,
        body: JSON.stringify(payload)
      });
      rawText = await res.text();
    } catch (fetchErr) {
      lastStatus = 504;
      lastError = `${model}: ${fetchErr?.message || 'Request timeout or network error'}`;
      continue;
    }

    if (res.ok && rawText) {
      const root = JSON.parse(rawText);
      const modelText = root?.candidates?.[0]?.content?.parts?.[0]?.text || '';
      if (modelText.trim()) {
        const clean = stripMarkdownJsonFences(modelText);
        const parsed = JSON.parse(clean);
        return {
          modelUsed: model,
          httpStatus: res.status,
          data: parsed,
          rawJson: clean,
          usageMetadata: root?.usageMetadata || null
        };
      }
      lastStatus = 502;
      lastError = `Empty response from model ${model}`;
      continue;
    }

    let apiMessage = `HTTP ${res.status}`;
    try {
      const parsedErr = JSON.parse(rawText);
      if (parsedErr?.error?.message) {
        apiMessage = parsedErr.error.message;
      }
    } catch {
      if (rawText) apiMessage = `HTTP ${res.status}: ${rawText.slice(0, 200)}`;
    }
    lastStatus = res.status;
    lastError = `${model}: ${apiMessage}`;

    // Fail fast on client / auth errors; only retry alternate models on 404/429/5xx
    if (res.status === 400 || res.status === 401 || res.status === 403) {
      const authErr = new Error(lastError);
      authErr.statusCode = res.status;
      throw authErr;
    }
  }

  const finalErr = new Error(lastError);
  finalErr.statusCode = lastStatus;
  throw finalErr;
}

export function createProxyServer({
  apiKeyProvider = () => process.env.GEMINI_API_KEY || '',
  clientTokenProvider = () => process.env.AI_PROXY_CLIENT_TOKEN || '',
  fetchImpl = globalThis.fetch,
  models = getConfiguredModels()
} = {}) {
  return http.createServer(async (req, res) => {
    const sendJson = (statusCode, bodyObj) => {
      const body = JSON.stringify(bodyObj);
      res.writeHead(statusCode, {
        'Content-Type': 'application/json; charset=utf-8',
        'Content-Length': Buffer.byteLength(body)
      });
      res.end(body);
    };

    try {
      if (req.method === 'GET' && req.url === '/health') {
        const hasKey = Boolean(apiKeyProvider()?.trim()) &&
          apiKeyProvider().trim() !== 'YOUR_SERVER_SIDE_GEMINI_API_KEY';
        return sendJson(200, {
          status: 'ok',
          geminiConfigured: hasKey,
          models
        });
      }

      if (req.method === 'POST' && req.url === '/v1/diagnose') {
        const expectedToken = clientTokenProvider()?.trim();
        if (expectedToken && req.headers['x-app-client-token'] !== expectedToken) {
          return sendJson(401, { error: { message: 'Unauthorized client token' } });
        }

        const chunks = [];
        for await (const chunk of req) {
          chunks.push(chunk);
        }
        const rawBody = Buffer.concat(chunks).toString('utf8');
        let parsedBody;
        try {
          parsedBody = rawBody ? JSON.parse(rawBody) : {};
        } catch {
          return sendJson(400, { error: { message: 'Invalid JSON request body' } });
        }

        if (!parsedBody.userProblem || !String(parsedBody.userProblem).trim()) {
          return sendJson(400, { error: { message: 'userProblem is required' } });
        }

        const prompt = parsedBody.rawPrompt && String(parsedBody.rawPrompt).trim()
          ? String(parsedBody.rawPrompt)
          : buildDiagnosisPrompt(parsedBody);

        const result = await callGeminiModels({
          apiKey: apiKeyProvider()?.trim(),
          prompt,
          screenshotBase64: parsedBody.screenshotBase64 || null,
          models,
          fetchImpl
        });

        return sendJson(200, {
          status: 'ONLINE_GEMINI',
          modelUsed: result.modelUsed,
          data: result.data,
          rawJson: result.rawJson,
          usageMetadata: result.usageMetadata
        });
      }

      return sendJson(404, { error: { message: 'Route not found' } });
    } catch (err) {
      const code = err?.statusCode && Number.isInteger(err.statusCode) ? err.statusCode : 500;
      return sendJson(code, {
        error: {
          code,
          message: err?.message || 'Internal proxy error'
        }
      });
    }
  });
}

function getConfiguredModels() {
  const primary = (process.env.PRIMARY_GEMINI_MODEL || '').trim();
  if (primary && SUPPORTED_GEMINI_MODELS.includes(primary)) {
    return [primary, ...SUPPORTED_GEMINI_MODELS.filter(m => m !== primary)];
  }
  return SUPPORTED_GEMINI_MODELS;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number(process.env.PORT || 8080);
  const server = createProxyServer();
  server.listen(port, () => {
    console.log(`[ai-smartphone-doctor-proxy] listening on port ${port} with models: ${getConfiguredModels().join(', ')}`);
  });
}
