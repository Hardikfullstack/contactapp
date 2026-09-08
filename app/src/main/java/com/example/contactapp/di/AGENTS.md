## Hilt Dependency Injection (`di/`)

Only two modules, both `@InstallIn(SingletonComponent::class)`:

- `DatabaseModule.kt` — an `object` (not `abstract class`, since it only has `@Provides`, no
  `@Binds`): builds the Room `AppDatabase` via `Room.databaseBuilder(...).fallbackToDestructiveMigration(dropAllTables = false).build()`, then provides `RecycleBinDao`/`ReminderDao` off of it
- `RepositoryModule.kt` — an `abstract class` with `@Binds` for the two repositories
  (`ContactRepositoryImpl → ContactRepository`, `CallLogRepositoryImpl → CallLogRepository`), plus a
  companion `@Provides fun provideContentResolver(@ApplicationContext context): ContentResolver`

`service/` singletons (`CallManager`, `FlashAlertManager`, etc.) are **not** Hilt-bound — they're
plain Kotlin `object`s consumed directly (`CallManager.answerCall()`), so there is nothing to wire
for them here.

### Adding a New Repository Binding
```kotlin
// in RepositoryModule.kt
@Binds
@Singleton
abstract fun bindMyRepository(impl: MyRepositoryImpl): MyRepository
```

### Adding a New Room DAO Provider
```kotlin
// in DatabaseModule.kt
@Provides
@Singleton
fun provideMyDao(database: AppDatabase): MyDao = database.myDao()
```

### Adding a New ViewModel
No module needed — `@HiltViewModel` + constructor injection is enough, whether the ViewModel lives
in `viewmodel/` (rare — only `AppConfigViewModel` does today) or in `ui/features/<feature>/` (the
normal place for a per-screen ViewModel).

### Anti-Patterns
- ❌ Adding a new top-level module per feature — this app keeps DI to two modules; extend the existing ones
- ❌ Trying to `@Inject` a `service/` singleton — those are consumed as `Foo.bar()`, not injected
- ❌ Providing a raw `Context` where `@ApplicationContext Context` is what's actually needed
