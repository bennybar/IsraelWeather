package com.bennybar.israelweather

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.bennybar.israelweather.data.Api
import com.bennybar.israelweather.data.OpenMeteo
import com.bennybar.israelweather.data.RainRadar
import com.bennybar.israelweather.data.SettingsStore
import com.bennybar.israelweather.data.WeatherRepository
import com.bennybar.israelweather.location.LocationResolver
import com.bennybar.israelweather.widget.PaletteWorker
import com.bennybar.israelweather.widget.RefreshWorker
import com.bennybar.israelweather.widget.Widgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Global container (manual DI, like Ilay): `app.repo`, `app.settings`, … */
lateinit var app: AppContainer
    private set

class WeatherApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        app = AppContainer(this)
        RefreshWorker.schedule(this, app.settings.value.refreshMinutes)
        PaletteWorker.schedule(this)
        // A short delay: right after an install or update, Android is still reloading
        // the widget list and would drop previews published meanwhile.
        app.scope.launch {
            delay(5_000)
            Widgets.publishPreviews(this@WeatherApp)
        }
    }

    /** Coil shares our OkHttp client (and its User-Agent, which OSM's tile servers require). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { app.http })) }
            .build()
}

class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Identifies us to IMS-backed services and OpenStreetMap's tile servers (their policy requires it). */
    val userAgent = "IsraelWeather/${BuildConfig.VERSION_NAME} (Android; github.com/bennybar/IsraelWeather)"

    val http: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 30L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build()) }
        .build()

    val settings = SettingsStore(context)
    val api = Api(http, json, BuildConfig.PROXY_URL)
    val openMeteo = OpenMeteo(http, json)
    val rainRadar = RainRadar(http)
    val repo = WeatherRepository(context, api, openMeteo, settings, json, scope)
    val location = LocationResolver(context)
}
