## Domain Models (`domain/model/`)

Real files: `Contact.kt`, `DetailedContact.kt`, `CallLogModel.kt`, `DialKey.kt`.

### Rules
- Plain `data class`, `@Immutable` (Compose stability annotation) where the model flows through
  Compose state — see `Contact`:
  ```kotlin
  @Immutable
  data class Contact(
      val id: String,
      val name: String,
      val number: String,
      val isFavorite: Boolean = false,
      val photoUri: String? = null,
      val isBlocked: Boolean = false
  )
  ```
- `val` properties only, sensible defaults so call sites don't need to fill in every field
- No `android.*` imports beyond `androidx.compose.runtime.Immutable` (a Compose annotation, not a
  platform dependency) — these types must stay easy to construct in tests and previews

### Adding a New Domain Model
```kotlin
@Immutable
data class MyModel(
    val id: String,
    val displayName: String
)
```

### Anti-Patterns
- ❌ Putting Room (`@Entity`) or Retrofit (`@Serializable`) annotations directly on these — map from `data/local`/`data/network` types instead
- ❌ Mutable (`var`) properties
- ❌ Skipping `@Immutable` on a model that's read from Compose `State`/`StateFlow` — Compose can't skip recomposition without it
