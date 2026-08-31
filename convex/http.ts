import { httpRouter } from "convex/server";
import { httpAction } from "./_generated/server";
import { api } from "./_generated/api";

const http = httpRouter();

/**
 * JARVIS Backend — Convex HTTP Actions
 *
 * All API keys are stored as Convex environment variables (secrets).
 * The Android app calls these endpoints via HTTPS.
 *
 * Endpoints:
 *   POST /api/llm/chat        → Proxies to Gemini, xAI, OpenAI, Anthropic, etc.
 *   POST /api/tts/speak       → ElevenLabs TTS (returns audio bytes)
 *   GET  /api/tts/voices      → List available ElevenLabs voices
 *   POST /api/stt/transcribe  → Speech-to-text via ElevenLabs
 *   POST /api/preferences     → Save user voice/preferences
 *   GET  /api/preferences/:id → Get user preferences
 *   GET  /api/health          → Health check
 */

// ─── Health Check ────────────────────────────────────────────────────

http.route({
  path: "/api/health",
  method: "GET",
  handler: httpAction(async () => {
    return new Response(
      JSON.stringify({ status: "ok", backend: "convex", timestamp: Date.now() }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  }),
});

// ─── LLM Chat Proxy ─────────────────────────────────────────────────

http.route({
  path: "/api/llm/chat",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = await request.json();
      const { provider, model, systemPrompt, messages, tools, toolChoice } = body;

      switch (provider) {
        case "gemini":
          return await proxyGemini(ctx, model, systemPrompt, messages, tools);
        case "anthropic":
          return await proxyAnthropic(ctx, model, systemPrompt, messages);
        case "xai":
        case "openai":
        case "groq":
        case "cerebras":
        case "mistral":
        case "openrouter":
          return await proxyOpenAICompatible(ctx, provider, model, systemPrompt, messages, tools, toolChoice);
        default:
          return jsonResp({ error: `Unknown provider: ${provider}` }, 400);
      }
    } catch (err: any) {
      console.error("LLM proxy error:", err);
      return jsonResp({ error: err.message || "Internal error" }, 500);
    }
  }),
});

// ─── ElevenLabs TTS ─────────────────────────────────────────────────

http.route({
  path: "/api/tts/speak",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const apiKey = ctx.environmentVariable("ELEVENLABS_API_KEY");
      if (!apiKey) return jsonResp({ error: "ElevenLabs not configured" }, 503);

      const body = await request.json();
      const { text, voiceId, modelId, stability, similarityBoost, style } = body;

      if (!text || text.trim().length === 0) {
        return jsonResp({ error: "No text provided" }, 400);
      }

      const resolvedVoiceId = voiceId || "JBFqnCBsd6RMkjVDRZzb"; // George (British JARVIS)
      const resolvedModelId = modelId || "eleven_multilingual_v2";

      const resp = await fetch(
        `https://api.elevenlabs.io/v1/text-to-speech/${resolvedVoiceId}`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "xi-api-key": apiKey,
            "Accept": "audio/mpeg",
          },
          body: JSON.stringify({
            text,
            model_id: resolvedModelId,
            voice_settings: {
              stability: stability ?? 0.5,
              similarity_boost: similarityBoost ?? 0.75,
              style: style ?? 0,
            },
          }),
        }
      );

      if (!resp.ok) {
        const errText = await resp.text();
        return jsonResp(
          { error: `ElevenLabs TTS error (${resp.status}): ${errText.slice(0, 300)}` },
          resp.status
        );
      }

      // Stream audio back
      const audioBuffer = await resp.arrayBuffer();
      return new Response(audioBuffer, {
        status: 200,
        headers: {
          "Content-Type": "audio/mpeg",
          "Content-Disposition": 'attachment; filename="jarvis_speech.mp3"',
        },
      });
    } catch (err: any) {
      return jsonResp({ error: err.message || "TTS error" }, 500);
    }
  }),
});

// ─── ElevenLabs Voice List ──────────────────────────────────────────

http.route({
  path: "/api/tts/voices",
  method: "GET",
  handler: httpAction(async (ctx) => {
    try {
      const apiKey = ctx.environmentVariable("ELEVENLABS_API_KEY");
      if (!apiKey) return jsonResp({ error: "ElevenLabs not configured" }, 503);

      const resp = await fetch("https://api.elevenlabs.io/v1/voices", {
        headers: { "xi-api-key": apiKey },
      });

      const data = (await resp.json()) as any;

      if (!resp.ok) {
        return jsonResp(
          { error: `ElevenLabs error: ${JSON.stringify(data).slice(0, 300)}` },
          resp.status
        );
      }

      // Simplify for Android client
      const voices = (data.voices || []).map((v: any) => ({
        voiceId: v.voice_id,
        name: v.name,
        category: v.category,
        description: v.description,
        previewUrl: v.preview_url,
        labels: v.labels,
      }));

      return jsonResp({ voices });
    } catch (err: any) {
      return jsonResp({ error: err.message || "Voices error" }, 500);
    }
  }),
});

// ─── ElevenLabs STT ─────────────────────────────────────────────────

http.route({
  path: "/api/stt/transcribe",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const apiKey = ctx.environmentVariable("ELEVENLABS_API_KEY");
      if (!apiKey) return jsonResp({ error: "ElevenLabs not configured" }, 503);

      const formData = await request.formData();
      const audioFile = formData.get("audio") as File | null;

      if (!audioFile) {
        return jsonResp({ error: "No audio file provided" }, 400);
      }

      const proxyForm = new FormData();
      proxyForm.append("audio", audioFile, audioFile.name || "recording.wav");
      proxyForm.append("model_id", "scribe_v1");

      const resp = await fetch("https://api.elevenlabs.io/v1/speech-to-text", {
        method: "POST",
        headers: { "xi-api-key": apiKey },
        body: proxyForm,
      });

      const data = (await resp.json()) as any;

      if (!resp.ok) {
        return jsonResp(
          { error: `ElevenLabs STT error (${resp.status}): ${JSON.stringify(data).slice(0, 300)}` },
          resp.status
        );
      }

      return jsonResp({ text: data.text || "" });
    } catch (err: any) {
      return jsonResp({ error: err.message || "STT error" }, 500);
    }
  }),
});

// ─── User Preferences ───────────────────────────────────────────────

http.route({
  path: "/api/preferences",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = await request.json();
      const { userId, voiceId, voiceName, engineType, stability, similarityBoost, style, userName, personalityTone } = body;

      if (!userId) return jsonResp({ error: "userId required" }, 400);

      // Save or update voice preferences
      const existing = await ctx.runQuery(api.voicePreferences.getByUserId, { userId });
      if (existing) {
        await ctx.runMutation(api.voicePreferences.update, {
          id: existing._id,
          voiceId: voiceId || existing.voiceId,
          voiceName: voiceName || existing.voiceName,
          engineType: engineType || existing.engineType,
          stability: stability ?? existing.stability,
          similarityBoost: similarityBoost ?? existing.similarityBoost,
          style: style ?? existing.style,
        });
      } else {
        await ctx.runMutation(api.voicePreferences.insert, {
          userId,
          voiceId: voiceId || "JBFqnCBsd6RMkjVDRZzb",
          voiceName: voiceName || "George",
          engineType: engineType || "elevenlabs",
          stability: stability ?? 0.5,
          similarityBoost: similarityBoost ?? 0.75,
          style: style ?? 0,
        });
      }

      // Save user settings if provided
      if (userName || personalityTone) {
        const existingSettings = await ctx.runQuery(api.userSettings.getByUserId, { userId });
        if (existingSettings) {
          await ctx.runMutation(api.userSettings.update, {
            id: existingSettings._id,
            userName: userName || existingSettings.userName,
            personalityTone: personalityTone || existingSettings.personalityTone,
          });
        } else {
          await ctx.runMutation(api.userSettings.insert, {
            userId,
            userName: userName || "User",
            personalityTone: personalityTone || "jarvis_protocol",
            activeProvider: "gemini",
            activeModel: "gemini-2.5-flash",
          });
        }
      }

      return jsonResp({ success: true });
    } catch (err: any) {
      return jsonResp({ error: err.message || "Preferences error" }, 500);
    }
  }),
});

http.route({
  path: "/api/preferences",
  method: "GET",
  handler: httpAction(async (ctx, request) => {
    try {
      const url = new URL(request.url);
      const userId = url.searchParams.get("userId");

      if (!userId) return jsonResp({ error: "userId required" }, 400);

      const voicePrefs = await ctx.runQuery(api.voicePreferences.getByUserId, { userId });
      const userSettings = await ctx.runQuery(api.userSettings.getByUserId, { userId });

      return jsonResp({
        voice: voicePrefs || null,
        settings: userSettings || null,
      });
    } catch (err: any) {
      return jsonResp({ error: err.message || "Preferences error" }, 500);
    }
  }),
});

// ─── Provider-Specific Proxy Functions ───────────────────────────────

async function proxyGemini(
  ctx: any,
  model: string | undefined,
  systemPrompt: string,
  messages: Array<{ role: string; content: string }>,
  tools: any[] | undefined
): Promise<Response> {
  const apiKey = ctx.environmentVariable("GEMINI_API_KEY");
  if (!apiKey) return jsonResp({ error: "Gemini not configured" }, 503);

  const geminiModel = model || "gemini-2.5-flash";
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

  const data = (await resp.json()) as any;

  if (!resp.ok) {
    return jsonResp(
      { error: `Gemini error (${resp.status}): ${JSON.stringify(data).slice(0, 500)}` },
      resp.status
    );
  }

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

  return jsonResp({ message: text.trim() || null, toolCalls });
}

async function proxyOpenAICompatible(
  ctx: any,
  provider: string,
  model: string | undefined,
  systemPrompt: string,
  messages: Array<{ role: string; content: string }>,
  tools: any[] | undefined,
  toolChoice: string | undefined
): Promise<Response> {
  const endpoint = getProviderEndpoint(provider);
  const apiKey = getProviderKey(ctx, provider);
  if (!apiKey) return jsonResp({ error: `${provider} not configured` }, 503);

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

  if (provider === "openrouter") {
    headers["HTTP-Referer"] = "https://github.com/macaulay-spec/Mirror-";
    headers["X-Title"] = "JARVIS";
  }

  const resp = await fetch(endpoint, {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
  });

  const data = (await resp.json()) as any;

  if (!resp.ok) {
    return jsonResp(
      { error: `${provider} error (${resp.status}): ${JSON.stringify(data).slice(0, 500)}` },
      resp.status
    );
  }

  const choice = data.choices?.[0];
  const message = choice?.message;
  const text = message?.content?.trim() || null;
  const toolCalls = (message?.tool_calls || []).map((tc: any) => ({
    toolName: tc.function?.name,
    arguments: JSON.parse(tc.function?.arguments || "{}"),
  }));

  return jsonResp({ message: text, toolCalls });
}

async function proxyAnthropic(
  ctx: any,
  model: string | undefined,
  systemPrompt: string,
  messages: Array<{ role: string; content: string }>
): Promise<Response> {
  const apiKey = ctx.environmentVariable("ANTHROPIC_API_KEY");
  if (!apiKey) return jsonResp({ error: "Anthropic not configured" }, 503);

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

  const data = (await resp.json()) as any;

  if (!resp.ok) {
    return jsonResp(
      { error: `Anthropic error (${resp.status}): ${JSON.stringify(data).slice(0, 500)}` },
      resp.status
    );
  }

  const text = data.content?.[0]?.text?.trim() || null;
  return jsonResp({ message: text, toolCalls: [] });
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

function getProviderKey(ctx: any, provider: string): string | undefined {
  const keyMap: Record<string, string> = {
    xai: ctx.environmentVariable("XAI_API_KEY"),
    openai: ctx.environmentVariable("OPENAI_API_KEY"),
    groq: ctx.environmentVariable("GROQ_API_KEY"),
    cerebras: ctx.environmentVariable("CEREBRAS_API_KEY"),
    openrouter: ctx.environmentVariable("OPENROUTER_API_KEY"),
    mistral: ctx.environmentVariable("MISTRAL_API_KEY"),
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
    gemini: "gemini-2.5-flash",
    anthropic: "claude-sonnet-4-20250514",
  };
  return models[provider] || "gpt-4o";
}

function jsonResp(data: any, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
    },
  });
}

export default http;
