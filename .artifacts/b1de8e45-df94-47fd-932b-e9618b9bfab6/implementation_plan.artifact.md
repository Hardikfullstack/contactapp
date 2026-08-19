# Implementation Plan - Enhanced Call Wallpaper Hub with Custom Color Picker

Expand the wallpaper collection, improve gallery selection, and introduce a **Custom Color Picker** to allow users to choose any background color they desire.

## Proposed Changes

### 1. Expanded Color Palette & State Management

#### [MODIFY] [CallWallpaperViewModel.kt](file:///C:/Users/01/AndroidStudioProjects/ContactApp/app/src/main/java/com/example/contactapp/ui/features/wallpaper/CallWallpaperViewModel.kt)
- **New Presets**: Add Indigo, Deep Red, Teal, Amber, Deep Purple, and Dark Gray.
- **Custom Color State**: Add `customColor: Color?` to `CallWallpaperUiState` to track the user's manually chosen color.

### 2. Custom Color Picker & Selection UI

#### [MODIFY] [CallWallpaperScreen.kt](file:///C:/Users/01/AndroidStudioProjects/ContactApp/app/src/main/java/com/example/contactapp/ui/features/wallpaper/CallWallpaperScreen.kt)
- **New "Custom Color" Action**:
    - Add a card next to "Gallery" that opens a **Color Picker Dialog**.
    - The dialog will feature a simplified grid of 20+ vibrant colors or a hue slider for total freedom.
- **Smart Grid Ordering**:
    - **Position 1**: "None (Solid)"
    - **Position 2**: **Active Gallery Image** (if selected)
    - **Position 3**: **Active Custom Color** (if selected)
    - **Followed by**: All presets.
- **Visual Polish**:
    - Update `WallpaperCard` to support `AsyncImage` (for gallery) and `Color` (for presets/custom).
    - Ensure the "Selected" checkmark is clearly visible on all card types.

### 3. Localization

- Add strings for "Custom Color", "Pick a Color", and "Recent Custom Color".

## Verification Plan

### Manual Verification
1.  **Custom Color Test**: Tap "Custom Color" and pick a unique shade (e.g., Lime Green).
    - **Observe**: Verify it appears in the grid and is marked as selected.
    - **Observe**: Verify the In-Call screen now uses this exact shade as its background.
2.  **Gallery Test**: Select an image. Verify it jumps to the top of the selection grid.
3.  **Persistence**: Verify your custom color choice is remembered after closing the app.
4.  **Dark Mode**: Ensure the color picker dialog and all new UI elements are perfectly themed.
