import re

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "r") as f:
    content = f.read()

bad_str = "                val tokens = raw.split(',', ';', '\n', '\r')"
good_str = "                val tokens = raw.split(',', ';', '\\n', '\\r')"

# Actually we need to replace the multi-line bad string
content = re.sub(
    r"val tokens = raw\.split\(',', ';', '\n', ''\)",
    good_str,
    content
)

with open("app/src/main/java/com/jarvis/app/config/ApiConfig.kt", "w") as f:
    f.write(content)

print("done")
