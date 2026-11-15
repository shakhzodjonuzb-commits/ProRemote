package uz.ilhomvich.proremote

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import uz.ilhomvich.proremote.adb.AdbClient
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var adb: AdbClient
    private lateinit var statusText: TextView
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var appList: LinearLayout
    private lateinit var appSearch: EditText
    private lateinit var remotePanel: LinearLayout
    private lateinit var appsPanel: LinearLayout
    private lateinit var toolsPanel: LinearLayout
    private lateinit var tabButtons: List<TextView>
    private var activeTab = 0
    private var screenWidth = 1920
    private var screenHeight = 1080
    private var lastPointerX = 960
    private var lastPointerY = 540
    private var apps: List<String> = emptyList()

    private val bg = Color.rgb(10, 16, 32)
    private val surface = Color.rgb(21, 30, 51)
    private val surface2 = Color.rgb(29, 42, 69)
    private val blue = Color.rgb(76, 132, 255)
    private val text = Color.rgb(244, 247, 255)
    private val muted = Color.rgb(157, 171, 198)
    private val green = Color.rgb(43, 190, 132)
    private val red = Color.rgb(226, 81, 96)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        adb = AdbClient(filesDir.toPath().resolve("adb"))
        buildUi()
        val prefs = getSharedPreferences("remote", MODE_PRIVATE)
        hostInput.setText(prefs.getString("host", ""))
        portInput.setText(prefs.getString("port", "5555"))
        updateTab()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }
        setContentView(scroll)

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(label("PRO REMOTE", 22, text, true))
        titleBlock.addView(label("ADB • ANDROID TV CONTROLLER", 10, muted, true))
        heading.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(label("v1.0", 11, muted, true))
        page.addView(heading)
        page.addView(space(12))

        val connection = card()
        connection.addView(label("TV CONNECTION", 11, muted, true))
        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hostInput = EditText(this).apply {
            setSingleLine(true)
            hint = "TV IP address"
            setTextColor(text)
            setHintTextColor(muted)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            background = rounded(surface2, 12)
            setPadding(dp(12), 0, dp(12), 0)
        }
        portInput = EditText(this).apply {
            setSingleLine(true)
            hint = "Port"
            setTextColor(text)
            setHintTextColor(muted)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER
            background = rounded(surface2, 12)
            setPadding(dp(10), 0, dp(10), 0)
        }
        fields.addView(hostInput, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        fields.addView(portInput, LinearLayout.LayoutParams(dp(78), dp(48)))
        connection.addView(space(10))
        connection.addView(fields)
        connection.addView(space(9))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton("CONNECT", blue) { connect() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
        actions.addView(actionButton("DISCONNECT", surface2) { disconnect() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        connection.addView(actions)
        connection.addView(space(10))
        statusText = label("●  DISCONNECTED  •  Enter TV IP and port", 11, muted, true)
        connection.addView(statusText)
        page.addView(connection)
        page.addView(space(14))

        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabNames = listOf("REMOTE", "APPS", "TOOLS")
        val mutableTabs = mutableListOf<TextView>()
        tabNames.forEachIndexed { index, name ->
            val tab = label(name, 12, muted, true).apply {
                gravity = Gravity.CENTER
                background = rounded(surface, 14)
                setPadding(dp(20), dp(12), dp(20), dp(12))
                setOnClickListener { activeTab = index; updateTab() }
            }
            tabRow.addView(tab, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(8) })
            mutableTabs.add(tab)
        }
        tabButtons = mutableTabs
        tabScroll.addView(tabRow)
        page.addView(tabScroll)
        page.addView(space(12))

        remotePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        buildRemotePanel()
        buildAppsPanel()
        buildToolsPanel()
        page.addView(remotePanel)
        page.addView(appsPanel)
        page.addView(toolsPanel)

        page.addView(space(18))
        page.addView(label("PRO REMOTE  •  Local network ADB control", 10, muted, false))
    }

    private fun buildRemotePanel() {
        remotePanel.removeAllViews()
        val power = card()
        power.addView(label("POWER & NAVIGATION", 11, muted, true))
        power.addView(space(8))
        addButtonGrid(power, listOf(
            ButtonSpec("⏻  POWER", red) { key("KEYCODE_POWER") },
            ButtonSpec("TV POWER", surface2) { key("KEYCODE_TV_POWER") },
            ButtonSpec("INPUT", surface2) { key("KEYCODE_TV_INPUT") },
            ButtonSpec("HOME", surface2) { key("KEYCODE_HOME") },
            ButtonSpec("BACK", surface2) { key("KEYCODE_BACK") },
            ButtonSpec("MENU", surface2) { key("KEYCODE_MENU") },
            ButtonSpec("SETTINGS", surface2) { key("KEYCODE_SETTINGS") },
            ButtonSpec("INFO", surface2) { key("KEYCODE_INFO") },
            ButtonSpec("GUIDE", surface2) { key("KEYCODE_GUIDE") },
            ButtonSpec("EXIT", surface2) { key("KEYCODE_ESCAPE") }
        ), 3)
        remotePanel.addView(power)
        remotePanel.addView(space(10))

        val nav = card()
        nav.addView(label("DIRECTION PAD", 11, muted, true))
        nav.addView(space(8))
        val dpad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        addButtonRow(dpad, listOf(null, ButtonSpec("▲", surface2) { key("KEYCODE_DPAD_UP") }, null), 54)
        addButtonRow(dpad, listOf(
            ButtonSpec("◀", surface2) { key("KEYCODE_DPAD_LEFT") },
            ButtonSpec("OK", blue) { key("KEYCODE_DPAD_CENTER") },
            ButtonSpec("▶", surface2) { key("KEYCODE_DPAD_RIGHT") }
        ), 58)
        addButtonRow(dpad, listOf(null, ButtonSpec("▼", surface2) { key("KEYCODE_DPAD_DOWN") }, null), 54)
        nav.addView(dpad)
        remotePanel.addView(nav)
        remotePanel.addView(space(10))

        val volume = card()
        volume.addView(label("VOLUME & CHANNEL", 11, muted, true))
        volume.addView(space(8))
        addButtonGrid(volume, listOf(
            ButtonSpec("VOL +", surface2) { key("KEYCODE_VOLUME_UP") },
            ButtonSpec("MUTE", surface2) { key("KEYCODE_MUTE") },
            ButtonSpec("CH +", surface2) { key("KEYCODE_CHANNEL_UP") },
            ButtonSpec("VOL −", surface2) { key("KEYCODE_VOLUME_DOWN") },
            ButtonSpec("VOL 0", surface2) { key("KEYCODE_VOLUME_MUTE") },
            ButtonSpec("CH −", surface2) { key("KEYCODE_CHANNEL_DOWN") }
        ), 3)
        remotePanel.addView(volume)
        remotePanel.addView(space(10))

        val media = card()
        media.addView(label("MEDIA CONTROL", 11, muted, true))
        media.addView(space(8))
        addButtonGrid(media, listOf(
            ButtonSpec("⏮ PREV", surface2) { key("KEYCODE_MEDIA_PREVIOUS") },
            ButtonSpec("⏪ REW", surface2) { key("KEYCODE_MEDIA_REWIND") },
            ButtonSpec("▶ / ⏸", blue) { key("KEYCODE_MEDIA_PLAY_PAUSE") },
            ButtonSpec("⏩ FF", surface2) { key("KEYCODE_MEDIA_FAST_FORWARD") },
            ButtonSpec("⏭ NEXT", surface2) { key("KEYCODE_MEDIA_NEXT") },
            ButtonSpec("■ STOP", surface2) { key("KEYCODE_MEDIA_STOP") }
        ), 3)
        remotePanel.addView(media)
        remotePanel.addView(space(10))

        val number = card()
        number.addView(label("NUMPAD", 11, muted, true))
        number.addView(space(8))
        addButtonGrid(number, (1..9).map { digit -> ButtonSpec(digit.toString(), surface2) { key("KEYCODE_$digit") } } + listOf(
            ButtonSpec("DEL", surface2) { key("KEYCODE_DEL") },
            ButtonSpec("0", surface2) { key("KEYCODE_0") },
            ButtonSpec(".", surface2) { key("KEYCODE_PERIOD") },
            ButtonSpec("−", surface2) { key("KEYCODE_MINUS") },
            ButtonSpec("LAST CH", surface2) { key("KEYCODE_LAST_CHANNEL") },
            ButtonSpec("ENTER", blue) { key("KEYCODE_ENTER") }
        ), 3)
        remotePanel.addView(number)
        remotePanel.addView(space(10))

        val colorKeys = card()
        colorKeys.addView(label("TV FUNCTION KEYS", 11, muted, true))
        colorKeys.addView(space(8))
        addButtonGrid(colorKeys, listOf(
            ButtonSpec("RED", Color.rgb(190, 58, 70)) { key("KEYCODE_PROG_RED") },
            ButtonSpec("GREEN", Color.rgb(30, 139, 95)) { key("KEYCODE_PROG_GREEN") },
            ButtonSpec("YELLOW", Color.rgb(190, 145, 43)) { key("KEYCODE_PROG_YELLOW") },
            ButtonSpec("BLUE", Color.rgb(50, 107, 205)) { key("KEYCODE_PROG_BLUE") },
            ButtonSpec("SUBTITLE", surface2) { key("KEYCODE_CAPTIONS") },
            ButtonSpec("TEXT", surface2) { key("KEYCODE_TV_TELETEXT") },
            ButtonSpec("TV", surface2) { key("KEYCODE_TV") },
            ButtonSpec("SLEEP", surface2) { key("KEYCODE_SLEEP") },
            ButtonSpec("APP SWITCH", surface2) { key("KEYCODE_APP_SWITCH") },
            ButtonSpec("VOICE", surface2) { key("KEYCODE_ASSIST") },
            ButtonSpec("ZOOM +", surface2) { key("KEYCODE_ZOOM_IN") },
            ButtonSpec("ZOOM −", surface2) { key("KEYCODE_ZOOM_OUT") },
            ButtonSpec("BOOKMARK", surface2) { key("KEYCODE_BOOKMARK") },
            ButtonSpec("RECORD", surface2) { key("KEYCODE_MEDIA_RECORD") },
            ButtonSpec("AUDIO", surface2) { key("KEYCODE_MEDIA_AUDIO_TRACK") }
        ), 3)
        remotePanel.addView(colorKeys)
        remotePanel.addView(space(10))

        val textCard = card()
        textCard.addView(label("INPUT TEXT", 11, muted, true))
        textCard.addView(label("Send text to the focused TV field. Unsupported symbols depend on the TV keyboard.", 11, muted, false))
        val textEntry = EditText(this).apply {
            hint = "Type here…"
            setTextColor(text)
            setHintTextColor(muted)
            textSize = 15f
            minLines = 2
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = rounded(surface2, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        textCard.addView(space(7))
        textCard.addView(textEntry)
        textCard.addView(space(8))
        textCard.addView(actionButton("SEND TEXT", blue) {
            val value = textEntry.text.toString()
            if (value.isNotBlank()) {
                val encoded = value.replace(" ", "%s").replace("\n", "%s").replace("\t", "%s")
                shell("input text ${shellQuote(encoded)}")
                textEntry.text.clear()
            } else toast("Avval matn kiriting")
        })
        remotePanel.addView(textCard)
        remotePanel.addView(space(10))

        val mouse = card()
        mouse.addView(label("MOUSE / TOUCHPAD", 11, muted, true))
        mouse.addView(label("Tap = click • Drag = swipe • coordinates are scaled to TV resolution", 11, muted, false))
        mouse.addView(space(8))
        val touch = TouchpadView().apply { layoutParams = LinearLayout.LayoutParams(-1, dp(210)) }
        mouse.addView(touch)
        mouse.addView(space(8))
        addButtonGrid(mouse, listOf(
            ButtonSpec("LEFT CLICK", blue) { shell("input mouse tap $lastPointerX $lastPointerY") },
            ButtonSpec("RIGHT / BACK", surface2) { key("KEYCODE_BACK") },
            ButtonSpec("MOUSE HOME", surface2) { key("KEYCODE_HOME") }
        ), 3)
        remotePanel.addView(mouse)
    }

    private fun buildAppsPanel() {
        appsPanel.removeAllViews()
        val panel = card()
        panel.addView(label("TV APP LAUNCHER", 11, muted, true))
        panel.addView(label("Installed apps with a launcher activity appear here. Tap one to open it on TV.", 11, muted, false))
        panel.addView(space(8))
        appSearch = EditText(this).apply {
            hint = "Search apps / package names"
            setTextColor(text)
            setHintTextColor(muted)
            textSize = 14f
            singleLine = true
            background = rounded(surface2, 12)
            setPadding(dp(12), 0, dp(12), 0)
            addTextChangedListener(SimpleTextWatcher { renderApps() })
        }
        panel.addView(appSearch, LinearLayout.LayoutParams(-1, dp(46)))
        panel.addView(space(8))
        panel.addView(actionButton("REFRESH APP LIST", blue) { loadApps() })
        panel.addView(space(10))
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(appList)
        appsPanel.addView(panel)
        appsPanel.addView(space(10))
        appsPanel.addView(actionButton("OPEN ANDROID SETTINGS", surface2) { shell("am start -a android.settings.SETTINGS") })
        showEmptyApps()
    }

    private fun buildToolsPanel() {
        toolsPanel.removeAllViews()
        val quick = card()
        quick.addView(label("QUICK SYSTEM SHORTCUTS", 11, muted, true))
        quick.addView(space(8))
        addButtonGrid(quick, listOf(
            ButtonSpec("Wi-Fi", surface2) { shell("am start -a android.settings.WIFI_SETTINGS") },
            ButtonSpec("Bluetooth", surface2) { shell("am start -a android.settings.BLUETOOTH_SETTINGS") },
            ButtonSpec("Display", surface2) { shell("am start -a android.settings.DISPLAY_SETTINGS") },
            ButtonSpec("Sound", surface2) { shell("am start -a android.settings.SOUND_SETTINGS") },
            ButtonSpec("Apps", surface2) { shell("am start -a android.settings.MANAGE_APPLICATIONS_SETTINGS") },
            ButtonSpec("Date & Time", surface2) { shell("am start -a android.settings.DATE_SETTINGS") },
            ButtonSpec("Notifications", surface2) { key("KEYCODE_NOTIFICATION") },
            ButtonSpec("Search", surface2) { key("KEYCODE_SEARCH") },
            ButtonSpec("Recent", surface2) { key("KEYCODE_APP_SWITCH") }
        ), 3)
        toolsPanel.addView(quick)
        toolsPanel.addView(space(10))

        val system = card()
        system.addView(label("TV INFORMATION", 11, muted, true))
        system.addView(space(6))
        system.addView(actionButton("GET DEVICE INFO", blue) {
            shell("getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; wm size; wm density") { result ->
                showInfoDialog("TV DEVICE INFO", result.ifBlank { "No information returned." })
            }
        })
        system.addView(space(8))
        system.addView(actionButton("RELOAD APP LIST", surface2) { loadApps() })
        system.addView(space(8))
        system.addView(actionButton("RECONNECT ADB", surface2) { connect() })
        toolsPanel.addView(system)

        toolsPanel.addView(space(10))
        val help = card()
        help.addView(label("CONNECTION NOTES", 11, muted, true))
        help.addView(label("• TV and phone must be on the same local network.\n• The TV must expose classic ADB TCP (usually port 5555).\n• Accept the RSA debugging prompt on the TV the first time.\n• Android 11+ Wireless Debugging pairing ports are not supported in this build.", 12, text, false))
        toolsPanel.addView(help)
    }

    private fun connect() {
        val host = hostInput.text.toString().trim()
        val port = portInput.text.toString().toIntOrNull() ?: 5555
        getSharedPreferences("remote", MODE_PRIVATE).edit().putString("host", host).putString("port", port.toString()).apply()
        statusText.text = "●  CONNECTING…  •  Check the TV authorization prompt"
        statusText.setTextColor(blue)
        io.execute {
            try {
                adb.connect(host, port)
                val size = adb.shell("wm size")
                val match = Regex("(\\d+)x(\\d+)").findAll(size).lastOrNull()
                if (match != null) {
                    screenWidth = match.groupValues[1].toIntOrNull() ?: screenWidth
                    screenHeight = match.groupValues[2].toIntOrNull() ?: screenHeight
                }
                runOnUiThread {
                    statusText.text = "●  CONNECTED  •  $host:$port  •  ${screenWidth}×${screenHeight}"
                    statusText.setTextColor(green)
                    toast("TV ADB bilan ulandi")
                    loadApps()
                }
            } catch (error: Exception) {
                adb.close()
                runOnUiThread {
                    statusText.text = "●  CONNECTION FAILED  •  ${error.message ?: "Unknown error"}"
                    statusText.setTextColor(red)
                    toast("Ulanish amalga oshmadi")
                }
            }
        }
    }

    private fun disconnect() {
        io.execute { adb.close(); runOnUiThread { statusText.text = "●  DISCONNECTED"; statusText.setTextColor(muted) } }
    }

    private fun key(keyName: String) {
        val source = if (keyName.startsWith("KEYCODE_DPAD_")) "dpad " else ""
        shell("input ${source}keyevent $keyName")
    }

    private fun shell(command: String, onSuccess: ((String) -> Unit)? = null) {
        if (!adb.isConnected) {
            toast("Avval TV’ga ulaning")
            return
        }
        io.execute {
            try {
                val result = adb.shell(command)
                runOnUiThread {
                    onSuccess?.invoke(result)
                    if (onSuccess == null && result.contains("Exception", true)) toast(result.take(120))
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusText.text = "●  ADB ERROR  •  ${error.message ?: "Connection lost"}"
                    statusText.setTextColor(red)
                }
            }
        }
    }

    private fun loadApps() {
        if (!adb.isConnected) { toast("Avval TV’ga ulaning"); return }
        appList.removeAllViews()
        appList.addView(label("Loading installed TV apps…", 12, muted, false))
        io.execute {
            try {
                val launcherResult = adb.shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
                var found = launcherResult.lineSequence()
                    .map { it.trim().substringBefore('/').trim() }
                    .filter { it.contains('.') && it.matches(Regex("[A-Za-z0-9_.$]+")) }
                    .distinct()
                    .toList()
                if (found.isEmpty()) {
                    found = adb.shell("pm list packages").lineSequence()
                        .map { it.trim().removePrefix("package:") }
                        .filter { it.contains('.') && it.matches(Regex("[A-Za-z0-9_.$]+")) }
                        .distinct().toList()
                }
                apps = found.sortedBy { appLabel(it).lowercase(Locale.ROOT) }
                runOnUiThread { renderApps() }
            } catch (error: Exception) {
                runOnUiThread {
                    appList.removeAllViews()
                    appList.addView(label("Could not read apps: ${error.message}", 12, red, false))
                }
            }
        }
    }

    private fun renderApps() {
        if (!::appList.isInitialized) return
        appList.removeAllViews()
        val query = if (::appSearch.isInitialized) appSearch.text.toString().trim().lowercase(Locale.ROOT) else ""
        val filtered = apps.filter { it.lowercase(Locale.ROOT).contains(query) || appLabel(it).lowercase(Locale.ROOT).contains(query) }
        if (filtered.isEmpty()) {
            appList.addView(label(if (apps.isEmpty()) "No apps loaded. Tap REFRESH APP LIST." else "No matching apps.", 12, muted, false))
            return
        }
        filtered.forEach { packageName ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(surface2, 12)
                setPadding(dp(12), dp(7), dp(7), dp(7))
            }
            val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            titleBlock.addView(label(appLabel(packageName), 14, text, true))
            titleBlock.addView(label(packageName, 10, muted, false))
            row.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
            val launch = actionButton("OPEN", blue) { launchApp(packageName) }
            row.addView(launch, LinearLayout.LayoutParams(dp(70), dp(38)))
            appList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
    }

    private fun launchApp(packageName: String) {
        shell("monkey -p ${shellQuote(packageName)} 1") { result ->
            if (result.contains("No activities found", true) || result.contains("monkey aborted", true)) {
                toast("Bu paket launcher ilovasi emas yoki ochilmadi")
            }
        }
    }

    private fun appLabel(pkg: String): String {
        val known = mapOf(
            "com.google.android.youtube.tv" to "YouTube",
            "com.google.android.youtube" to "YouTube",
            "com.netflix.ninja" to "Netflix",
            "com.android.settings" to "Settings",
            "com.google.android.tvlauncher" to "Android TV Home",
            "com.google.android.apps.tv.launcherx" to "Google TV Home",
            "org.videolan.vlc" to "VLC",
            "com.disney.disneyplus" to "Disney+",
            "com.amazon.amazonvideo.livingroom" to "Prime Video"
        )
        return known[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun showEmptyApps() {
        appList.removeAllViews()
        appList.addView(label("Connect to your TV, then tap REFRESH APP LIST.", 12, muted, false))
    }

    private fun updateTab() {
        remotePanel.visibility = if (activeTab == 0) View.VISIBLE else View.GONE
        appsPanel.visibility = if (activeTab == 1) View.VISIBLE else View.GONE
        toolsPanel.visibility = if (activeTab == 2) View.VISIBLE else View.GONE
        tabButtons.forEachIndexed { index, view ->
            view.background = rounded(if (index == activeTab) blue else surface, 14)
            view.setTextColor(if (index == activeTab) Color.WHITE else muted)
        }
    }

    private fun addButtonGrid(parent: LinearLayout, specs: List<ButtonSpec>, columns: Int) {
        specs.chunked(columns).forEach { row -> addButtonRow(parent, row.map { it }, 46) }
    }

    private fun addButtonRow(parent: LinearLayout, specs: List<ButtonSpec?>, height: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        specs.forEach { spec ->
            if (spec == null) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(height), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            } else {
                val button = actionButton(spec.title, spec.color, spec.action)
                row.addView(button, LinearLayout.LayoutParams(0, dp(height), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private data class ButtonSpec(val title: String, val color: Int, val action: () -> Unit)

    private fun actionButton(title: String, color: Int, action: () -> Unit): Button {
        return Button(this).apply {
            text = title
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = rounded(color, 12)
            setPadding(dp(6), 0, dp(6), 0)
            minHeight = 0
            minimumHeight = 0
            stateListAnimator = null
            setOnClickListener { action() }
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(surface, 18)
        setPadding(dp(13), dp(13), dp(13), dp(13))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = true
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun space(height: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun showInfoDialog(title: String, value: String) {
        val view = TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(text)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private inner class TouchpadView : View(this@MainActivity) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface2 }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 180, 198, 230); strokeWidth = dp(1).toFloat() }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
        private var downX = 0f
        private var downY = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(bounds, dp(14).toFloat(), dp(14).toFloat(), fill)
            canvas.drawLine(width / 2f, dp(18).toFloat(), width / 2f, height - dp(18).toFloat(), line)
            canvas.drawLine(dp(18).toFloat(), height / 2f, width - dp(18).toFloat(), height / 2f, line)
            canvas.drawCircle(width / 2f, height / 2f, dp(5).toFloat(), dot)
            val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = dp(12).toFloat(); textAlign = Paint.Align.CENTER }
            canvas.drawText("TOUCH / DRAG HERE", width / 2f, height / 2f + dp(34), caption)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; return true }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val startX = (downX / width.coerceAtLeast(1) * screenWidth).toInt().coerceIn(0, screenWidth)
                    val startY = (downY / height.coerceAtLeast(1) * screenHeight).toInt().coerceIn(0, screenHeight)
                    val endX = (event.x / width.coerceAtLeast(1) * screenWidth).toInt().coerceIn(0, screenWidth)
                    val endY = (event.y / height.coerceAtLeast(1) * screenHeight).toInt().coerceIn(0, screenHeight)
                    lastPointerX = endX
                    lastPointerY = endY
                    if (abs(dx) < dp(18) && abs(dy) < dp(18)) {
                        shell("input mouse tap $endX $endY")
                    } else {
                        shell("input mouse swipe $startX $startY $endX $endY 350")
                    }
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }

        override fun performClick(): Boolean { super.performClick(); return true }
    }

    override fun onDestroy() {
        super.onDestroy()
        io.execute { adb.close() }
        io.shutdown()
    }
}

private class SimpleTextWatcher(private val changed: (String) -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed(s?.toString().orEmpty())
    override fun afterTextChanged(s: Editable?) = Unit
}
