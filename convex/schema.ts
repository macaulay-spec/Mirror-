import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

/**
 * JARVIS Convex Schema
 *
 * Stores user preferences and voice selection.
 * API keys are stored as Convex environment variables (secrets), NOT in the database.
 */
export default defineSchema({
  // User voice preferences
  voicePreferences: defineTable({
    userId: v.string(),                     // device identifier (no auth needed for MVP)
    voiceId: v.string(),                    // ElevenLabs voice ID
    voiceName: v.string(),                  // display name
    engineType: v.string(),                 // "elevenlabs" | "android_tts"
    stability: v.number(),                  // 0-1, ElevenLabs setting
    similarityBoost: v.number(),            // 0-1, ElevenLabs setting
    style: v.number(),                      // 0-1, ElevenLabs setting
  }).index("by_userId", ["userId"]),

  // Conversation history (optional — keeps context across sessions)
  conversations: defineTable({
    userId: v.string(),
    sessionId: v.string(),
    role: v.string(),                       // "user" | "assistant" | "system"
    content: v.string(),
    timestamp: v.number(),
  }).index("by_session", ["userId", "sessionId"]),

  // User settings
  userSettings: defineTable({
    userId: v.string(),
    userName: v.string(),
    personalityTone: v.string(),            // "jarvis_protocol" | "conversational" | "executive"
    activeProvider: v.string(),             // "gemini" | "xai" | "openai" | etc.
    activeModel: v.string(),
  }).index("by_userId", ["userId"]),
});
