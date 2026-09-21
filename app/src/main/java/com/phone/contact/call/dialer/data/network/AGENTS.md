## Retrofit / Network Layer (`data/network/`)

Small — one remote endpoint, used to fetch this app's ad/config panel data.

### Real Shape
- `ApiClient.kt` — `object`, not DI-bound. Builds its own `OkHttpClient` (logging interceptor gated
  on `BuildConfig.DEBUG`) and `Retrofit` (base URL `https://panel.aavakar.com/`,
  `kotlinx.serialization` converter). Exposes `suspend fun fetchAppConfig(): AppResponse`, which
  POSTs the app's ad-panel package identifier (`API_PACKAGE_NAME`, `"Contact_1.0.0"`)
- `ApiService.kt` — single method: `@Multipart @POST("api/getApp") suspend fun getAppData(@Part("package_name") ...): AppResponse`
- Consumed only from `viewmodel/AppConfigViewModel.kt`, which turns the response into ad-unit-ID
  state the rest of the app (mainly `ads/`) reads

### Adding a New Endpoint
```kotlin
// ApiService.kt
@GET("api/newEndpoint") suspend fun fetchThing(): ThingDto

// ApiClient.kt
suspend fun fetchThing(): ThingDto = apiService.fetchThing()
```

### Anti-Patterns
- ❌ Calling `ApiClient` directly from a Composable — go through a ViewModel
- ❌ Adding Room access here — this package is network-only
