import { v } from "convex/values";
import { query, mutation } from "./_generated/server";

// Get user settings
export const getByUserId = query({
  args: { userId: v.string() },
  handler: async (ctx, args) => {
    return await ctx.db
      .query("userSettings")
      .withIndex("by_userId", (q) => q.eq("userId", args.userId))
      .first();
  },
});

// Insert user settings
export const insert = mutation({
  args: {
    userId: v.string(),
    userName: v.string(),
    personalityTone: v.string(),
    activeProvider: v.string(),
    activeModel: v.string(),
  },
  handler: async (ctx, args) => {
    return await ctx.db.insert("userSettings", args);
  },
});

// Update user settings
export const update = mutation({
  args: {
    id: v.id("userSettings"),
    userName: v.optional(v.string()),
    personalityTone: v.optional(v.string()),
    activeProvider: v.optional(v.string()),
    activeModel: v.optional(v.string()),
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
