# Implementation Plan - Step 5: Global Search

Implement a high-fidelity search feature for call logs and contacts, strictly following the Figma design.

## User Review Required

> [!IMPORTANT]
> - **Search Illustration**: The "No Result found" illustration from Figma is missing. I will use a placeholder `Icon` (e.g., `SearchOff`) or a themed `Box` until the asset is provided.
> - **Search Scope**: The search will implement a **comprehensive filter** that matches the user's query against both **Contact Names** and **Mobile Numbers**.
- **Matching Logic**: The filter will be case-insensitive and match if the query is contained anywhere within the name or the phone number.
> - **Navigation**: Clicking the search icon on the Recents screen will navigate to this new dedicated search screen.

## Proposed Changes

### UI Layer

#### [NEW] [SearchScreen.kt](file:///C:/Users/01/AndroidStudioProjects/ContactApp/app/src/main/java/com/example/contactapp/ui/features/recents/SearchScreen.kt)
- **Search Header**:
    - A pill-shaped, light-gray search bar.
    - Includes a Back button and a Clear (X) button.
    - Real-time filtering as the user types.
- **Results State**:
    - Displays a "Recent Calls" header.
    - Reuses the `CallItem` component for consistent styling.
- **Empty State**:
    - Displays the "No Result found." message with a placeholder illustration.

#### [NEW] [SearchViewModel.kt](file:///C:/Users/01/AndroidStudioProjects/ContactApp/app/src/main/java/com/example/contactapp/ui/features/recents/SearchViewModel.kt)
- Fetches all call logs from `CallLogRepository`.
- Exposes a filtered list based on the search query.
- Manages the query state.

#### [MODIFY] [MainNavigation.kt](file:///C:/Users/01/AndroidStudioProjects/ContactApp/app/src/main/java/com/example/contactapp/ui/navigation/MainNavigation.kt)
- Add the `search` destination to the `NavHost`.
- Ensure the bottom bar is hidden or the search screen covers it for a full-screen experience.

#### [MODIFY] [RecentsScreen.kt](file:///C:/Users/01/AndroidStudioProjects/ContactApp/app/src/main/java/com/example/contactapp/ui/features/recents/RecentsScreen.kt)
- Update the search icon's `onClick` to navigate to the search screen.

## Verification Plan

### Manual Verification
- **Real-time Filter**: Verify that typing "Pal" correctly shows "Pallavi" and other matching entries.
- **Empty State**: Type a random string (e.g., "xyz123") and verify the "No Result found." screen appears.
- **Back Navigation**: Ensure clicking the back arrow returns the user to the Recents screen.
- **Visual Accuracy**: Compare the search bar spacing, colors, and pill shape against the Figma mockup.
