const express = require('express');
const cors = require('cors');
const fetch = require('node-fetch');
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

app.post('/api/ai/chat', async (req, res) => {
    try {
        const { provider = 'gemini', model = 'gemini-3.5-flash', messages, system } = req.body;
        
        const provConfig = PROVIDERS[provider];
        if (!provConfig || provConfig.keys.length === 0) {
            return res.status(500).json({ success: false, error: "Provider not configured" });
        }
        
        // Simple round-robin or first available key
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
            // Setup for OpenAI
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

app.listen(PORT, () => console.log(`Jarvis Backend running on port ${PORT}`));
