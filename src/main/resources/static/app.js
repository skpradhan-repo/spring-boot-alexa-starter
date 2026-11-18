
const API_URL = "/alexa";

// Replace this with your actual Alexa Skill ID
const SKILL_ID = "amzn1.ask.skill.your-skill-id";

// Speech recognition setup
const SpeechRecognition =
    window.SpeechRecognition || window.webkitSpeechRecognition;

let recognition = null;

const micButton = document.getElementById("micButton");
const sendButton = document.getElementById("sendButton");
const messageInput = document.getElementById("messageInput");

const statusText = document.getElementById("status");
const recognizedText = document.getElementById("recognizedText");
const alexaResponse = document.getElementById("alexaResponse");
const assistantCircle = document.getElementById("assistantCircle");

// ------------------------------------------
// INITIALIZE SPEECH RECOGNITION
// ------------------------------------------

if (SpeechRecognition) {

    recognition = new SpeechRecognition();

    recognition.lang = "en-IN";
    recognition.continuous = false;
    recognition.interimResults = false;

    recognition.onstart = function () {

        statusText.innerText = "Listening... Speak now";

        assistantCircle.classList.add("listening");

        micButton.innerText = "Listening...";

        micButton.disabled = true;
    };

    recognition.onresult = function (event) {

        const transcript =
            event.results[0][0].transcript;

        messageInput.value = transcript;

        recognizedText.innerText = transcript;

    };

    recognition.onerror = function (event) {

        statusText.innerText =
            "Speech recognition error: " + event.error;

    };

    recognition.onend = function () {

        assistantCircle.classList.remove("listening");

        micButton.innerText = "🎤 Start Listening";

        micButton.disabled = false;

        // Automatically send recognized speech
        if (messageInput.value.trim()) {
            sendToAlexa();
        } else {
            statusText.innerText =
                "Click the microphone and start speaking";
        }
    };

} else {

    micButton.disabled = true;

    statusText.innerText =
        "Speech recognition is not supported in this browser.";
}


// ------------------------------------------
// START LISTENING
// ------------------------------------------

function startListening() {

    if (!recognition) {
        alert("Please use Chrome or Edge for voice input.");
        return;
    }

    try {

        recognition.start();

    } catch (error) {

        console.error("Microphone error:", error);

        statusText.innerText =
            "Unable to start microphone.";

    }
}


// ------------------------------------------
// CREATE ALEXA JSON REQUEST
// ------------------------------------------

function createAlexaRequest(message) {

    const timestamp = new Date().toISOString();

    const requestId = "amzn1.echo-api.request." +
        crypto.randomUUID();

    const sessionId = "amzn1.echo-api.session." +
        crypto.randomUUID();

    return {

        version: "1.0",

        session: {

            new: false,

            sessionId: sessionId,

            application: {
                applicationId: SKILL_ID
            },

            user: {
                userId: "local-test-user"
            },

            attributes: {}

        },

        request: {

            type: "IntentRequest",

            requestId: requestId,

            timestamp: timestamp,

            locale: "en-IN",

            intent: {

                name: "HelloWorldIntent",

                confirmationStatus: "NONE",

                slots: {

                    message: {

                        name: "message",

                        value: message,

                        confirmationStatus: "NONE"

                    }

                }

            }

        }

    };

}


// ------------------------------------------
// SEND REQUEST TO SPRING BOOT
// ------------------------------------------

async function sendToAlexa() {

    const message = messageInput.value.trim();

    if (!message) {

        alert("Please enter or speak something.");

        return;
    }

    recognizedText.innerText = message;

    alexaResponse.innerText = "Processing...";

    statusText.innerText = "Sending request to Alexa backend...";

    sendButton.disabled = true;
    micButton.disabled = true;

    try {

        const alexaRequest = createAlexaRequest(message);

        // Display generated JSON in browser console
        console.log("Alexa Request JSON:", alexaRequest);

        const response = await fetch(API_URL, {

            method: "POST",

            headers: {
                "Content-Type": "application/json"
            },

            body: JSON.stringify(alexaRequest)

        });

        const result = await response.json();

        console.log("Alexa Response:", result);

        if (!response.ok) {

            throw new Error(
                result.message || "Backend returned HTTP " + response.status
            );

        }

        const speech = result.response?.outputSpeech;

        let responseText = "Alexa processed your request.";

        if (speech?.ssml) {

            responseText = speech.ssml.replace(/<[^>]*>/g, "");

        } else if (speech?.text) {

            responseText = speech.text;

        }

        alexaResponse.innerText = responseText;

        statusText.innerText = "Request completed successfully!";

        // Optional: Make the browser speak Alexa's response
        if ("speechSynthesis" in window) {

            window.speechSynthesis.cancel();

            const utterance =
                new SpeechSynthesisUtterance(responseText);

            utterance.lang = "en-IN";

            window.speechSynthesis.speak(utterance);

        }

    } catch (error) {

        console.error("Alexa API error:", error);

        alexaResponse.innerText = error.message;

        statusText.innerText = "Something went wrong.";

    } finally {

        sendButton.disabled = false;
        micButton.disabled = !recognition;

    }

}


// ------------------------------------------
// EVENT LISTENERS
// ------------------------------------------

micButton.addEventListener("click", startListening);

sendButton.addEventListener("click", sendToAlexa);

messageInput.addEventListener("keydown", function (event) {

    if (event.key === "Enter") {
        sendToAlexa();
    }

});
