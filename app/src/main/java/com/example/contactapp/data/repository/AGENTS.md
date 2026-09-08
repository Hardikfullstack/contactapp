## Data Repository Implementations (`data/repository/`)

Only two files: `ContactRepositoryImpl.kt` and `CallLogRepositoryImpl.kt`. Both are primarily
**`ContentResolver`/`ContactsContract` wrappers**, not Room repositories — Room (`data/local/`) is
only touched here for the recycle-bin side of `ContactRepositoryImpl`.

### Rules
- Each `*RepositoryImpl` implements exactly one `domain/repository/` interface, injected via
  constructor and bound with `@Binds` in `di/RepositoryModule.kt`
- Reads from `ContentResolver` are exposed as `Flow` via a shared `callbackFlow` pattern
  (`contactFlow{}` in `ContactRepositoryImpl`, `callLogFlow{}` in `CallLogRepositoryImpl`) that
  registers a `ContentObserver` on the relevant `ContactsContract`/`CallLog` URI and re-queries on
  every change, emitting once immediately on subscribe
- Cursor queries wrap `contentResolver.query(...)` in try/catch and fail closed (return
  empty/false) rather than propagating — a malformed cursor should never crash a screen
- `ContactRepositoryImpl` also owns `RecycleBinDao` (soft-delete/restore for contacts) and reads/
  writes the contact's system `CUSTOM_RINGTONE` column directly — that's what makes a per-contact
  ringtone actually apply to real incoming calls, not just something tracked locally
- `CallLogRepositoryImpl` additionally consults `LocalBlockManager` to annotate/filter blocked numbers

### Adding a New Repository Implementation
```kotlin
class MyRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    @ApplicationContext private val context: Context
) : MyRepository {
    override fun fetchThings(): Flow<List<Thing>> = callbackFlow {
        // register ContentObserver, trySend(query()), awaitClose { unregister }
    }.flowOn(Dispatchers.IO)
}
```
Then bind it in `di/RepositoryModule.kt`:
```kotlin
@Binds @Singleton abstract fun bindMyRepository(impl: MyRepositoryImpl): MyRepository
```

### Anti-Patterns
- ❌ Querying `ContentResolver` outside `Dispatchers.IO`
- ❌ Letting a cursor exception propagate to the UI — catch and return a safe empty/false default
- ❌ Duplicating the `callbackFlow`+`ContentObserver` boilerplate — factor it the way `contactFlow{}`/`callLogFlow{}` already do
