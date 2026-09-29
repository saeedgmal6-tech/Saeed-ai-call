import 'dotenv/config';
import express from 'express';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { WebSocketServer, WebSocket } from 'ws';
import twilio from 'twilio';
import { GoogleGenAI, Modality } from '@google/genai';
import { mulaw } from 'alawmulaw';
import { rateLimit } from 'express-rate-limit';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const DATA_DIR = path.join(ROOT, 'data');
const SETTINGS_FILE = path.join(DATA_DIR, 'settings.json');
const app = express();
const server = http.createServer(app);
const streams = new WebSocketServer({ noServer: true, maxPayload: 1_000_000 });
const { VoiceResponse } = twilio.twiml;

const defaults = {
  phoneNumbers: [],
  greeting: 'مرحبًا، أنا سليم المساعد الإلكتروني لسعيد.',
  instructions: 'تحدث بالعربية المصرية بشكل طبيعي ومختصر. عرّف بنفسك كمساعد إلكتروني، واسأل المتصل كيف تساعده. لا تدّع أنك إنسان. لا تخترع معلومات عن سعيد. إذا كان الطلب شخصيًا وموجهًا لسعيد، اسأل المتصل عن كلمة التنبيه ثم دوّن رقم المتصل وملخص طلبه وأرسل تنبيه واتساب. لا ترسل التنبيه إلا بطلب شخصي واضح وموافقة المتصل على تمرير رسالته.',
  alertKeyword: 'ضروري',
  ownerWhatsapp: process.env.WHATSAPP_OWNER_NUMBER || '',
  callsEnabled: process.env.CALLS_ENABLED === 'true'
};
let settings = defaults;
const recentCalls = [];

async function loadSettings() {
  await mkdir(DATA_DIR, { recursive: true });
  try { settings = { ...defaults, ...JSON.parse(await readFile(SETTINGS_FILE, 'utf8')) }; }
  catch (error) { if (error.code !== 'ENOENT') throw error; await saveSettings(); }
}
async function saveSettings() {
  await mkdir(DATA_DIR, { recursive: true });
  await writeFile(SETTINGS_FILE, JSON.stringify(settings, null, 2), { mode: 0o600 });
}
function basicAuth(req, res, next) {
  const expected = process.env.ADMIN_PASSWORD;
  if (!expected) return res.status(503).send('Set ADMIN_PASSWORD in .env before opening the dashboard.');
  const [scheme, encoded] = (req.headers.authorization || '').split(' ');
  if (scheme === 'Basic' && encoded) {
    const decoded = Buffer.from(encoded, 'base64').toString();
    const pass = decoded.slice(decoded.indexOf(':') + 1);
    if (pass.length === expected.length && Buffer.from(pass).equals(Buffer.from(expected))) return next();
  }
  res.set('WWW-Authenticate', 'Basic realm="Salim Admin", charset="UTF-8"');
  return res.status(401).send('Authentication required');
}
function twilioSignatureOk(req, route) {
  const token = process.env.TWILIO_AUTH_TOKEN;
  if (!token || !process.env.PUBLIC_BASE_URL) return false;
  const url = `${process.env.PUBLIC_BASE_URL.replace(/\/$/, '')}${route}`;
  return twilio.validateRequest(token, req.headers['x-twilio-signature'] || '', url, req.body || {});
}
function publicConfig() {
  return {
    phoneNumbers: settings.phoneNumbers,
    greeting: settings.greeting,
    instructions: settings.instructions,
    alertKeyword: settings.alertKeyword,
    ownerWhatsapp: settings.ownerWhatsapp,
    callsEnabled: settings.callsEnabled,
    callCount: recentCalls.length,
    recentCalls: recentCalls.slice(-20).reverse(),
    integrations: {
      gemini: Boolean(process.env.GEMINI_API_KEY),
      telephony: Boolean(process.env.TWILIO_ACCOUNT_SID && process.env.TWILIO_AUTH_TOKEN),
      whatsapp: Boolean(process.env.WHATSAPP_ACCESS_TOKEN && process.env.WHATSAPP_PHONE_NUMBER_ID && process.env.WHATSAPP_GRAPH_VERSION && settings.ownerWhatsapp),
      publicUrl: Boolean(process.env.PUBLIC_BASE_URL?.startsWith('https://'))
    }
  };
}

app.set('trust proxy', 1);
app.use('/voice', express.urlencoded({ extended: false, limit: '32kb' }));
app.use('/api', express.json({ limit: '32kb' }));
app.use('/api', rateLimit({ windowMs: 60_000, limit: 100, standardHeaders: 'draft-7', legacyHeaders: false }));
app.use(express.static(path.join(ROOT, 'public'), { index: false }));
app.get('/', basicAuth, (_req, res) => res.sendFile(path.join(ROOT, 'public', 'index.html')));
app.get('/health', (_req, res) => res.json({ ok: true, callsEnabled: settings.callsEnabled }));
app.get('/api/config', basicAuth, (_req, res) => res.json(publicConfig()));
app.put('/api/config', basicAuth, async (req, res, next) => {
  try {
    const { phoneNumbers, greeting, instructions, alertKeyword, ownerWhatsapp, callsEnabled } = req.body || {};
    if (phoneNumbers !== undefined) {
      if (!Array.isArray(phoneNumbers) || phoneNumbers.length > 100 || phoneNumbers.some(x => !/^\+[1-9]\d{6,14}$/.test(x.phone || ''))) return res.status(400).json({ error: 'Enter phone numbers in international E.164 format.' });
      settings.phoneNumbers = phoneNumbers.map(x => ({ phone: x.phone, label: String(x.label || '').slice(0, 80) }));
    }
    if (greeting !== undefined) settings.greeting = String(greeting).slice(0, 300);
    if (instructions !== undefined) settings.instructions = String(instructions).slice(0, 5000);
    if (alertKeyword !== undefined) settings.alertKeyword = String(alertKeyword).slice(0, 80);
    if (ownerWhatsapp !== undefined) {
      if (ownerWhatsapp && !/^\+[1-9]\d{6,14}$/.test(ownerWhatsapp)) return res.status(400).json({ error: 'WhatsApp number must use international E.164 format.' });
      settings.ownerWhatsapp = ownerWhatsapp;
    }
    if (callsEnabled !== undefined) {
      const ready = Boolean(process.env.GEMINI_API_KEY && process.env.TWILIO_ACCOUNT_SID && process.env.TWILIO_AUTH_TOKEN && process.env.PUBLIC_BASE_URL?.startsWith('https://') && process.env.WHATSAPP_ACCESS_TOKEN && process.env.WHATSAPP_PHONE_NUMBER_ID && process.env.WHATSAPP_GRAPH_VERSION && settings.ownerWhatsapp);
      if (callsEnabled && !ready) return res.status(400).json({ error: 'Add Gemini, Twilio, WhatsApp credentials, the owner WhatsApp number, and a public HTTPS URL before enabling calls.' });
      settings.callsEnabled = Boolean(callsEnabled);
    }
    await saveSettings();
    res.json(publicConfig());
  } catch (e) { next(e); }
});

app.post('/voice/incoming', (req, res) => {
  if (!twilioSignatureOk(req, '/voice/incoming')) return res.sendStatus(403);
  const twiml = new VoiceResponse();
  if (!settings.callsEnabled || !process.env.GEMINI_API_KEY) {
    twiml.say({ language: 'ar-XA' }, 'عذرًا، المساعد غير متاح حاليًا. برجاء المحاولة لاحقًا.');
    twiml.hangup();
    return res.type('text/xml').send(twiml.toString());
  }
  if (process.env.GREETING_AUDIO_URL) twiml.play(process.env.GREETING_AUDIO_URL);
  const connect = twiml.connect();
  const stream = connect.stream({ url: `${process.env.PUBLIC_BASE_URL.replace(/^https:/, 'wss:')}/voice/stream` });
  stream.parameter({ name: 'caller', value: String(req.body.From || '').slice(0, 24) });
  stream.parameter({ name: 'callSid', value: String(req.body.CallSid || '').slice(0, 64) });
  res.type('text/xml').send(twiml.toString());
});

function decodeMulawToPcm16k(base64) {
  const decoded = mulaw.decode(Buffer.from(base64, 'base64'));
  const doubled = Buffer.alloc(decoded.length * 4);
  for (let i = 0; i < decoded.length; i++) {
    const a = decoded[i], b = decoded[Math.min(i + 1, decoded.length - 1)];
    doubled.writeInt16LE(a, i * 4);
    doubled.writeInt16LE((a + b) >> 1, i * 4 + 2);
  }
  return doubled;
}
function pcm24kToMulaw8k(base64) {
  const pcm = Buffer.from(base64, 'base64');
  const count = Math.floor(pcm.length / 6);
  const samples = new Int16Array(count);
  for (let i = 0; i < count; i++) {
    const s0 = pcm.readInt16LE(i * 6), s1 = pcm.readInt16LE(i * 6 + 2), s2 = pcm.readInt16LE(i * 6 + 4);
    samples[i] = Math.max(-32768, Math.min(32767, Math.round((s0 + s1 + s2) / 3)));
  }
  return Buffer.from(mulaw.encode(samples)).toString('base64');
}
async function sendWhatsappAlert({ caller, keyword, message }) {
  const { WHATSAPP_ACCESS_TOKEN: token, WHATSAPP_PHONE_NUMBER_ID: id, WHATSAPP_GRAPH_VERSION: graphVersion, WHATSAPP_ALERT_TEMPLATE = 'salim_personal_alert', WHATSAPP_TEMPLATE_LANGUAGE = 'ar' } = process.env;
  if (!token || !id || !graphVersion || !settings.ownerWhatsapp) throw new Error('WhatsApp Cloud API is not configured.');
  const digits = settings.ownerWhatsapp.replace(/\D/g, '');
  const response = await fetch(`https://graph.facebook.com/${graphVersion}/${id}/messages`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ messaging_product: 'whatsapp', recipient_type: 'individual', to: digits, type: 'template', template: { name: WHATSAPP_ALERT_TEMPLATE, language: { code: WHATSAPP_TEMPLATE_LANGUAGE }, components: [{ type: 'body', parameters: [{ type: 'text', text: caller || 'غير معروف' }, { type: 'text', text: keyword || '—' }, { type: 'text', text: message || '—' }] }] } })
  });
  if (!response.ok) throw new Error(`WhatsApp send failed (${response.status}): ${(await response.text()).slice(0, 300)}`);
}

const notifyTool = [{ functionDeclarations: [{ name: 'notify_owner', description: 'Use only after the caller clearly wants to pass a personal message to Saeed, and has agreed to have it sent. Capture the spoken alert keyword and a concise faithful summary.', parameters: { type: 'OBJECT', properties: { keyword: { type: 'STRING', description: 'Alert keyword spoken by caller' }, message: { type: 'STRING', description: 'Concise summary of what the caller wants Saeed to know' } }, required: ['keyword', 'message'] } }] }];
function startGeminiSession(twilioSocket, streamSid, caller) {
  const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY, httpOptions: { apiVersion: 'v1beta' } });
  let ready = false, closed = false, greeted = false, liveSession;
  const connected = ai.live.connect({
    model: process.env.GEMINI_MODEL || 'gemini-3.8-live',
    config: {
      responseModalities: [Modality.AUDIO],
      speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: process.env.GEMINI_VOICE || 'Kore' } } },
      systemInstruction: `${settings.instructions}\nرسالة الترحيب المطلوبة حرفيًا في بداية المكالمة: ${settings.greeting}\nرقم المتصل الحالي: ${caller || 'غير معروف'}\nكلمة التنبيه التي أعدّها سعيد: ${settings.alertKeyword}. عند طلب تمرير رسالة شخصية، اطلب موافقة المتصل ثم اسأله عن الكلمة والرسالة. لا تستدع notify_owner قبل وضوح الكلمة والرسالة والموافقة.`,
      tools: notifyTool,
    },
    callbacks: {
      onopen() { connected.then(session => { liveSession = session; ready = true; if (closed) return session.close(); if (!greeted) { greeted = true; session.sendRealtimeInput({ text: `ابدأ الآن وقل حرفيًا: ${settings.greeting}` }); } }).catch(error => console.error('[Gemini Live]', error.message)); },
      onmessage(message) {
        const content = message.serverContent;
        if (content?.interrupted && twilioSocket.readyState === WebSocket.OPEN) twilioSocket.send(JSON.stringify({ event: 'clear', streamSid }));
        for (const part of content?.modelTurn?.parts || []) if (part.inlineData?.data && twilioSocket.readyState === WebSocket.OPEN) {
          const payload = pcm24kToMulaw8k(part.inlineData.data);
          twilioSocket.send(JSON.stringify({ event: 'media', streamSid, media: { payload } }));
        }
        if (message.toolCall?.functionCalls?.length) {
          Promise.all(message.toolCall.functionCalls.map(async call => {
            let result;
            try {
              if (call.name === 'notify_owner') {
                const keyword = String(call.args?.keyword || '').trim().normalize('NFKC').toLocaleLowerCase();
                const expectedKeyword = String(settings.alertKeyword || '').trim().normalize('NFKC').toLocaleLowerCase();
                if (!expectedKeyword || keyword !== expectedKeyword) result = { sent: false, error: 'The alert keyword did not match. Ask the caller to repeat it.' };
                else { await sendWhatsappAlert({ caller, keyword: String(call.args?.keyword || '').slice(0, 80), message: String(call.args?.message || '').slice(0, 1000) }); result = { sent: true }; }
              }
              else result = { error: 'Unknown tool' };
            } catch (error) { console.error('[WhatsApp]', error.message); result = { sent: false, error: 'Notification unavailable' }; }
            return { id: call.id, name: call.name, response: result };
            })).then(functionResponses => { if (ready && !closed) liveSession?.sendToolResponse({ functionResponses }); });
        }
      },
      onerror(error) { console.error('[Gemini Live]', error?.message || error); },
      onclose(event) { closed = true; if (event?.reason) console.info('[Gemini Live closed]', event.reason); }
    }
  });
  connected.then(session => { liveSession = session; ready = true; if (closed) session.close(); }).catch(error => console.error('[Gemini Live]', error.message));
  return {
    isReady: () => ready,
    sendAudio(audio) { if (ready && liveSession && !closed) liveSession.sendRealtimeInput({ audio }); },
    close() { closed = true; liveSession?.close(); }
  };
}

server.on('upgrade', (req, socket, head) => {
  if (req.url !== '/voice/stream' || !twilioSignatureOk({ headers: req.headers, body: {} }, '/voice/stream/')) {
    socket.write('HTTP/1.1 403 Forbidden\r\n\r\n'); socket.destroy(); return;
  }
  streams.handleUpgrade(req, socket, head, ws => streams.emit('connection', ws, req));
});
streams.on('connection', twilioSocket => {
  let streamSid = '', caller = '', callSid = '', gemini;
  const entry = { from: '', startedAt: new Date().toISOString(), status: 'جارية' };
  recentCalls.push(entry);
  if (recentCalls.length > 100) recentCalls.shift();
  twilioSocket.on('message', raw => {
    try {
      const event = JSON.parse(raw.toString());
      if (event.event === 'start') {
        streamSid = event.start.streamSid;
        caller = event.start.customParameters?.caller || '';
        callSid = event.start.customParameters?.callSid || event.start.callSid || '';
        entry.from = caller;
        gemini = startGeminiSession(twilioSocket, streamSid, caller);
      } else if (event.event === 'media' && gemini?.isReady()) {
        gemini.sendAudio({ data: decodeMulawToPcm16k(event.media.payload).toString('base64'), mimeType: 'audio/pcm;rate=16000' });
      } else if (event.event === 'stop') entry.status = 'انتهت';
    } catch (error) { console.error('[Media stream]', error.message); }
  });
  twilioSocket.on('close', () => { entry.status = 'انتهت'; entry.durationEndedAt = new Date().toISOString(); gemini?.close(); });
  twilioSocket.on('error', error => console.error('[Twilio stream]', error.message));
  void callSid;
});

app.use((error, _req, res, _next) => { console.error(error); res.status(500).json({ error: 'Internal server error' }); });
await loadSettings();
const port = Number(process.env.PORT || 3000);
server.listen(port, '0.0.0.0', () => console.info(`Salim assistant listening on ${port}`));
