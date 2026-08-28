const express = require('express');
const cors = require('cors');
const fetch = require('node-fetch');
const cheerio = require('cheerio');
require('dotenv').config();

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;

const PROVIDERS = {
    gemini: {
        url: "https://generativelanguage.googleapis.com/v1beta/models/",
        keys: (process.env.GEMINI_KEYS || "").split(",").filter(Boolean)
    },
    openai: {
        url: "https://api.openai.com/v1/chat/completions",
        keys: (process.env.OPENAI_KEYS || "").split(",").filter(Boolean)
    },
    anthropic: {
        url: "https://api.anthropic.com/v1/messages",
        keys: (process.env.ANTHROPIC_KEYS || "").split(",").filter(Boolean)
    }
};

app.get('/health', (req, res) => res.json({ status: "ok" }));
app.get('/api/v1/providers/health', (req, res) => {
    const health = {};
    for (const p in PROVIDERS) {
        health[p] = PROVIDERS[p].keys.length > 0 ? "healthy" : "offline";
    }
    res.json({ success: true, providers: health });
});

app.post('/api/v1/ai/chat', async (req, res) => {
    try {
        const { provider = 'gemini', model = 'gemini-2.5-flash', messages, system } = req.body;
        
        const provConfig = PROVIDERS[provider];
        if (!provConfig || provConfig.keys.length === 0) {
            return res.status(500).json({ success: false, error: "Provider not configured" });
        }
        
        const apiKey = provConfig.keys[0];
        
        if (provider === 'gemini') {
            const contents = messages.map(m => ({
                role: m.role === 'assistant' ? 'model' : 'user',
                parts: [{ text: m.content }]
            }));
            
            const payload = {
                contents,
                systemInstruction: system ? { parts: [{ text: system }] } : undefined
            };
            
            const url = `${provConfig.url}${model}:generateContent?key=${apiKey}`;
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            
            const data = await response.json();
            if (!response.ok) return res.status(response.status).json({ success: false, error: data });
            
            const reply = data.candidates?.[0]?.content?.parts?.[0]?.text || "";
            return res.json({ success: true, provider, model, message: reply });
        } else if (provider === 'openai') {
            const msgs = system ? [{ role: 'system', content: system }, ...messages] : messages;
            const response = await fetch(provConfig.url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${apiKey}` },
                body: JSON.stringify({ model, messages: msgs })
            });
            const data = await response.json();
            if (!response.ok) return res.status(response.status).json({ success: false, error: data });
            return res.json({ success: true, provider, model, message: data.choices[0].message.content });
        }
        
        return res.status(400).json({ success: false, error: "Unsupported provider" });
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

app.post('/api/v1/voice/tts', async (req, res) => {
    try {
        const { text, voiceId = '21m00Tcm4TlvDq8ikWAM' } = req.body;
        const apiKey = process.env.ELEVENLABS_API_KEY;
        if (!apiKey) return res.status(500).json({ success: false, error: 'ElevenLabs key not configured' });
        
        const url = `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?output_format=mp3_44100_128`;
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'xi-api-key': apiKey,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                text,
                model_id: "eleven_turbo_v2_5",
                voice_settings: { stability: 0.5, similarity_boost: 0.75, style: 0.0, use_speaker_boost: true }
            })
        });

        if (!response.ok) {
            const data = await response.json();
            return res.status(response.status).json({ success: false, error: data });
        }

        res.setHeader('Content-Type', 'audio/mpeg');
        response.body.pipe(res);
    } catch (e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

app.post('/api/v1/web/search', async (req, res) => {
    try {
        const { query } = req.body;
        // Mocking search for simplicity, in a real env we would use SERP API / Google Custom Search / DuckDuckGo
        return res.json({ 
            success: true, 
            results: [
                { title: `Search result for ${query} 1`, snippet: "This is a snippet.", url: "https://example.com/1" },
                { title: `Search result for ${query} 2`, snippet: "This is another snippet.", url: "https://example.com/2" }
            ] 
        });
    } catch(e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

app.post('/api/v1/web/open', async (req, res) => {
    try {
        const { url } = req.body;
        const response = await fetch(url);
        const text = await response.text();
        const $ = cheerio.load(text);
        const content = $('body').text().replace(/\s+/g, ' ').trim().substring(0, 5000); // truncate for prompt limit
        return res.json({ success: true, url, content });
    } catch(e) {
        res.status(500).json({ success: false, error: e.message });
    }
});

app.listen(PORT, () => console.log(`Jarvis Backend running on port ${PORT}`));
