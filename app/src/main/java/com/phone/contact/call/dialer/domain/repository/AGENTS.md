## Domain Repository Interfaces (`domain/repository/`)

Two interfaces: `ContactRepository`, `CallLogRepository`. Each has exactly one implementation in
`data/repository/`.

### Rules
- Interface-only, one `android.*` import allowed where the shape genuinely needs it (`android.net.Uri`
  for photo/ringtone URIs) — otherwise domain stays platform-agnostic
- `Flow<T>` for anything that should live-update off a `ContentObserver` (e.g.
  `fetchContacts(): Flow<List<Contact>>`), plain `suspend fun` for one-shot reads/writes
  (`suspend fun saveContact(name: String, number: String)`)
- Doc comments matter here when a method has non-obvious real-world behavior — e.g.
  `ContactRepository.updateContactRingtone` is documented as writing the system `CUSTOM_RINGTONE`
  column specifically because that's the field Android's own ringer consults for real calls, not
  just app-local state

### Adding a New Repository Interface
```kotlin
interface MyRepository {
    fun fetchThings(): Flow<List<Thing>>
    suspend fun saveThing(thing: Thing)
}
```

### Anti-Patterns
- ❌ Implementing anything here — that belongs in `data/repository/`
- ❌ Returning Room entities or network DTOs from these signatures — domain models (`domain/model/`) only
- ❌ Adding unrelated methods to `ContactRepository`/`CallLogRepository` instead of a new focused interface
