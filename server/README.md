# سليم — مساعد المكالمات الذكي

مشروع Node.js جاهز للرفع إلى GitHub والتشغيل على سيرفر عام. يستقبل مكالمات Twilio Voice، يبث الصوت ثنائي الاتجاه إلى Gemini Live، يرد بالعربية، ويستطيع إرسال تنبيه واتساب عند طلب المتصل تمرير رسالة شخصية إلى سعيد.

> لوحة التحكم لا تحتاج Gemini key في المتصفح. مفاتيح Gemini وTwilio وWhatsApp تبقى في ملف `.env` على السيرفر. لا ترفع `.env` أو مجلد `data/` إلى GitHub.

## الوظائف

- لوحة إدارة عربية محمية بكلمة مرور، تحفظ الأرقام والتعليمات على السيرفر.
- Webhook لمكالمات Twilio مع التحقق من توقيع الطلب.
- بث Twilio Media Streams ثنائي الاتجاه وربطه بجلسة Gemini Live.
- تحويل صوت الهاتف G.711 μ-law/8 kHz إلى PCM/16 kHz للمدخل، ثم صوت Gemini PCM/24 kHz إلى μ-law/8 kHz للمكالمة.
- رسالة تعريف فورية بصوت Gemini. لتشغيل تسجيل صوتي فعلي، ارفع MP3 عامًّا وآمنًا واضبط `GREETING_AUDIO_URL`.
- أداة واتساب ترسل رقم المتصل، وكلمة التنبيه، وملخص الطلب بعد موافقة المتصل.
- Docker Compose للنشر خلف reverse proxy يدعم HTTPS وWSS.

## ما تحتاجه قبل التفعيل

1. **رقم/خدمة اتصالات تدعم Twilio Programmable Voice وMedia Streams في بلدك.** تحقّق من توفر الأرقام ومتطلبات التسجيل محليًا. رقمك الحالي أُضيف إلى لوحة الإدارة كرقمك الشخصي؛ هذا لا يعني أنه رقم Twilio. إذا كانت شركة الخط تدعم تحويل المكالمات، يمكن تحويل المكالمات الواردة إلى رقم Twilio. يمكن أيضًا توصيل SIP/PBX مدعوم.
2. مفتاح Gemini API لديه وصول إلى Live API والموديل المحدد. هذا المشروع يستخدم `gemini-3.8-live` افتراضيًا؛ غيّره إلى موديل متاح لحسابك عند الحاجة.
3. حساب WhatsApp Business Platform/Cloud API، رقم إرسال مسجل، ورمز وصول.
4. استضافة عامة مع HTTPS للـwebhook وWSS لتدفق الصوت. لا يكفي تشغيل السيرفر على الكمبيوتر المحلي إلا للاختبار عبر نفق آمن مؤقت.

## التكلفة: ما هو مجاني وما قد يُحاسب

- **الشفرة والتشغيل على كمبيوترك:** لا توجد رسوم ترخيص لهذا المشروع أو Node.js/Docker؛ يبقى استهلاك الكهرباء والإنترنت على خطك.
- **Gemini Live:** صفحة Google الحالية تعرض حصة مجانية للموديل `gemini-3.8-live` ضمن حدودها، ثم تُطبق الأسعار عند تجاوزها أو عند تغيير الخطة/الموديل. الحصة والحدود قد تتغير. بيانات الاستخدام في المستوى المجاني قد تُستخدم لتحسين منتجات Google؛ راجع [الأسعار وشروط الحساب](https://ai.google.dev/gemini-api/docs/pricing) قبل تمرير مكالمات حقيقية أو بيانات خاصة.
- **المكالمات:** لا أقدر أضمنها مجانًا. [صفحة Twilio لمصر](https://www.twilio.com/en-us/voice/pricing/eg) تعرض Media Streams بسعر $0.0044 لكل دقيقة، كما أن الأرقام الدولية تبدأ من $1.15 شهريًا إذا احتجت رقمًا. تكلفة الرقم والمكالمة وتحويل المكالمات من شركة خطك تعتمد على التوفر والتوجيه الفعليين.
- **واتساب:** رسالة التنبيه تستخدم قالب WhatsApp Business معتمدًا. قد تنطبق رسوم لكل رسالة حسب الفئة والبلد؛ راجع [أسعار WhatsApp Business](https://developers.facebook.com/docs/whatsapp/pricing) في حسابك قبل التفعيل.
- **الاستضافة:** تشغيل السيرفر على كمبيوترك يتجنب رسوم استضافة شهرية، لكنه يحتاج اتصالًا ثابتًا وعنوان HTTPS/WSS عامًّا. النفق المجاني مناسب للتجارب وقد يتغير رابطه؛ لا تعتمد عليه لاستقبال مكالمات موثوق.

**الخلاصة:** يمكن بدء تجربة بتكلفة منخفضة، لكن نسخة هاتفية إنتاجية بالكامل لا يمكن ضمان أنها صفر تكلفة. لا تفعّل المكالمات قبل مراجعة حدود Gemini وأسعار الاتصالات وWhatsApp.

## التشغيل محليًا

المطلوب Node.js 20.11 أو أحدث.

```powershell
Copy-Item .env.example .env
npm ci
npm start
```

عدّل `.env` وضع كلمة مرور إدارة طويلة ومفتاح Gemini. اترك `CALLS_ENABLED=false` أثناء الإعداد. افتح `http://localhost:3000`، ثم سجّل الدخول باستخدام اسم مستخدم اختياري وكلمة مرور `ADMIN_PASSWORD`.

## متغيرات البيئة

| المتغير | الغرض |
| --- | --- |
| `ADMIN_PASSWORD` | حماية لوحة الإدارة وواجهات إعدادها |
| `PUBLIC_BASE_URL` | العنوان العام `https://...`؛ يجب أن يكون هو نفس عنوان Twilio webhook |
| `GEMINI_API_KEY` | مفتاح Gemini الخادمي |
| `GEMINI_MODEL` | موديل Gemini Live، الافتراضي `gemini-3.8-live` |
| `GEMINI_VOICE` | اسم صوت Gemini، الافتراضي `Kore` |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` | التحقق من توقيع Twilio |
| `GREETING_AUDIO_URL` | اختياري: رابط MP3 عام لرسالة مسجلة قبل رد Gemini |
| `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` | بيانات WhatsApp Cloud API |
| `WHATSAPP_OWNER_NUMBER` | رقم واتساب المستلم بصيغة دولية؛ يمكن تعديله من اللوحة |
| `WHATSAPP_ALERT_TEMPLATE` | اسم قالب واتساب المعتمد، الافتراضي `salim_personal_alert` |
| `WHATSAPP_TEMPLATE_LANGUAGE` | رمز لغة القالب المعتمد، الافتراضي `ar` |
| `WHATSAPP_GRAPH_VERSION` | إصدار Graph API مدعوم ومفعّل في تطبيق Meta |
| `CALLS_ENABLED` | قيمة بداية فقط؛ التحكم اليومي متاح من لوحة الإدارة |

أنشئ قالب WhatsApp معتمدًا باسم `salim_personal_alert` يحتوي ثلاثة مواضع نصية بالترتيب: رقم المتصل، كلمة التنبيه، وملخص الرسالة. استبدل الاسم/اللغة في `.env` إذا اختلفا، وحدد إصدار Graph API مدعومًا لحساب Meta. يتطلب الإرسال خارج نافذة خدمة WhatsApp قالبًا معتمدًا وموافقة المستلم على استقبال الرسائل.

## ربط Twilio

1. انشر التطبيق على استضافة عامة خلف proxy يمرر WebSocket، مع TLS.
2. اضبط `PUBLIC_BASE_URL` على أصل الموقع العام، مثل `https://calls.example.com`.
3. في إعداد رقم Twilio الصوتي، عيّن **A call comes in** إلى `POST https://calls.example.com/voice/incoming`.
4. افتح `https://calls.example.com/health` للتأكد من وصول السيرفر، ثم أدخل بيانات Gemini وTwilio وWhatsApp Business، ورقم واتساب المستلم، وإصدار Graph API في `.env`.
5. رقم سعيد المسجل في اللوحة هو رقم خطه الشخصي؛ سجّل فيه أيضًا أرقام Twilio التي خصصتها عند استلامها. قائمة اللوحة تحفظ سجل الأرقام فقط؛ تعيين رقم Twilio للـwebhook يتم من لوحة Twilio. لتحويل الخط الشخصي، اطلب من شركة الخط تفعيل تحويل المكالمات إلى رقم Twilio.
6. فعّل استقبال المكالمات من اللوحة، ثم اتصل برقم Twilio واختبر المحادثة. يبدأ مسار الصوت على `wss://calls.example.com/voice/stream` تلقائيًا.

Twilio يتطلب `WSS` للبث ثنائي الاتجاه. احتفظ بالتحقق من `X-Twilio-Signature` مفعّلًا؛ لا تفتح واجهة المكالمات للعامة دون بيانات Twilio صحيحة.

## النشر بـ Docker

أنشئ `.env` على السيرفر من `.env.example`، ثم:

```sh
docker compose up -d --build
```

اربط reverse proxy بـ `127.0.0.1:3000` مع دعم WebSocket وترقية الاتصال، وأصدر شهادة TLS. منفذ التطبيق مربوط محليًا فقط عمدًا.

## GitHub

```sh
git init
git add .
git commit -m "Initial Salim call assistant"
git branch -M main
git remote add origin https://github.com/YOUR-USER/salim-call-assistant.git
git push -u origin main
```

تحقق قبل الرفع أن `.env` وبيانات التشغيل غير موجودة في قائمة الملفات. `.gitignore` يستثنيها.

## ملاحظات أمن وتشغيل

- أنشئ كلمة مرور إدارة عشوائية قوية، واحمِ `.env` ونسخ قاعدة الإعدادات.
- واجهة المكالمات تعتمد على Gemini Live؛ تختلف الإتاحة والحدود حسب حساب Google والموديل. الجلسات الحية لها مدة محدودة لدى Gemini؛ راجع وثائق الموديل عند توسيع التشغيل.
- WhatsApp Cloud API قد يرفض الرسالة إذا لم يكن القالب معتمدًا أو إذا كانت اللغة/رقم الإرسال غير مطابقين.
- سجل المكالمات المعروض في اللوحة ذاكرة مؤقتة فقط. لا يسجل النظام الصوت ولا يحتفظ بتفريغ المحادثة. اجعل المتصل على علم بأنه يتحدث إلى مساعد إلكتروني، واحصل على موافقته قبل إرسال ملخصه.
- لم يُنشر المشروع إلى GitHub ولم يُختبر بمكالمة فعلية بعد، لأن مفاتيح الحسابات وعنوان الاستضافة غير متوفرة.

## المراجع الرسمية

- [Gemini Live API — JavaScript SDK](https://ai.google.dev/gemini-api/docs/live-api/get-started-sdk)
- [Twilio Media Streams](https://www.twilio.com/docs/voice/media-streams)
- [Twilio WebSocket message formats](https://www.twilio.com/docs/voice/media-streams/websocket-messages)
- [Twilio request validation](https://www.twilio.com/docs/usage/security)
- [WhatsApp Cloud API](https://developers.facebook.com/docs/whatsapp/cloud-api)
