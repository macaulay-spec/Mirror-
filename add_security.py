with open("app/build.gradle.kts", "r") as f:
    content = f.read()

content = content.replace(
    "implementation(libs.datastore.preferences)",
    "implementation(libs.datastore.preferences)\n    implementation(\"androidx.security:security-crypto:1.1.0-alpha06\")"
)

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
