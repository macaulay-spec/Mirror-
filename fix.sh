sed -i 's/val isSelected =  == selectedVoice/val isSelected = preset.id == selectedVoice/g' app/src/main/java/com/jarvis/feature/voice/VoiceRoomScreen.kt
sed -i 's/val isPreviewing =  == previewingVoice/val isPreviewing = preset.id == previewingVoice/g' app/src/main/java/com/jarvis/feature/voice/VoiceRoomScreen.kt
sed -i 's/selectedVoice = /selectedVoice = preset.id/g' app/src/main/java/com/jarvis/feature/voice/VoiceRoomScreen.kt
sed -i 's/ApiConfig.saveVoicePreferences(context, "cloud", )/ApiConfig.saveVoicePreferences(context, "cloud", preset.id)/g' app/src/main/java/com/jarvis/feature/voice/VoiceRoomScreen.kt
sed -i 's/previewingVoice = /previewingVoice = preset.id/g' app/src/main/java/com/jarvis/feature/voice/VoiceRoomScreen.kt
