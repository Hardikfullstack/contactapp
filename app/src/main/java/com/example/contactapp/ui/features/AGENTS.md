## UI Features Layer (`ui/features/<feature>/`)

20 feature packages today: `aftercall`, `analytics`, `announcer`, `autoreply`, `call`,
`callreminder`, `callthemes`, `contacts`, `fakecall`, `favorites`, `flash`, `history`, `keypad`,
`onboarding`, `recents`, `ringtone`, `settings`, `splash`, `tools`, `wallpaper`.

### Rules
- **The ViewModel for a feature lives in the same package as its screen**, e.g.
  `ui/features/contacts/ContactsScreen.kt` + `ui/features/contacts/ContactsViewModel.kt` — there is
  no separate top-level `viewmodel/` home for these (that package holds only the shared
  `AppConfigViewModel`)
- Screen composables are named `<Feature>Screen`, take navigation as callback params
  (`onBack: () -> Unit`, `onXClick: (...) -> Unit`), and default their ViewModel param to
  `viewModel: XViewModel = hiltViewModel()` — never construct a ViewModel manually
- Navigation wiring itself (the `composable(route) { ... }` block) lives in `ui/navigation/`, not here
- UI state is a `data class XUiState` (often defined in the same file as its ViewModel, not always
  split out), collected with `by viewModel.uiState.collectAsState()`
- Shared components used by more than one feature go in `ui/components/`, not duplicated per feature

### Creating a New Screen
```kotlin
// ui/features/myfeature/MyFeatureViewModel.kt
data class MyFeatureUiState(val isLoading: Boolean = false, val items: List<MyModel> = emptyList())

@HiltViewModel
class MyFeatureViewModel @Inject constructor(
    private val myRepository: MyRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyFeatureUiState())
    val uiState: StateFlow<MyFeatureUiState> = _uiState.asStateFlow()
}

// ui/features/myfeature/MyFeatureScreen.kt
@Composable
fun MyFeatureScreen(
    onBack: () -> Unit,
    viewModel: MyFeatureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // ...
}

// then wire composable(MainScreen.MyFeature.route) { MyFeatureScreen(...) } in ui/navigation/MainNavigation.kt
```

### Anti-Patterns
- ❌ Putting a new per-feature ViewModel in the top-level `viewmodel/` package instead of beside its screen
- ❌ Passing Room entities or `ContentResolver` cursors to Composables — map to a `domain/model/` type in the repository/ViewModel first
- ❌ A screen calling `NavController` directly instead of receiving a callback from `ui/navigation/`
- ❌ Suspend calls directly in a `@Composable` body — route through the ViewModel + `collectAsState`
