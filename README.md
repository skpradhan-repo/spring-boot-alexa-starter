# spring-boot-alexa-starter
A local end-to-end **Alexa Skill simulator** powered by **Spring Boot**, the **Amazon Alexa Skills Kit (ASK) Java SDK**, and a browser-based **Web Speech API** interface. Test full Alexa `RequestEnvelope` lifecycles locally without needing an Echo device or AWS Lambda deployment.

## ✨ Features
* **Browser Voice Interface:** Captures voice via `SpeechRecognition` (`en-IN`), builds valid Alexa `IntentRequest` payloads with slot values, and speaks responses aloud using `speechSynthesis`.
* **Spring Boot Webhook (`/alexa`):** Deserializes raw Alexa JSON payloads and routes requests through custom ASK `RequestHandler` implementations (`LaunchRequestHandler`, `HelloWorldIntentHandler`, `FallbackHandler`).
* **Local Testing Ready:** Includes pre-wired signature and timestamp verifiers (commented out for local development) and single-line cURL test commands.

## 🛠️ Tech Stack
* **Backend:** Java 17+, Spring Boot, Alexa Skills Kit SDK (`ask-sdk-core`)
* **Frontend:** HTML5, CSS3, Vanilla JavaScript (Web Speech API)

## 🚀 Quick Start

1. **Start the Spring Boot application:**
   ```bash
   ./mvnw spring-boot:run
   ```
2. **Open the Voice Assistant UI:**
   Navigate to `http://localhost:8080/` in Google Chrome or Microsoft Edge.
3. **Speak or Type:**
   Click **🎤 Start Listening** (or type a message and press **Send**) to trigger `HelloWorldIntent` with your message mapped to the `message` slot.
