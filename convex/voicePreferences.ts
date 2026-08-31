import { v } from "convex/values";
import { query, mutation } from "./_generated/server";

// Get voice preferences for a user
export const getByUserId = query({
  args: { userId: v.string() },
  handler: async (ctx, args) => {
    return await ctx.db
      .query("voicePreferences")
      .withIndex("by_userId", (q) => q.eq("userId", args.userId))
      .first();
  },
});

// Insert voice preferences
export const insert = mutation({
  args: {
    userId: v.string(),
    voiceId: v.string(),
    voiceName: v.string(),
    engineType: v.string(),
    stability: v.number(),
    similarityBoost: v.number(),
    style: v.number(),
  },
  handler: async (ctx, args) => {
    return await ctx.db.insert("voicePreferences", args);
  },
});

// Update voice preferences
export const update = mutation({
  args: {
    id: v.id("voicePreferences"),
    voiceId: v.optional(v.string()),
    voiceName: v.optional(v.string()),
    engineType: v.optional(v.string()),
    stability: v.optional(v.number()),
    similarityBoost: v.optional(v.number()),
    style: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    const { id, ...updates } = args;
    const filtered = Object.fromEntries(
      Object.entries(updates).filter(([_, v]) => v !== undefined)
    );
    if (Object.keys(filtered).length > 0) {
      await ctx.db.patch(id, filtered);
    }
  },
});
