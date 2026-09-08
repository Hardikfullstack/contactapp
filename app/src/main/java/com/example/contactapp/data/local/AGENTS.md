## Room Database Layer (`data/local/`)

Room here backs only the **recycle bin** and **call reminders** — actual contacts and call logs are
read live from the system `ContentResolver` (`ContactsContract`), not stored in this database.

### Real Shape
- `AppDatabase` (`data/local/AppDatabase.kt`): `entities = [DeletedContactEntity::class, ReminderEntity::class]`, `version = 2`, `exportSchema = false`, `.fallbackToDestructiveMigration(dropAllTables = false)` — **no real `Migration` has been written yet**; acceptable pre-release since there's no shipped user data to preserve, but a proper migration is needed once this app has real users
- `dao/RecycleBinDao.kt`, `dao/ReminderDao.kt` — interface DAOs, provided as `@Singleton` via `di/DatabaseModule.kt`
- `entity/DeletedContactEntity.kt`, `entity/ReminderEntity.kt`

### Adding a New Entity + DAO
```kotlin
@Entity(tableName = "new_entities")
data class NewEntity(
    @PrimaryKey val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao interface NewEntityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: NewEntity)
    @Query("SELECT * FROM new_entities") suspend fun getAll(): List<NewEntity>
}
```
Then: add it to `AppDatabase`'s `entities = [...]`, add an `abstract fun newEntityDao(): NewEntityDao`,
bump `version`, **write a real `Migration`** (don't rely on `fallbackToDestructiveMigration` once
there are real users), and add a `@Provides fun provideNewEntityDao(db: AppDatabase)` to `DatabaseModule`.

### Anti-Patterns
- ❌ Assuming contacts/call-log live in Room — they don't, they're `ContentResolver` queries in `data/repository/`
- ❌ Adding another destructive-migration entity now that the app is closer to release — write a real `Migration`
- ❌ Business logic in DAOs — queries only
