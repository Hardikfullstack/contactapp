# Implementation Plan - Fix 'onDestroyed' overrides nothing error

The user is experiencing a build error because `FakeCallConnection` attempts to override `onDestroyed()`, which is not a method of `android.telecom.Connection`. I will move the cleanup logic (purging call log entries) to the existing termination methods to ensure it still runs when a fake call ends.

## User Review Required

> [!IMPORTANT]
> The `onDestroyed()` method was likely intended to be a lifecycle callback, but it doesn't exist in the `Connection` class. I will move its logic to the methods that actually handle the connection's termination (`onReject`, `onDisconnect`, and the UI-driven `endFromUi`).

## Proposed Changes

### Telecom Service Component

#### [MODIFY] [FakeCallConnectionService.kt](file:///C:/Users/01/AndroidStudioProjects/ContactApp/app/src/main/java/com/example/contactapp/service/FakeCallConnectionService.kt)
- Remove the invalid `override fun onDestroyed()` from `FakeCallConnection`.
- Add a helper method `destroyAndCleanup(cause: DisconnectCause)` to `FakeCallConnection` that:
    1. Sets the disconnect cause.
    2. Calls `destroy()`.
    3. Triggers the call log purging logic (using a `Handler` to delay it slightly).
- Update `onReject()` and `onDisconnect()` to use this new helper method.

#### [MODIFY] [FakeCallManager.kt](file:///C:/Users/01/AndroidStudioProjects/ContactApp/app/src/main/java/com/example/contactapp/service/FakeCallManager.kt)
- Update `endFromUi()` to call the new `destroyAndCleanup()` method on the connection instead of calling `setDisconnected` and `destroy` manually.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify that the project builds without errors.

### Manual Verification
- N/A (This is a fix for a compilation error).
