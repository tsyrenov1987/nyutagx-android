/*
 * Нютаг — транспорт «Кильватер» под Rust SDK.
 *
 * farvater-core (Go, gomobile) держит SOCKS5 на 127.0.0.1 с одноразовым логином/паролем и везёт каждый поток
 * в kilvaterd на входе; оттуда поток идёт по stream-релею и WireGuard в ядро. Rust SDK ходит только через этот
 * SOCKS (схема socks5h — имя ядра разрешает вход, публичного DNS у него нет) и доверяет только нашему CA.
 */

package io.element.android.libraries.matrix.impl.nyutag

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.farvater.core.mobile.Mobile
import timber.log.Timber
import java.net.ServerSocket
import java.security.cert.CertificateFactory

object NyutagTunnel {
    /**
     * Путь до входа (строка `kilvater://<ключ>@host:port?…`) — ключ Нютага, не ключ флота Farvater.
     * В репозитории его нет: файл [CATALOGUE_ASSET] лежит в `.gitignore`, рядом — `catalogue.txt.example`.
     * Без файла туннель не поднимется и приложение не подключится.
     */
    private const val CATALOGUE_ASSET = "nyutag/catalogue.txt"

    private fun bundledCatalogue(context: Context): String? = try {
        context.assets.open(CATALOGUE_ASSET).bufferedReader().use { it.readText() }.trim().ifEmpty { null }
    } catch (e: java.io.IOException) {
        null
    }

    /** Последний состав входов с ядра ([NyutagCatalogue]); меняется без выпуска сборки. */
    private const val CATALOGUE_PREFS = "nyutag_catalogue"
    private const val CATALOGUE_KEY = "json"
    private const val CATALOGUE_PATH = "/_nyutag/v1/catalogue"

    private fun catalogue(context: Context): String? {
        val saved = context.getSharedPreferences(CATALOGUE_PREFS, Context.MODE_PRIVATE).getString(CATALOGUE_KEY, null)
        return NyutagCatalogue.compose(bundledCatalogue(context), NyutagCatalogue.parse(saved))
    }

    @Volatile private var catalogueRefreshStarted = false

    /** Повторы обновления каталога, если ядро не ответило: через 1, 2, 4 и 8 минут (старт без сети — обычное дело). */
    private const val CATALOGUE_RETRIES = 4
    private const val CATALOGUE_RETRY_MS = 60_000L

    /**
     * Раз за процесс, уже через поднятый туннель, спрашивает у ядра актуальный состав входов и сохраняет его.
     * Ответ сервера (любой код) — финал; нет ответа — повторы с удвоением паузы, см. [CATALOGUE_RETRIES].
     * Работающий туннель не перезапускается (живые соединения не рвём) — новый состав берётся при следующем запуске.
     */
    private fun refreshCatalogue(context: Context) {
        if (catalogueRefreshStarted) return
        catalogueRefreshStarted = true
        Thread({
            var attempt = 0
            while (true) {
                val answered = runCatching {
                    val response = NyutagHttp.request(context, "GET", CATALOGUE_PATH)
                    if (response.status == 200) {
                        val fetched = NyutagCatalogue.parse(response.body)
                        if (fetched != null) {
                            context.getSharedPreferences(CATALOGUE_PREFS, Context.MODE_PRIVATE).edit().putString(CATALOGUE_KEY, fetched.toJson()).apply()
                            Timber.i("Nyutag tunnel: catalogue v%d saved, %d paths", fetched.version, fetched.paths.size)
                        }
                    }
                    response.status < 500
                }.onFailure { Timber.w(it, "Nyutag tunnel: catalogue refresh failed (attempt %d)", attempt) }.getOrDefault(false)
                if (answered || attempt >= CATALOGUE_RETRIES) return@Thread
                Thread.sleep(CATALOGUE_RETRY_MS shl attempt)
                attempt++
            }
        }, "nyutag-catalogue").apply { isDaemon = true }.start()
    }

    /** Ядро за входом; TLS сквозной, сертификат выпущен [CA_PEM]. */
    const val SERVER_NAME = "nyutag.chat"
    const val HOMESERVER_URL = "https://core.nyutag.chat:6167"

    /** Nyutag Private CA 1 (P-256, до 2036-10-08). Публичная часть — секрета нет. */
    private const val CA_PEM = """-----BEGIN CERTIFICATE-----
MIIBwjCCAWmgAwIBAgIUdESbmTDNd3kdFjE1CO728DfK9mIwCgYIKoZIzj0EAwIw
LzEcMBoGA1UEAwwTTnl1dGFnIFByaXZhdGUgQ0EgMTEPMA0GA1UECgwGTnl1dGFn
MB4XDTI2MTAxMTAyMTExMFoXDTM2MTAwODAyMTExMFowLzEcMBoGA1UEAwwTTnl1
dGFnIFByaXZhdGUgQ0EgMTEPMA0GA1UECgwGTnl1dGFnMFkwEwYHKoZIzj0CAQYI
KoZIzj0DAQcDQgAETor1VmEXfZjHEwb3tN4+7DyeMoYzla+26gWWrryNsukaQWJD
KCQIZ9Xss7w9JdT6MgbzeHTOhpp7BS9JUhup9aNjMGEwHQYDVR0OBBYEFD8k/5gp
RZdzsql1EE4ibo6gzi+wMB8GA1UdIwQYMBaAFD8k/5gpRZdzsql1EE4ibo6gzi+w
MA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49BAMCA0cA
MEQCIA/jaEWYsLV9gIlj3x3eylQl9rhllpbGYntQlUGl9q3LAiA3lLol4Znkn0Zg
IpEIFGu+D+0Mt/kAVjPkv7TN6GASeQ==
-----END CERTIFICATE-----"""

    /** DER нашего CA — единственный корень доверия для Rust SDK. */
    val caDer: ByteArray by lazy {
        CertificateFactory.getInstance("X.509").generateCertificate(CA_PEM.byteInputStream()).encoded
    }

    /**
     * Куда пускает локальный прокси WebView звонка ([DefaultNyutagWebProxy]): ядро (JWT LiveKit и сигналинг),
     * узлы `*.nyutag.chat:443` (TURN/TLS) и ICE-TCP SFU по IP. Остальное — 403, чтобы прокси не стал общим выходом.
     */
    private val RTC_ICE_TCP = setOf("2.31.27.66:7881")

    internal fun webProxyAllows(host: String, port: Int): Boolean = when {
        host == "core.nyutag.chat" && port == 6167 -> true
        host.endsWith(".nyutag.chat") && port == 443 -> true
        else -> "$host:$port" in RTC_ICE_TCP
    }

    @Volatile private var port = 0
    private var appContext: Context? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Поднимает ядро один раз на процесс и возвращает URL SOCKS-прокси для reqwest
     * (`socks5h://user:pass@127.0.0.1:port`); null — если ядро не поднялось (тогда SDK пойдёт напрямую и упрётся в закрытый порт).
     */
    @Synchronized
    fun proxyUrl(context: Context): String? {
        if (!Mobile.running()) {
            val catalogue = catalogue(context)
            if (catalogue == null) {
                Timber.e("Nyutag tunnel: asset %s is missing — see catalogue.txt.example", CATALOGUE_ASSET)
                return null
            }
            try {
                Mobile.setMemoryFile(context.filesDir.resolve("nyutag-core-memory.json").path)
                val p = ServerSocket(0).use { it.localPort }
                Mobile.start(catalogue, p.toLong(), network(context))
                port = p
            } catch (e: Exception) {
                Timber.e(e, "Nyutag tunnel failed to start")
                return null
            }
            appContext = context.applicationContext
            watchNetwork(context)
        }
        refreshCatalogue(context)
        return "socks5h://${Mobile.socksUser()}:${Mobile.socksPass()}@127.0.0.1:$port"
    }

    /**
     * Гасит ядро — после выхода из последнего аккаунта: возить нечего, а зонды путей зря тратят трафик.
     * Следующий [proxyUrl] поднимет его заново (память путей на диске остаётся).
     */
    @Synchronized
    fun stop() {
        val callback = networkCallback
        val context = appContext
        if (callback != null && context != null) {
            runCatching { context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        if (Mobile.running()) {
            runCatching { Mobile.stop() }.onFailure { Timber.w(it, "Nyutag tunnel: stop failed") }
            Timber.i("Nyutag tunnel stopped")
        }
        port = 0
    }

    /**
     * Смена сети (Wi-Fi ↔ мобильная ↔ провод) — ядру, как делает iOS: оно переключает память путей на новую сеть
     * и обновляет общие соединения, без перезапуска туннеля. Ядро само игнорирует повтор того же имени.
     */
    private fun watchNetwork(context: Context) {
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (!Mobile.running()) return
                runCatching { Mobile.setNetwork(networkName(caps)) }.onFailure { Timber.w(it, "Nyutag tunnel: setNetwork failed") }
            }
        }
        runCatching { context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
            .onFailure { Timber.w(it, "Nyutag tunnel: network callback not registered") }
    }

    private fun network(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "cell"
        return networkName(caps)
    }

    private fun networkName(caps: NetworkCapabilities): String = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "wired"
        else -> "cell"
    }
}
