/**
 * JARVIS Backend Proxy — Cloudflare Worker
 *
 * Holds all API keys as encrypted secrets. The Android app never sees them.
 *
 * Endpoints:
 *   POST /api/llm/chat        → Proxies to Gemini, xAI, OpenAI, Anthropic, etc.
 *   POST /api/tts/speak       → ElevenLabs TTS (returns audio)
 *   GET  /api/tts/voices      → List available ElevenLabs voices
 *   POST /api/stt/transcribe  → Speech-to-text (ElevenLabs or Whisper)
 *   GET  /api/health          → Health check
 *
 * Deploy:
 *   npx wrangler secret put GEMINI_API_KEY
 *   npx wrangler secret put XAI_API_KEY
 *   npx wrangler secret put ELEVENLABS_API_KEY
 *   npx wrangler deploy
 */

interface Env {
  GEMINI_API_KEY: string;
  XAI_API_KEY: string;
  OPENAI_API_KEY: string;
  ANTHROPIC_API_KEY: string;
  ELEVENLABS_API_KEY: string;
  // Optional: restrict to specific app fingerprints
  ALLOWED_APP_IDS?: string;
}

interface LLMRequest {
  provider: string;        // "gemini" | "xai" | "openai" | "anthropic" | "groq"
  model?: string;          // optional model override
  systemPrompt: string;
  messages: Array<{ role: string; content: string }>;
  tools?: any[];           // function calling schemas
  toolChoice?: string;
}

interface TTSRequest {
  text: string;
  voiceId?: string;        // ElevenLabs voice ID (default: "Rachel" = 21m00Tcm4TlvDq8ikWAM)
  modelId?: string;        // default: "eleven_multilingual_v2"
  stability?: number;      // 0-1, default 0.5
  similarityBoost?: number; // 0-1, default 0.75
  style?: number;          // 0-1, default 0
}

interface STTRequest {
  // Audio sent as multipart form data
  language?: string;
  modelId?: string;        // default: "eleven_multilingual_v2"
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    // CORS headers for the Android app
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization, X-App-Id",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    try {
      // Route to the correct handler
      if (path === "/api/health") {
        return jsonResponse({ status: "ok", timestamp: Date.now() }, corsHeaders);
      }

      if (path === "/api/llm/chat" && request.method === "POST") {
        return await handleLLMChat(request, env, corsHeaders);
      }

      if (path === "/api/tts/speak" && request.method === "POST") {
        return await handleTTS(request, env, corsHeaders);
      }

      if (path === "/api/tts/voices" && request.method === "GET") {
        return await handleVoices(env, corsHeaders);
      }

      if (path === "/api/stt/transcribe" && request.method === "POST") {
        return await handleSTT(request, env, corsHeaders);
      }

      return jsonResponse({ error: "Not found" }, corsHeaders, 404);
    } catch (err: any) {
      console.error("Worker error:", err);
      return jsonResponse(
        { error: err.message || "Internal error" },
        corsHeaders,
        500
      );
    }
  },
};

// ─── LLM Chat Proxy ─────────────────────────────────────────────────

async function handleLLMChat(
  request: Request,
  env: Env,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const body: LLMRequest = await request.json();
  const { provider, model, systemPrompt, messages, tools, toolChoice } = body;

  switch (provider) {
    case "gemini":
      return proxyGemini(env, model, systemPrompt, messages, tools, corsHeaders);
    case "anthropic":
      return proxyAnthropic(env, model, systemPrompt, messages, corsHeaders);
    case "xai":
    case "openai":
    case "groq":
    case "cerebras":
    case "mistral":
    case "openrouter":
      return proxyOpenAICompatible(env, provider, model, systemPrompt, messages, tools, toolChoice, corsHeaders);
    default:
      return jsonResponse({ error: `Unknown provider: ${provider}` }, corsHeaders, 400);
  }
}

// ─── Gemini ──────────────────────────────────────────────────────────

async function proxyGemini(
  env: Env,
  model: string | undefined,
  systemPrompt: string,
  messages: Array<{ role: string; content: string }>,
  tools: any[] | undefined,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const apiKey = env.GEMINI_API_KEY;
  if (!apiKey) return jsonResponse({ error: "Gemini not configured" }, corsHeaders, 503);

  const geminiModel = model || "gemini-2.0-flash";
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${geminiModel}:generateContent?key=${apiKey}`;

  const contents = messages.map((m) => ({
    role: m.role === "assistant" || m.role === "model" ? "model" : "user",
    parts: [{ text: m.content }],
  }));

  const payload: any = {
    system_instruction: { parts: [{ text: systemPrompt }] },
    contents,
  };

  if (tools && tools.length > 0) {
    payload.tools = tools;
  }

  const resp = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  const data = await resp.json() as any;

  if (!resp.ok) {
    return jsonResponse(
      { error: `Gemini error (${resp.status}): ${JSON.stringify(data).slice(0, 500)}` },
      corsHeaders,
      resp.status
    );
  }

  // Parse Gemini response into a standard format
  const candidate = data.candidates?.[0];
  const parts = candidate?.content?.parts || [];
  let text = "";
  const toolCalls: any[] = [];

  for (const part of parts) {
    if (part.text) text += part.text;
    if (part.functionCall) {
      toolCalls.push({
        toolName: part.functionCall.name,
        arguments: part.functionCall.args || {},
      });
    }
  }

  return jsonResponse(
    { message: text.trim() || null, toolCalls },
    corsHeaders
  );
}

// ─── OpenAI-Compatible (xAI, OpenAI, Groq, etc.) ───────────────────

async function proxyOpenAICompatible(
  env: Env,
  provider: string,
  model: string | undefined,
  systemPrompt: string,
  messages: Array<{ role: string; content: string }>,
  tools: any[] | undefined,
  toolChoice: string | undefined,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const endpoint = getProviderEndpoint(provider);
  const apiKey = getProviderKey(env, provider);
  if (!apiKey) return jsonResponse({ error: `${provider} not configured` }, corsHeaders, 503);

  const resolvedModel = model || getDefaultModel(provider);

  const payload: any = {
    model: resolvedModel,
    messages: [
      { role: "system", content: systemPrompt },
      ...messages.map((m) => ({
        role: m.role === "assistant" || m.role === "model" ? "assistant" : "user",
        content: m.content,
      })),
    ],
  };

  if (tools && tools.length > 0) {
    payload.tools = tools;
    payload.tool_choice = toolChoice || "auto";
  }

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${apiKey}`,
  };

  // OpenRouter wants extra headers
  if (provider === "openrouter") {
    headers["HTTP-Referer"] = "https://github.com/macaulay-spec/Mirror-";
    headers["X-Title"] = "JARVIS";
  }

  const resp = await fetch(endpoint, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
  });

  const data = await resp.json() as any;

  if (!resp.ok) {
    return jsonResponse(
      { error: `${provider} error (${resp.status}): ${JSON.stringify(data).slice(0, 500)}` },
      corsHeaders,
      resp.status
    );
  }

  // Parse into standard format
  const choice = data.choices?.[0];
  const message = choice?.message;
  const text = message?.content?.trim() || null;
  const toolCalls = (message?.tool_calls || []).map((tc: any) => ({
    toolName: tc.function?.name,
    arguments: JSON.parse(tc.function?.arguments || "{}"),
  }));

  return jsonResponse({ message: text, toolCalls }, corsHeaders);
}

// ─── Anthropic Claude ────────────────────────────────────────────────

async function proxyAnthropic(
  env: Env,
  model: string | undefined,
  systemPrompt: string,
  messages: Array<{ role: string; content: string }>,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const apiKey = env.ANTHROPIC_API_KEY;
  if (!apiKey) return jsonResponse({ error: "Anthropic not configured" }, corsHeaders, 503);

  const resolvedModel = model || "claude-sonnet-4-20250514";

  const payload: any = {
    model: resolvedModel,
    max_tokens: 4096,
    system: systemPrompt,
    messages: messages.map((m) => ({
      role: m.role === "assistant" ? "assistant" : "user",
      content: m.content,
    })),
  };

  const resp = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-api-key": apiKey,
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify(payload),
  });

  const data = await resp.json() as any;

  if (!resp.ok) {
    return jsonResponse(
      { error: `Anthropic error (${resp.status}): ${JSON.stringify(data).slice(0, 500)}` },
      corsHeaders,
      resp.status
    );
  }

  const text = data.content?.[0]?.text?.trim() || null;
  return jsonResponse({ message: text, toolCalls: [] }, corsHeaders);
}

// ─── ElevenLabs TTS ─────────────────────────────────────────────────

async function handleTTS(
  request: Request,
  env: Env,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const apiKey = env.ELEVENLABS_API_KEY;
  if (!apiKey) return jsonResponse({ error: "ElevenLabs not configured" }, corsHeaders, 503);

  const body: TTSRequest = await request.json();
  const voiceId = body.voiceId || "21m00Tcm4TlvDq8ikWAM"; // Rachel (default)
  const modelId = body.modelId || "eleven_multilingual_v2";

  if (!body.text || body.text.trim().length === 0) {
    return jsonResponse({ error: "No text provided" }, corsHeaders, 400);
  }

  const url = `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}`;

  const payload: any = {
    text: body.text,
    model_id: modelId,
    voice_settings: {
      stability: body.stability ?? 0.5,
      similarity_boost: body.similarityBoost ?? 0.75,
      style: body.style ?? 0,
    },
  };

  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "xi-api-key": apiKey,
      "Accept": "audio/mpeg",
    },
    body: JSON.stringify(payload),
  });

  if (!resp.ok) {
    const errText = await resp.text();
    return jsonResponse(
      { error: `ElevenLabs TTS error (${resp.status}): ${errText.slice(0, 300)}` },
      corsHeaders,
      resp.status
    );
  }

  // Stream the audio back to the client
  const audioBody = resp.body;
  return new Response(audioBody, {
    headers: {
      ...corsHeaders,
      "Content-Type": "audio/mpeg",
      "Content-Disposition": 'attachment; filename="jarvis_speech.mp3"',
    },
  });
}

// ─── ElevenLabs Voice List ───────────────────────────────────────────

async function handleVoices(
  env: Env,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const apiKey = env.ELEVENLABS_API_KEY;
  if (!apiKey) return jsonResponse({ error: "ElevenLabs not configured" }, corsHeaders, 503);

  const resp = await fetch("https://api.elevenlabs.io/v1/voices", {
    headers: { "xi-api-key": apiKey },
  });

  const data = await resp.json() as any;

  if (!resp.ok) {
    return jsonResponse({ error: `ElevenLabs error: ${JSON.stringify(data).slice(0, 300)}` }, corsHeaders, resp.status);
  }

  // Simplify the voice list for the Android client
  const voices = (data.voices || []).map((v: any) => ({
    voiceId: v.voice_id,
    name: v.name,
    category: v.category,       // "premade", "cloned", "generated"
    description: v.description,
    previewUrl: v.preview_url,
    labels: v.labels,           // { accent, gender, age, use_case }
  }));

  return jsonResponse({ voices }, corsHeaders);
}

// ─── ElevenLabs STT ─────────────────────────────────────────────────

async function handleSTT(
  request: Request,
  env: Env,
  corsHeaders: Record<string, string>
): Promise<Response> {
  const apiKey = env.ELEVENLABS_API_KEY;
  if (!apiKey) return jsonResponse({ error: "ElevenLabs not configured" }, corsHeaders, 503);

  // The Android app sends multipart form data with the audio file
  const formData = await request.formData();
  const audioFile = formData.get("audio") as File | null;

  if (!audioFile) {
    return jsonResponse({ error: "No audio file provided" }, corsHeaders, 400);
  }

  const language = formData.get("language") as string || "en";

  const proxyForm = new FormData();
  proxyForm.append("audio", audioFile, audioFile.name || "recording.wav");
  proxyForm.append("model_id", "scribe_v1");

  const resp = await fetch("https://api.elevenlabs.io/v1/speech-to-text", {
    method: "POST",
    headers: { "xi-api-key": apiKey },
    body: proxyForm,
  });

  const data = await resp.json() as any;

  if (!resp.ok) {
    return jsonResponse(
      { error: `ElevenLabs STT error (${resp.status}): ${JSON.stringify(data).slice(0, 300)}` },
      corsHeaders,
      resp.status
    );
  }

  return jsonResponse({ text: data.text || "" }, corsHeaders);
}

// ─── Helpers ─────────────────────────────────────────────────────────

function getProviderEndpoint(provider: string): string {
  const endpoints: Record<string, string> = {
    xai: "https://api.x.ai/v1/chat/completions",
    openai: "https://api.openai.com/v1/chat/completions",
    groq: "https://api.groq.com/openai/v1/chat/completions",
    cerebras: "https://api.cerebras.ai/v1/chat/completions",
    openrouter: "https://openrouter.ai/api/v1/chat/completions",
    mistral: "https://api.mistral.ai/v1/chat/completions",
  };
  return endpoints[provider] || endpoints.openai;
}

function getProviderKey(env: Env, provider: string): string | undefined {
  const keyMap: Record<string, string> = {
    xai: env.XAI_API_KEY,
    openai: env.OPENAI_API_KEY,
    groq: env.OPENAI_API_KEY,       // Groq uses OpenAI-compatible format
    cerebras: env.OPENAI_API_KEY,   // Same
    openrouter: env.OPENAI_API_KEY, // Same
    mistral: env.OPENAI_API_KEY,    // Same
  };
  return keyMap[provider];
}

function getDefaultModel(provider: string): string {
  const models: Record<string, string> = {
    xai: "grok-3",
    openai: "gpt-4o",
    groq: "llama-3.3-70b-versatile",
    cerebras: "llama-3.3-70b",
    openrouter: "anthropic/claude-3.5-sonnet",
    mistral: "mistral-large-latest",
    gemini: "gemini-2.0-flash",
    anthropic: "claude-sonnet-4-20250514",
  };
  return models[provider] || "gpt-4o";
}

function jsonResponse(data: any, headers: Record<string, string>, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...headers,
      "Content-Type": "application/json",
    },
  });
}
