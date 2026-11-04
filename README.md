# AlertAlarm ⏰

Ek Android alarm app jo **scheduled time par kaam bolkar yaad dilata hai** aur **end time tak band nahi hota**.

Example: "1 tarikh, 11:00 AM se 11:30 AM: Spring Security section padhna"
→ 11:00 baje alarm bajega, har 20 second mein bolega *"Attention please. It is time for your task: Study the Spring Security section"*, aur 11:30 tak koi band nahi kar payega.

## Features
- Kaam, date, start time, aur **end time chuno ya minutes/seconds likho** (dono sync rehte hain; max 12 ghante)
- Poora app English mein
- Start time par **full-screen alarm**, lock screen par bhi
- Alarm tone loop mein bajti hai, beech-beech mein **kaam English mein bolkar batata hai**
- **Lock:** end time se pehle
  - "Band karo" button disabled rehta hai
  - Back button kaam nahi karta
  - Volume kam karo toh 5 second mein wapas full ho jaata hai
  - App ko recent apps se hatao, tab bhi bajta rehta hai
  - Bajte hue alarm ko delete nahi kar sakte
- Phone restart ho jaaye toh alarms wapas set ho jaate hain (aur agar ringing ka time chal raha ho toh dobara bajne lagta hai)
- End time aate hi apne aap band

## Kaise chalayein
1. **Android Studio** install karo (latest version).
2. `AlertAlarm` folder unzip karo → Android Studio → **File → Open** → yeh folder chuno.
3. Android Studio khud Gradle aur Android SDK download karega (pehli baar 5–10 minute). Agar "Gradle wrapper missing" jaisa message aaye, toh **OK / Use Gradle wrapper** chuno.
4. Phone par **Developer options → USB debugging** on karo, USB se laptop se jodo.
5. Upar phone select karo aur **▶ Run** dabao. App phone par install ho jaayega.

## Pehli baar app kholne par (zaroori!)
App upar ek peela banner dikhayega. Ek-ek karke saari permissions do:
1. **Notifications** → Allow
2. **Alarms & reminders** → Allow (Android 12+)
3. **Full-screen alarm** → Allow (Android 14+)
4. **Battery optimization** → "Don't optimize" / Allow

**Xiaomi / Redmi / Oppo / Vivo / Realme phones par extra:**
- Settings → Apps → AlertAlarm → **Autostart: ON**
- Battery saver → AlertAlarm → **No restrictions**

Yeh nahi kiya toh yeh phones apne aap alarm ko rok dete hain.


## Pehla test
1. Ek kaam add karo jo **2 minute baad** shuru ho aur **3 minute** tak baje.
2. Phone lock kar do.
3. Time par alarm khulna chahiye, bolna chahiye, aur 3 minute tak band nahi hona chahiye.

## Seemayein (honest)
Android mein koi bhi app 100% "unstoppable" nahi ho sakta. Yeh cheezein alarm rok sakti hain:
- Settings → Apps → **Force stop** ya app uninstall karna
- Phone **switch off** karna (on karne par alarm wapas bajne lagega agar time bacha ho)
- Kuch phones ka aggressive battery saver (upar wali settings karo)

## Code structure (Java)
| File | Kaam |
|---|---|
| `MainActivity` | Kaamon ki list, permissions ka banner |
| `AddReminderActivity` | Naya kaam: date, start time, end time |
| `AlarmScheduler` | `AlarmManager.setAlarmClock` se exact alarm set karna |
| `AlarmReceiver` | Time hone par service start karna |
| `AlarmService` | Foreground service: tone, bolna (TextToSpeech), vibration, volume lock, end time par khud band |
| `RingingActivity` | Full-screen lal screen, countdown, locked stop button |
| `BootReceiver` | Phone restart ke baad alarms wapas set karna |
| `ReminderStore` | Kaam phone mein save karna (SharedPreferences + JSON) |

## Android Studio nahi hai? GitHub se APK banao
1. GitHub par ek **naya repository** banao (naam: `AlertAlarm`).
2. **Add file → Upload files** se is folder ki saari files aur folders upload karo (`.github` folder bhi), aur **Commit changes** dabao.
3. Repo ke **Actions** tab mein "Build APK" chalega (3–5 minute). Hara ✓ aane ka wait karo.
4. Repo ke **Releases** section mein `AlertAlarm.apk` milega. Ise **phone ke browser** se kholo aur download karo.
5. Phone par APK kholo → "Install unknown apps" ki permission do → **Install**.

Code mein kuch badalna ho toh file GitHub par edit karke commit karo, naya APK apne aap ban jaayega.
"# AlertAlarm" 
