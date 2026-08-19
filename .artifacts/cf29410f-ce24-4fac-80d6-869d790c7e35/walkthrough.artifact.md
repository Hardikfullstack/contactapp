# Walkthrough - Fixing Unresolved Reference Error

I fixed the build error `Unresolved reference 'rememberInfiniteTransition'` in `SwipeUpCallButton.kt`.

## Changes Made

### UI Components

#### [SwipeUpCallButton.kt](file:///C:/Users/01/AndroidStudioProjects/ContactApp/app/src/main/java/com/example/contactapp/ui/components/SwipeUpCallButton.kt)

- Added missing import for `androidx.compose.animation.core.rememberInfiniteTransition`.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin` and the build finished successfully.

```
$ ./gradlew :app:compileDebugKotlin
BUILD SUCCESSFUL in 2s
```