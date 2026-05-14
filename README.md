# Boris AI Agent 🤖

## 🚀 Build

Before building, compile the Vue frontend:

```bash
rpm install
npm run build
```

This generates the production files in `src/main/resources/static/dist`.

Then run the application:

```bash
./mvnw spring-boot:run
```

Or build the JAR:

```bash
./mvnw clean package
java -jar target/boris-ai-1.0.0.jar
```

### Build Native Installer (Windows)

```cmd
build-windows.bat
```

Or manually:
```bash
./mvnw clean package
```

The JAR will be at `target/boris-ai-1.0.0.jar`.

**Note:** ProGuard obfuscation is temporarily disabled due to Java 21 compatibility.

---

## 👨‍💻 Author

**Anibal Gomez** — anibal@librixsoft.com

> *"I am invincible!"* — **Boris AI**