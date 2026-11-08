package fr.bonamy.iptv

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.leanback.widget.SearchBar
import androidx.leanback.widget.SearchOrbView
import androidx.leanback.widget.SpeechOrbView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.bonamy.iptv.core.*
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var browser: LinearLayout
    private lateinit var grid: RecyclerView
    private lateinit var adapter: ChannelAdapter
    private lateinit var status: TextView
    private lateinit var empty: LinearLayout
    private lateinit var search: EditText
    private lateinit var searchBar: SearchBar
    private lateinit var searchPanel: LinearLayout
    private lateinit var remoteSearch: RemoteSearchController
    private lateinit var countryGrid: RecyclerView
    private lateinit var countryAdapter: CountryAdapter
    private lateinit var hero: LinearLayout
    private lateinit var heading: TextView
    private lateinit var breadcrumb: TextView
    private var countryMode = true
    private var countryFocus: String? = "FAVORITES"
    private val voiceSearch = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::submitRemoteSearch)
    }
    private lateinit var menuControl: LinearLayout
    private var browserMenu: android.app.Dialog? = null
    private val preferences by lazy { ViewerPreferences(this) }
    private val store get() = (application as IPTVApplication).libraryStore
    private val channelOrder by lazy {
        CountryChannelOrder.parse(assets.open("channel-order.json").bufferedReader().use { it.readText() })
    }
    private var library: CatalogSnapshot? = null
    private var catalog = CatalogIndex(emptyList())
    private var countries = CountryAdapter.tiles(emptyList())
    internal var section = Section.ALL
        private set
    private var country: String? = null
    private var searchOrigin = Section.ALL
    private var searchOriginHome = true
    private var searchOriginCountry: String? = null
    private var selectedChannel: String? = null
    private val positions = mutableMapOf<Section, String>()
    private var updateJob: Job? = null
    private var filterJob: Job? = null
    private var guideScreen: GuideScreen? = null
    private var guideJob: Job? = null
    private var guideOverlay: GuideScreen? = null
    private var guideOverlayJob: Job? = null
    private var restoreGuideOverlay = false
    private var guideOverlayState: Bundle? = null
    private var guideTileFocused = false
    private var restoreGuide = false
    private var guideState: Bundle? = null
    private var playerScreen: PlayerScreen? = null
    private var restorePlayback: String? = null
    private var restoreStream = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        section = savedInstanceState?.getString("section")?.let { runCatching { Section.valueOf(it) }.getOrNull() } ?: Section.ALL
        country = savedInstanceState?.getString("country")
        selectedChannel = savedInstanceState?.getString("channel")
        restoreGuide = savedInstanceState?.getBoolean("guideOpen") ?: false
        guideState = savedInstanceState?.getBundle("guideState")
        restoreGuideOverlay = savedInstanceState?.getBoolean("guideOverlayOpen") ?: false
        guideOverlayState = savedInstanceState?.getBundle("guideOverlayState")
        restorePlayback = savedInstanceState?.getString("playing")
        restoreStream = savedInstanceState?.getInt("stream") ?: 0
        countryMode = savedInstanceState?.getBoolean("countryMode") ?: true
        countryFocus = if (savedInstanceState?.getString("homeFocus") == "ALL") null else savedInstanceState?.getString("homeFocus") ?: "FAVORITES"
        searchOriginHome = savedInstanceState?.getBoolean("searchOriginHome") ?: true
        searchOriginCountry = savedInstanceState?.getString("searchOriginCountry")
        searchOrigin = savedInstanceState?.getString("searchOrigin")?.let { Section.valueOf(it) } ?: Section.ALL
        root = FrameLayout(this).apply { background = browserBackground() }
        browser = column()
        root.addView(browser, FrameLayout.LayoutParams(-1, -1)); setContentView(root)
        val header = row().apply { setPadding(dp(36), 0, dp(36), 0) }
        val menuRegion = FrameLayout(this)
        menuControl = row().apply {
            id = R.id.browser_menu; isFocusable = true; isClickable = true
            background = headerBackground(); setPadding(dp(10), 0, dp(10), 0)
            contentDescription = "IPTV menu"; setOnClickListener { showBrowserMenu() }
        }
        menuControl.addView(label("IPTV", 17f).apply { bold(); letterSpacing = .16f })
        breadcrumb = label("/  LiveTV", 12f, MUTED).apply { setPadding(dp(22), 0, 0, 0) }
        menuControl.addView(breadcrumb, LinearLayout.LayoutParams(-2, -2))
        menuRegion.addView(menuControl, FrameLayout.LayoutParams(-2, dp(36), Gravity.CENTER_VERTICAL))
        header.addView(menuRegion, LinearLayout.LayoutParams(0, -1, 1f))
        val searchControl = row().apply {
            id = R.id.search_button; isFocusable = true; isClickable = true
            background = headerBackground(); contentDescription = "Search channels"
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { toggleSearch() }
        }
        val searchLabel = label("Search", 15f).apply { visibility = View.INVISIBLE }
        searchControl.addView(searchLabel, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
        searchControl.addView(ImageView(this).apply {
            setImageResource(R.drawable.baseline_search_24); imageTintList = android.content.res.ColorStateList.valueOf(MUTED)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        searchControl.setOnFocusChangeListener { _, focused -> searchLabel.visibility = if (focused) View.VISIBLE else View.INVISIBLE }
        header.addView(searchControl, LinearLayout.LayoutParams(-2, dp(36)).apply { marginEnd = dp(20) })
        browser.addView(header, LinearLayout.LayoutParams(-1, dp(56)))
        val body = column().apply { setPadding(dp(36), dp(8), dp(36), dp(16)) }
        browser.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        hero = row().apply { id = R.id.browser_hero }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.live_tv_cutout); scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(132), dp(96)).apply { marginEnd = dp(22) })
        heading = label(section.title, 30f).apply { bold() }
        hero.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(hero, LinearLayout.LayoutParams(-1, dp(96)).apply { bottomMargin = dp(20) })
        searchPanel = row().apply { visibility = View.GONE }
        remoteSearch = RemoteSearchController(this, ::submitRemoteSearch)
        searchBar = (layoutInflater.inflate(R.layout.search_bar, searchPanel, false) as SearchBar).apply {
            title = "channels"
            setSearchBarListener(object : SearchBar.SearchBarListener {
                override fun onSearchQueryChange(query: String) { render() }
                override fun onSearchQuerySubmit(query: String) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
                    focusChannel()
                }
                override fun onKeyboardDismiss(query: String) { focusChannel() }
            })
        }
        search = searchBar.findViewById<EditText>(androidx.leanback.R.id.lb_search_text_editor).apply {
            id = R.id.search_input; typeface = resources.getFont(R.font.theme)
            hint = "Search channels"; setTextColor(-1); setHintTextColor(MUTED)
        }
        searchBar.findViewById<View>(androidx.leanback.R.id.lb_search_bar_items).setBackgroundResource(R.drawable.search_input_background)
        searchBar.findViewById<SpeechOrbView>(androidx.leanback.R.id.lb_search_bar_speech_orb).apply {
            setNotListeningOrbColors(SearchOrbView.Colors(ACCENT, ACCENT, -1))
            setListeningOrbColors(SearchOrbView.Colors(ACCENT, ACCENT, -1)); showNotListening()
        }
        searchBar.setSpeechRecognitionCallback {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search channels")
            runCatching { voiceSearch.launch(intent) }.onFailure { search.requestFocus() }
        }
        searchPanel.addView(searchBar, LinearLayout.LayoutParams(0, -2, 1f))
        searchPanel.addView(remoteSearch.shortcut, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(20) })
        body.addView(searchPanel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val content = FrameLayout(this)
        adapter = ChannelAdapter(::play, ::channelOptions) { guideTileFocused = false; selectedChannel = it; positions[section] = it }
        adapter.openGuide = ::openGuide
        adapter.guideFocused = { guideTileFocused = true }
        grid = RecyclerView(this).apply {
            id = R.id.channel_grid; layoutManager = GridLayoutManager(this@MainActivity, 6)
            adapter = this@MainActivity.adapter; itemAnimator = null
            clipToPadding = false; setPadding(0, 0, 0, dp(8))
            descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        countryAdapter = CountryAdapter({ countryFocus = it }) { selected ->
            countryFocus = selected
            section = when (selected) { "FAVORITES" -> Section.FAVORITES; "FR" -> Section.FRANCE; "US" -> Section.USA; else -> Section.ALL }
            country = if (section == Section.ALL) selected else null
            countryMode = false; selectedChannel = positions[section]; render(true)
        }
        countryGrid = RecyclerView(this).apply {
            id = R.id.country_grid; layoutManager = GridLayoutManager(this@MainActivity, 5)
            adapter = countryAdapter; itemAnimator = null; isVerticalScrollBarEnabled = false
            visibility = View.GONE
        }
        empty = column().apply { gravity = Gravity.CENTER; visibility = View.GONE }
        content.addView(grid, FrameLayout.LayoutParams(-1, -1)); content.addView(countryGrid, FrameLayout.LayoutParams(-1, -1)); content.addView(empty, FrameLayout.LayoutParams(-1, -1))
        body.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        status = label("Loading channels…", 13f, MUTED).apply { id = R.id.browser_status }
        body.addView(status, LinearLayout.LayoutParams(-1, dp(22)).apply { topMargin = dp(8) })
        savedInstanceState?.getString("query")?.takeIf { it.isNotEmpty() }?.let { searchPanel.visibility = View.VISIBLE; searchBar.setSearchQuery(it) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val screen = playerScreen
                when {
                    guideOverlay != null -> closeGuideOverlay()
                    screen != null -> if (!screen.back()) closePlayer()
                    guideScreen != null -> closeGuide()
                    searchPanel.visibility == View.VISIBLE -> closeSearch()
                    !countryMode -> showHome()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
                }
            }
        })
        render(true)
        prepareChannelCards()
        lifecycleScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { prepareLibrary(store.initial()) }
                // An explicit Refresh may finish while the initial local index is preparing.
                if (library == null) {
                    applyLibrary(loaded)
                    render(!countryMode || selectedChannel != null)
                }
                if (restoreGuide) { openGuide(); restoreGuide = false }
                restorePlayback?.let { id -> catalog.byId[id]?.let { play(it, restoreStream) } }
                restorePlayback = null
                if (restoreGuideOverlay) { openGuideOverlay(); restoreGuideOverlay = false }
                lifecycleScope.launch(Dispatchers.IO) { runCatching { store.persistIfNeeded() } }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { status.text = "Channels unavailable. Select Refresh to retry."; render() }
        }
    }

    private fun showHome() {
        countryMode = true; country = null; render(true)
    }

    private fun prepareChannelCards() {
        val pool = grid.recycledViewPool
        pool.setMaxRecycledViews(0, 18)
        grid.postOnAnimation(object : Runnable {
            override fun run() {
                if (isDestroyed || !countryMode || pool.getRecycledViewCount(0) >= 12) return
                // Build one reusable card per frame while the local catalog loads.
                pool.putRecycledView(adapter.createViewHolder(grid, 0))
                grid.postOnAnimation(this)
            }
        })
    }

    private fun render(focus: Boolean = false) {
        if (!::adapter.isInitialized) return
        val searching = searchPanel.visibility == View.VISIBLE
        hero.visibility = if (countryMode && !searching) View.VISIBLE else View.GONE
        heading.text = if (searching) "Search" else if (countryMode) "LiveTV" else country?.let(::countryName) ?: section.title
        breadcrumb.text = "/  " + heading.text
        status.visibility = if (status.text.isNullOrBlank()) View.GONE else View.VISIBLE
        val currentSection = section; val currentCountry = country; val query = search.text.toString()
        val favorites = preferences.favorites(); val source = catalog
        filterJob?.cancel()
        if (countryMode && searchPanel.visibility != View.VISIBLE) {
            val restoreCountryFocus = countryGrid.hasFocus()
            countryAdapter.countries = countries
            countryGrid.visibility = View.VISIBLE; grid.visibility = View.GONE; empty.visibility = View.GONE
            if (focus || restoreCountryFocus) {
                val position = countryAdapter.countries.indexOfFirst { it.code == countryFocus }.coerceAtLeast(0)
                countryGrid.scrollToPosition(position)
                countryGrid.doOnNextLayout { countryGrid.findViewHolderForAdapterPosition(position)?.itemView?.requestFocusFromTouch() }
                countryGrid.requestLayout()
            }
            return
        }
        countryGrid.visibility = View.GONE
        if (searching && query.isBlank()) {
            adapter.replace(emptyList(), favorites, countries = true)
            grid.visibility = View.GONE; empty.visibility = View.GONE; status.visibility = View.GONE
            if (focus) search.requestFocus()
            return
        }
        if (!searching) {
            val items = if (library == null) emptyList() else if (currentSection == Section.FAVORITES) preferences.favoriteChannels(source.byId)
                else source.browse(currentSection, currentCountry)
            displayChannels(items, favorites, searching = false, focus = focus, query = query)
        } else filterJob = lifecycleScope.launch {
            val items = withContext(Dispatchers.Default) { source.search(query) }
            displayChannels(items, favorites, searching = true, focus = focus, query = query)
        }
    }

    private fun displayChannels(items: List<Channel>, favorites: List<String>, searching: Boolean, focus: Boolean, query: String) {
        val hadGridFocus = grid.hasFocus()
        adapter.replace(items, favorites, countries = searching, guide = !searching && section == Section.FRANCE)
        empty.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        grid.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        if (items.isEmpty()) {
            val message = when {
                library == null -> "Loading channels…"
                query.isNotBlank() -> "No matching channels"
                section == Section.FAVORITES -> "No favorites yet"
                else -> "No channels available"
            }
            empty.addView(label(message, 23f, MUTED).apply { gravity = Gravity.CENTER })
            if (section == Section.FAVORITES && query.isBlank() && library != null) {
                empty.addView(action("Browse channels") { showHome() }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(20) })
            }
        }
        if (focus || hadGridFocus) {
            if (items.isNotEmpty()) focusChannel() else empty.getChildAt(1)?.requestFocus() ?: menuControl.requestFocus()
        }
    }

    private fun focusChannel() {
        val position = if (adapter.guideAvailable && (guideTileFocused || selectedChannel == null)) 0 else adapter.channels.indexOfFirst { it.id == selectedChannel }.coerceAtLeast(0) + if (adapter.guideAvailable) 1 else 0
        grid.post {
            if (guideScreen != null || playerScreen != null || !browser.isShown) return@post
            val visible = grid.findViewHolderForAdapterPosition(position)?.itemView
            if (visible != null) visible.requestFocusFromTouch()
            else {
                grid.scrollToPosition(position)
                grid.post { if (guideScreen == null && playerScreen == null && browser.isShown) grid.findViewHolderForAdapterPosition(position)?.itemView?.requestFocusFromTouch() ?: grid.requestFocus() }
            }
        }
    }

    private fun toggleSearch() {
        if (searchPanel.visibility == View.VISIBLE) { closeSearch(); return }
        rememberSearchOrigin(); section = Section.ALL; country = null; countryMode = false
        remoteSearch.start(); searchPanel.visibility = View.VISIBLE; searchBar.requestFocus(); render()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
    }
    private fun closeSearch() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
        searchPanel.visibility = View.GONE; searchBar.setSearchQuery(""); section = searchOrigin; country = searchOriginCountry; countryMode = searchOriginHome; render(true)
    }

    private fun showBrowserMenu() {
        browserMenu?.dismiss()
        browserMenu = DialogUtils.getDialogBuilder(this, "IPTV")
            .setItems(arrayOf("Refresh")) { _, _ -> refresh() }
            .create().also { it.show() }
    }

    private data class PreparedLibrary(val source: CatalogSnapshot, val catalog: CatalogIndex, val countries: List<CountryTile>)
    private fun prepareLibrary(loaded: CatalogSnapshot): PreparedLibrary {
        val index = CatalogIndex(loaded.channels, channelOrder)
        return PreparedLibrary(loaded, index, CountryAdapter.tiles(index.countries.toList()))
    }
    private fun applyLibrary(loaded: PreparedLibrary) {
        library = loaded.source; catalog = loaded.catalog; countries = loaded.countries
        status.text = ""; status.visibility = View.GONE
    }
    private fun refresh() {
        if (updateJob?.isActive == true) return
        updateJob = lifecycleScope.launch {
            status.text = "Refreshing channels…"; status.visibility = View.VISIBLE
            try {
                val loaded = runInterruptible(Dispatchers.IO) { prepareLibrary(store.refresh()) }
                applyLibrary(loaded); render()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                android.util.Log.w("IPTV", "Catalog refresh failed: ${error.javaClass.simpleName}")
                status.text = "Refresh unavailable. Saved channels are still available."; status.visibility = View.VISIBLE
            }
        }
    }

    private fun countryName(code: String) = CountryAdapter.name(code)
    private fun rememberSearchOrigin() {
        searchOrigin = section; searchOriginCountry = country; searchOriginHome = countryMode
    }
    private fun submitRemoteSearch(query: String) {
        if (playerScreen != null) closePlayer()
        if (searchPanel.visibility != View.VISIBLE) rememberSearchOrigin()
        section = Section.ALL; country = null; countryMode = false
        searchPanel.visibility = View.VISIBLE; searchBar.setSearchQuery(query)
        render(true)
    }

    private fun channelOptions(channel: Channel) {
        val favorite = channel.id in preferences.favorites()
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += (if (favorite) "Remove from favorites" else "Add to favorites") to { preferences.toggle(channel); render(true) }
        if (favorite) {
            val index = preferences.favorites().indexOf(channel.id)
            if (index > 0) actions += "Move favorite up" to { preferences.move(channel.id, -1); render(true) }
            if (index < preferences.favorites().lastIndex) actions += "Move favorite down" to { preferences.move(channel.id, 1); render(true) }
        }
        AlertDialog.Builder(this).setTitle(channel.name).setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun openGuide() {
        if (guideScreen != null) return
        browser.visibility = View.GONE
        guideScreen = GuideScreen(this, ::play, ::closeGuide) { loadGuide(true) }.also { it.restoreState(guideState); guideState = null; root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        loadGuide()
    }
    private fun loadGuide(force: Boolean = false) {
        guideJob?.cancel()
        guideJob = guideScreen?.let { loadGuideInto(it, force) }
    }
    private fun loadGuideInto(screen: GuideScreen, force: Boolean): Job {
        screen.loading()
        return lifecycleScope.launch {
            try {
                val result = runInterruptible(Dispatchers.IO) { (application as IPTVApplication).guideStore.load(force) }
                screen.show(result, catalog.browse(Section.FRANCE))
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { screen.failed() }
        }
    }
    private fun openGuideOverlay() {
        val current = playerScreen?.channel ?: return
        if (current.country != "FR" || guideOverlay != null) return
        playerScreen?.setGuideVisible(true)
        guideOverlay = GuideScreen(this, { channel ->
            closeGuideOverlay()
            if (playerScreen?.channel?.id != channel.id) play(channel)
        }, ::closeGuideOverlay, overlayMode = true) { loadGuideOverlay(true) }.also {
            it.restoreState(guideOverlayState); guideOverlayState = null
            it.focusChannel(current.id)
            root.addView(it, FrameLayout.LayoutParams(-1, -1))
        }
        loadGuideOverlay()
    }
    private fun loadGuideOverlay(force: Boolean = false) {
        guideOverlayJob?.cancel()
        guideOverlayJob = guideOverlay?.let { loadGuideInto(it, force) }
    }
    private fun closeGuideOverlay() {
        guideOverlayJob?.cancel(); guideOverlayJob = null
        guideOverlay?.let(root::removeView); guideOverlay = null
        playerScreen?.setGuideVisible(false)
    }
    private fun closeGuide() {
        guideJob?.cancel(); guideScreen?.let(root::removeView); guideScreen = null
        browser.visibility = View.VISIBLE; guideTileFocused = true; render(true)
    }
    private fun play(channel: Channel) = play(channel, 0)
    private fun play(channel: Channel, stream: Int) {
        if (channel.streams.isEmpty()) {
            AlertDialog.Builder(this).setTitle(channel.name).setMessage("This channel is no longer in the playlist. Try Refresh.")
                .setPositiveButton("OK", null).show(); return
        }
        closeGuideOverlay()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
        selectedChannel = channel.id
        positions[section] = channel.id
        playerScreen?.let { it.release(); root.removeView(it) }
        browser.visibility = View.GONE
        guideScreen?.visibility = View.GONE
        playerScreen = PlayerScreen(this, channel, preferences, stream).also { root.addView(it, FrameLayout.LayoutParams(-1, -1)); it.start() }
    }
    private fun changeChannel(direction: Int) {
        val current = playerScreen?.channel ?: return
        val channels = when {
            guideScreen != null -> catalog.browse(Section.FRANCE)
            searchPanel.visibility == View.VISIBLE -> catalog.search(search.text.toString())
            section == Section.FAVORITES -> preferences.favoriteChannels(catalog.byId)
            else -> catalog.browse(section, country)
        }
        ChannelNavigation.adjacent(channels, current.id, direction)?.let(::play)
    }
    private fun closePlayer() {
        closeGuideOverlay()
        playerScreen?.let { it.release(); root.removeView(it) }; playerScreen = null
        if (guideScreen != null) { guideScreen?.visibility = View.VISIBLE; guideScreen?.restoreFocus() }
        else { browser.visibility = View.VISIBLE; render(true) }
    }
    // This overrides the public platform key hook; AndroidX's inherited annotation
    // incorrectly marks the bridge restricted. TV controls need interception before focus dispatch.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val visibleGuide = guideOverlay?.takeIf { it.isShown } ?: guideScreen?.takeIf { it.isShown }
        if (visibleGuide?.handlePageKey(event) == true) return true
        if (playerScreen != null && event.keyCode in setOf(KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_GUIDE)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (guideOverlay != null) closeGuideOverlay() else openGuideOverlay()
            }
            return true
        }
        if (playerScreen != null && event.keyCode in setOf(KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                changeChannel(if (event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP) 1 else -1)
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode != KeyEvent.KEYCODE_BACK && playerScreen?.handleKey(event.keyCode) == true) return true
        return super.dispatchKeyEvent(event)
    }
    override fun onStart() {
        super.onStart()
        if (searchPanel.visibility == View.VISIBLE) remoteSearch.start()
        playerScreen?.start()
    }
    override fun onStop() { updateJob?.cancel(); remoteSearch.stop(); playerScreen?.stop(); super.onStop() }
    override fun onDestroy() { browserMenu?.dismiss(); playerScreen?.release(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("guideOverlayOpen", guideOverlay != null)
        guideOverlay?.let { outState.putBundle("guideOverlayState", it.saveState()) }
        outState.putBoolean("guideOpen", guideScreen != null)
        guideScreen?.let { outState.putBundle("guideState", it.saveState()) }
        outState.putBoolean("searchOriginHome", searchOriginHome); outState.putString("searchOriginCountry", searchOriginCountry)
        outState.putString("searchOrigin", searchOrigin.name)
        outState.putString("homeFocus", countryFocus ?: "ALL"); outState.putBoolean("countryMode", countryMode); outState.putString("section", section.name); outState.putString("country", country)
        outState.putString("channel", selectedChannel); outState.putString("query", search.text.toString())
        playerScreen?.let { outState.putString("playing", it.channel.id); outState.putInt("stream", it.streamIndex) }
        super.onSaveInstanceState(outState)
    }
}
