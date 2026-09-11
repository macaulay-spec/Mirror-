import re

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "r") as f:
    content = f.read()

# Add import
content = content.replace(
    "import android.content.SharedPreferences",
    "import android.content.SharedPreferences\nimport androidx.security.crypto.EncryptedSharedPreferences\nimport androidx.security.crypto.MasterKey"
)

# Update prefs()
new_prefs = """    private var _prefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        if (_prefs == null) {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            _prefs = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return _prefs!!
    }"""

content = re.sub(
    r"    private fun prefs\(context: Context\): SharedPreferences =\n        context\.getSharedPreferences\(PREFS_NAME, Context\.MODE_PRIVATE\)",
    new_prefs,
    content
)

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "w") as f:
    f.write(content)

print("done")
