#!/bin/bash
# JARVIS Convex Backend Setup
# Run this from the convex/ directory

set -e

echo "🔧 JARVIS Convex Backend Setup"
echo "=============================="
echo ""

# Check if already logged in
if ! npx convex whoami 2>/dev/null; then
    echo "📝 Logging into Convex..."
    npx convex login
fi

echo ""
echo "🚀 Deploying Convex backend..."
npx convex deploy

echo ""
echo "🔑 Setting environment variables..."
echo "You'll be prompted for each secret. Paste the values when asked."
echo ""

# Set secrets
npx convex env set XAI_API_KEY
npx convex env set GEMINI_API_KEY
npx convex env set ELEVENLABS_API_KEY
npx convex env set OPENAI_API_KEY
npx convex env set ANTHROPIC_API_KEY
npx convex env set GROQ_API_KEY

echo ""
echo "✅ Done! Copy your deployment URL from the output above."
echo "Then update BackendConfig.WORKER_URL in:"
echo "  app/src/main/java/com/jarvis/app/config/BackendConfig.kt"
echo ""
echo "Example: https://your-deployment.convex.site"
