## Service Managers & Android Components (`service/`)

Real files: `CallManager`, `ContactCallService` (InCallService), `ContactCallScreeningService`
(CallScreeningService), `FakeCallConnectionService`, `FakeCallManager`, `FakeCallReceiver`,
`FakeCallActionReceiver`, `FakeCallActiveNotification`, `FakeCallRingtonePlayer`, `BootReceiver`,
`AfterCallReceiver`, `AutoReplyManager`, `CallAnnouncerManager`, `CallNotificationManager`,
`CallReminderActionReceiver`, `CallReminderReceiver`, `CallReminderScheduler`, `FlashAlertManager`,
`ReminderReceiver`, `ShakeDetectionService`, `ShakeWatchdogReceiver`, `ShakeWatchdogScheduler`,
`SpamManager`.

### Rules
- `object` singletons (not Hilt-bound) for telecom/notification managers — consumed directly as
  `CallManager.answerCall()`, never `@Inject`ed
- `CallManager` is the single source of truth for call state: exposes `currentCall`/`callState`,
  `secondaryCall`/`secondaryCallState` as `StateFlow`s that `InCallActivity`/`InCallScreen` collect;
  it also owns the real call-waiting operations (`answerSecondaryCall`, `rejectSecondaryCall`,
  `swapCalls`) using `Call.hold()/unhold()/answer()/reject()`
- Android SDK usage (Telecom `Call`/`Connection` APIs, `NotificationManager`, `PowerManager`,
  `BroadcastReceiver`) is the norm here and expected — this is the layer that talks to the platform
- `ContactCallService` gates disruptive behavior (Flash Alert blinking, Call Announcer TTS) to the
  **primary** ringing call only (`isFirstCall` check) — a genuine second/call-waiting call must not
  re-trigger those
- Lifecycle-aware: receivers/services register and unregister on the appropriate callbacks; several
  managers (`FlashAlertManager`, etc.) are told to stop explicitly rather than relying on GC

### Creating a New Service Manager
```kotlin
object MyServiceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun start(context: Context) { /* register, listen, dispatch */ }
    fun stop(context: Context) { /* cleanup, unregister receivers */ }
}
// Consume via MyServiceManager.start(...) — never @Inject
```

### Known Technical Debt
- A few older files use raw `Thread`/`Thread.sleep` instead of a coroutine `delay()` — don't copy
  this pattern into new code, prefer the `CoroutineScope` approach above

### Anti-Patterns
- ❌ Trying to `@Inject` one of these singletons — they're consumed as `Foo.bar()`
- ❌ Letting a second/call-waiting call re-trigger Flash Alert or Call Announcer — gate on "is this the primary call" the way `ContactCallService` does
- ❌ Blocking the main thread in a receiver/service callback — dispatch to `Dispatchers.IO`
