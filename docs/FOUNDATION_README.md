# JARVIS Foundation Architecture V2

## Overview
This foundation represents the hard architectural reset of the JARVIS personal AI assistant application on Android.

## Architecture Layers
- **`com.jarvis.core.model`**: Strongly typed domain models, execution states, and risk tiers (Level 0 to Level 4).
- **`com.jarvis.agent.orchestrator`**: ContextBuilder, AI reasoner, structured Tool Calling, Risk Checks, Action Verification, and Memory updates.
- **`com.jarvis.agent.tool`**: Comprehensive ToolRegistry defining tool schemas, risk classification, confirmation policy, and native Android execution.
- **`com.jarvis.android`**: Native platform subsystems (VoiceInteraction, Accessibility, NotificationListener, DeviceCapabilities, Permissions).
- **`com.jarvis.core.theme` & `com.jarvis.core.ui`**: Living holographic JARVIS Core with 3D spherical wireframe, counter-rotating orbitals, and dynamic state reactivity.
- **`com.jarvis.feature.home`**: DualModeHost (Compact HUD & Expanded Holo Cockpit) with conversational streams, memory diagnostics, and emergency kill-switch.
