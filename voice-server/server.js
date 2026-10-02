import "dotenv/config";
import express from "express";
import OpenAI from "openai";
import WebSocket from "ws";

const app = express();
const port = Number(process.env.PORT || 8080);

const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY,
  webhookSecret: process.env.OPENAI_WEBHOOK_SECRET
});

const SYSTEM_PROMPT = [
  "You are Saeed AI, the phone assistant for Saeed.",
  "Speak natural Egyptian Arabic, briefly, warmly, and clearly.",
  "You answer every incoming call. Never claim to be Saeed.",
  "If the caller asks for Saeed, asks Saeed to call them, or says equivalent phrases such as عايز سعيد or خليه يكلمني, use notify_saeed.",
  "Ask for the caller name and message when needed, then notify Saeed.",
  "After notification succeeds, tell the caller that Saeed was notified.",
  "Do not promise a specific callback time."
].join("\n");

const tools = [{
  type: "function",
  name: "notify_saeed",
  description: "Notify Saeed that the caller wants to speak with him.",
  parameters: {
    type: "object",
    properties: {
      caller_name: { type: "string" },
      caller_number: { type: "string" },
      message: { type: "string" }
    },
    required: ["caller_name", "caller_number", "message"],
    additionalProperties: false
  }
}];

function sipHeader(headers, name) {
  const h = (headers || []).find(
    x => String(x.name || "").toLowerCase() === name.toLowerCase()
  );
  return h?.value || "";
}

function callerFromHeaders(headers) {
  const from = sipHeader(headers, "From");
  return {
    number: from.replace(/^sip:/i, "").split("@")[0].replace(/[<>]/g, "")
  };
}

async function notifySaeed(args) {
  if (!process.env.WHATSAPP_NOTIFY_URL) {
    throw new Error("WHATSAPP_NOTIFY_URL is not configured");
  }

  const r = await fetch(process.env.WHATSAPP_NOTIFY_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      caller_name: args.caller_name || "",
      caller_number: args.caller_number || "",
      message: args.message || ""
    })
  });

  if (!r.ok) {
    throw new Error("WhatsApp notification failed: " + r.status);
  }
}

function sendJson(ws, payload) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

async function handleNotifyTool(ws, event, caller) {
  const args = JSON.parse(event.arguments || "{}");
  args.caller_number = args.caller_number || caller.number || "unknown";

  try {
    await notifySaeed(args);

    sendJson(ws, {
      type: "conversation.item.create",
      item: {
        type: "function_call_output",
        call_id: event.call_id,
        output: JSON.stringify({ ok: true })
      }
    });

    sendJson(ws, {
      type: "response.create",
      response: {
        modalities: ["audio"],
        instructions: "قل للمتصل باختصار وباللهجة المصرية: تمام، بلغت سعيد بطلبك."
      }
    });
  } catch (err) {
    console.error("WhatsApp notification error:", err);

    sendJson(ws, {
      type: "conversation.item.create",
      item: {
        type: "function_call_output",
        call_id: event.call_id,
        output: JSON.stringify({ ok: false })
      }
    });

    sendJson(ws, {
      type: "response.create",
      response: {
        modalities: ["audio"],
        instructions: "اعتذر للمتصل باختصار وباللهجة المصرية وقل إنك لم تتمكن من إرسال التنبيه الآن."
      }
    });
  }
}

function attachRealtimeCall(callId, caller) {
  const ws = new WebSocket(
    "wss://api.openai.com/v1/realtime?call_id=" + encodeURIComponent(callId),
    { headers: { Authorization: "Bearer " + process.env.OPENAI_API_KEY } }
  );

  ws.on("open", () => {
    sendJson(ws, {
      type: "session.update",
      session: {
        instructions: SYSTEM_PROMPT,
        tools,
        tool_choice: "auto",
        audio: {
          input: {
            turn_detection: {
              type: "server_vad",
              interrupt_response: true
            }
          },
          output: { voice: process.env.AI_VOICE || "marin" }
        }
      }
    });

    sendJson(ws, {
      type: "response.create",
      response: {
        modalities: ["audio"],
        instructions: "Say: أهلاً، معاك Saeed AI، اتفضل."
      }
    });
  });

  ws.on("message", async raw => {
    try {
      const e = JSON.parse(raw.toString());

      if (
        e.type === "response.function_call_arguments.done" &&
        e.name === "notify_saeed"
      ) {
        await handleNotifyTool(ws, e, caller);
      }

      if (e.type === "error") {
        console.error("Realtime error:", e.error);
      }
    } catch (err) {
      console.error("Realtime event error:", err);
    }
  });

  ws.on("close", () => console.log("Realtime call ended:", callId));
  ws.on("error", err => console.error("Realtime socket error:", err));
}

function attachLiveSession(sessionId, caller) {
  const ws = new WebSocket(
    "wss://api.openai.com/v1/live/sessions/" +
      encodeURIComponent(sessionId) +
      "/attach",
    { headers: { Authorization: "Bearer " + process.env.OPENAI_API_KEY } }
  );

  ws.on("open", () => {
    // SIP carries the audio. The sideband socket is only for session events,
    // tools, commands, and responses. Do not send session.start here.
    sendJson(ws, {
      type: "response.create",
      response: {
        instructions: "Say in natural Egyptian Arabic: أهلاً، معاك Saeed AI، اتفضل."
      }
    });
  });

  ws.on("message", async raw => {
    try {
      const e = JSON.parse(raw.toString());

      if (
        e.type === "response.function_call_arguments.done" &&
        e.name === "notify_saeed"
      ) {
        await handleNotifyTool(ws, e, caller);
      }

      if (e.type === "error") {
        console.error("Live error:", e.error);
      }

      if (e.type === "session.closed") {
        console.log("Live session closed:", sessionId);
      }
    } catch (err) {
      console.error("Live event error:", err);
    }
  });

  ws.on("close", () => console.log("Live sideband ended:", sessionId));
  ws.on("error", err => console.error("Live sideband socket error:", err));
}

async function acceptLiveSession(sessionId) {
  const response = await fetch(
    "https://api.openai.com/v1/live/sessions/" +
      encodeURIComponent(sessionId) +
      "/accept",
    {
      method: "POST",
      headers: {
        Authorization: "Bearer " + process.env.OPENAI_API_KEY,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        session: {
          type: "live",
          model: process.env.LIVE_MODEL || "gpt-live-1",
          instructions: SYSTEM_PROMPT,
          tools,
          tool_choice: "auto",
          audio: {
            output: { voice: process.env.AI_VOICE || "marin" }
          },
          delegation: { type: "client" }
        }
      })
    }
  );

  if (!response.ok) {
    throw new Error(
      "Live accept failed " + response.status + ": " + await response.text()
    );
  }
}

app.get("/", (_req, res) => {
  res.json({
    service: "Saeed AI Phone Bot",
    status: "running",
    mode: "OpenAI GPT-Live SIP"
  });
});

app.post(
  "/openai/webhook",
  express.text({ type: "application/json" }),
  async (req, res) => {
    try {
      const event = await openai.webhooks.unwrap(req.body, req.headers);

      // Current GPT-Live SIP contract.
      if (event.type === "live.transport.incoming") {
        const sessionId = event.data?.session_id;
        if (!sessionId) {
          console.error("Live webhook missing session_id");
          return res.sendStatus(400);
        }

        const caller = callerFromHeaders(event.data?.sip_headers);

        await acceptLiveSession(sessionId);
        attachLiveSession(sessionId, caller);

        return res.sendStatus(200);
      }

      // Backward compatibility for existing Realtime SIP integrations.
      if (event.type === "realtime.call.incoming") {
        const callId = event.data?.call_id;
        if (!callId) {
          console.error("Realtime webhook missing call_id");
          return res.sendStatus(400);
        }

        const caller = callerFromHeaders(event.data?.sip_headers);

        const accept = await fetch(
          "https://api.openai.com/v1/realtime/calls/" +
            encodeURIComponent(callId) +
            "/accept",
          {
            method: "POST",
            headers: {
              Authorization: "Bearer " + process.env.OPENAI_API_KEY,
              "Content-Type": "application/json"
            },
            body: JSON.stringify({
              type: "realtime",
              model: process.env.REALTIME_MODEL || "gpt-realtime-2.1",
              instructions: SYSTEM_PROMPT,
              tools
            })
          }
        );

        if (!accept.ok) {
          console.error("Realtime accept failed:", accept.status, await accept.text());
          return res.sendStatus(500);
        }

        void attachRealtimeCall(callId, caller);
        return res.sendStatus(200);
      }

      return res.sendStatus(200);
    } catch (err) {
      console.error("Webhook error:", err);
      return res.status(400).send("Invalid webhook");
    }
  }
);

app.listen(port, () => console.log("Saeed AI Phone Bot on", port));
