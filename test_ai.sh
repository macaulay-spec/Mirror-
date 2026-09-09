#!/bin/bash
# Uses NVIDIA_API_KEY environment variable.
curl -s -X POST -H "Authorization: Bearer ${NVIDIA_API_KEY}" \
-H "Content-Type: application/json" \
-d '{"model": "meta/llama-3.2-11b-vision-instruct", "messages": [{"role":"system", "content": "You are JARVIS."}, {"role":"user", "content":"Hi"}, {"role":"assistant", "content": "I need to check my memory.\n<tool_call>memory_recall</tool_call>"}, {"role":"user", "content": "Observation: No matching memories found. Now reply to the user."}], "tools": [{"type": "function", "function": {"name": "memory_recall", "description": "recall memory"}}]}' \
https://integrate.api.nvidia.com/v1/chat/completions
