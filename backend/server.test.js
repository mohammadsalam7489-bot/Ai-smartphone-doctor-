import test from 'node:test';
import assert from 'node:assert/strict';
import {
  SUPPORTED_GEMINI_MODELS,
  buildDiagnosisPrompt,
  callGeminiModels,
  createProxyServer
} from './server.js';

test('model catalog uses only Gemini 3.x models and excludes retired models', () => {
  assert.ok(SUPPORTED_GEMINI_MODELS.includes('gemini-3.8-flash'));
  assert.ok(SUPPORTED_GEMINI_MODELS.includes('gemini-3.5-flash-lite'));
  assert.ok(SUPPORTED_GEMINI_MODELS.includes('gemini-3-flash-preview'));
  for (const retired of ['gemini-1.5-flash', 'gemini-2.0-flash', 'gemini-2.5-flash']) {
    assert.equal(SUPPORTED_GEMINI_MODELS.includes(retired), false);
  }
});

test('proxy falls back cleanly from 503 on gemini-3.5-flash-lite to gemini-3-flash-preview', async () => {
  const attemptedModels = [];
  const fakeFetch = async (url, init) => {
    attemptedModels.push(url);
    assert.equal(init.headers['x-goog-api-key'], 'test-server-secret');
    if (url.includes('gemini-3.5-flash-lite')) {
      return new Response(
        JSON.stringify({ error: { code: 503, message: 'High demand' } }),
        { status: 503 }
      );
    }
    return new Response(
      JSON.stringify({
        candidates: [
          {
            content: {
              parts: [
                {
                  text: JSON.stringify({
                    replyText: 'आपका फोन अधिक गर्म हो रहा है।',
                    problemTitle: 'फोन अधिक गर्म होना',
                    matchedProblemId: 'AND-27-001',
                    confidenceLevel: 'High',
                    confirmedEvidence: ['बैटरी तापमान: 43.2°C'],
                    possibleCauses: ['बैकग्राउंड प्रोसेस लोड'],
                    diagnosticQuestions: ['क्या यह चार्जिंग के समय होता है?'],
                    unverifiedAssumptions: ['तीसरे पक्ष के ऐप्स सैंडबॉक्स में हैं'],
                    recommendedActionIds: ['auto_audit_thermal_power'],
                    hardwareFaultIndicators: ['लगातार 48°C से ऊपर'],
                    escalationInstructions: 'अधिकृत सर्विस केंद्र से जांच कराएं।',
                    verificationMethod: 'बैटरी तापमान 38°C से नीचे आने की जांच करें।'
                  })
                }
              ]
            }
          }
        ]
      }),
      { status: 200 }
    );
  };

  const result = await callGeminiModels({
    apiKey: 'test-server-secret',
    prompt: buildDiagnosisPrompt({ userProblem: 'मेरा फोन गर्म हो रहा है', language: 'Hindi' }),
    fetchImpl: fakeFetch
  });

  assert.equal(attemptedModels.length, 2);
  assert.equal(result.modelUsed, 'gemini-3-flash-preview');
  assert.equal(result.data.matchedProblemId, 'AND-27-001');
  assert.ok(result.data.replyText.includes('गर्म'));
});

test('proxy HTTP endpoint surfaces exact auth errors and keeps secrets out of /health', async () => {
  const server = createProxyServer({
    apiKeyProvider: () => 'test-secret',
    fetchImpl: async () => new Response(
      JSON.stringify({ error: { code: 403, message: 'API key invalid' } }),
      { status: 403 }
    )
  });

  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  try {
    const healthRes = await fetch(`http://127.0.0.1:${port}/health`);
    assert.equal(healthRes.status, 200);
    const healthJson = await healthRes.json();
    assert.equal(healthJson.status, 'ok');
    assert.equal(healthJson.geminiConfigured, true);
    assert.equal(JSON.stringify(healthJson).includes('test-secret'), false);

    const errRes = await fetch(`http://127.0.0.1:${port}/v1/diagnose`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userProblem: 'Phone hanging', language: 'English' })
    });
    assert.equal(errRes.status, 403);
    const errJson = await errRes.json();
    assert.ok(errJson.error.message.includes('API key invalid'));
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});
