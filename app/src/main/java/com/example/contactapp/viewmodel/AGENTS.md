## Top-Level ViewModels (`viewmodel/`)

This package holds exactly **one** file today: `AppConfigViewModel.kt` — app-wide remote
config/ad-unit-id state fetched via `data/network/ApiClient.fetchAppConfig()`. It is **not** where
per-screen ViewModels live.

### Rules
- Per-feature ViewModels (e.g. `ContactsViewModel`) live beside their screen in
  `ui/features/<feature>/`, not here — put a new one here only if it's genuinely app-wide/shared
  across many unrelated features, the way `AppConfigViewModel` is
- `@HiltViewModel` with constructor injection; deps are `domain/repository/` interfaces or, for
  `AppConfigViewModel`, the network layer directly
- Exposes `StateFlow<UiState>` to UI; mutations via direct `_uiState.value = _uiState.value.copy(...)` (this codebase does not use a `MutableStateFlow.update {}` convention consistently — matching existing call sites in the same file is more important than a single "correct" style)
- I/O dispatched inside `viewModelScope.launch`, never in Composables

### Creating a New Feature ViewModel (goes in `ui/features/<feature>/`, not `viewmodel/`)
```kotlin
@HiltViewModel
class MyScreenViewModel @Inject constructor(
    private val myRepo: MyRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyScreenUiState())
    val uiState: StateFlow<MyScreenUiState> = _uiState.asStateFlow()

    init { loadData() }
    private fun loadData() = viewModelScope.launch {
        myRepo.fetchThings().collect { things ->
            _uiState.value = _uiState.value.copy(things = things)
        }
    }
}
```

### Anti-Patterns
- ❌ Adding a new per-feature ViewModel to this top-level package — put it in `ui/features/<feature>/` instead
- ❌ Direct Android `Context` usage — go through a repository or a `service/` singleton
- ❌ Exposing `MutableStateFlow` publicly — expose as `StateFlow`, mutate internally
