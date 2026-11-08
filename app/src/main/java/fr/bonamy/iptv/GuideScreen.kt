package fr.bonamy.iptv

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.*
import fr.bonamy.iptv.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/** A three-hour, time-proportional grid. Paging keeps every slot readable from a sofa. */
internal class GuideScreen(context: Context, private val play: (Channel) -> Unit, close: () -> Unit,
    private val overlayMode: Boolean = false, retry: () -> Unit) : LinearLayout(context) {
    private val zone = ZoneId.of("Europe/Paris")
    private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    private val date = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", java.util.Locale.ENGLISH).withZone(zone)
    private val duration = 3 * 60 * 60 * 1000L
    private var start = nowStart()
    private var followsNow = true
    private val tick = object : Runnable { override fun run() { if (isShown) render(table.hasFocus()) } }
    private var data: TvGuide? = null
    private var channels = emptyList<Channel>()
    private var selected: String? = null
    private var preferredChannel: String? = null
    private val closeButton = context.headerAction("TV guide", close).apply {
        textSize = 19f; bold(); gravity = Gravity.START or Gravity.CENTER_VERTICAL
        if (overlayMode) contentDescription = "Close TV guide"
    }
    private val period = context.label("", 13f, MUTED).apply { gravity = Gravity.CENTER_VERTICAL }
    private val detail = context.label("", 15f, MUTED).apply { maxLines = 2 }
    private val message = context.label("", 15f, MUTED).apply { visibility = View.GONE }
    private val loadingPane = context.column().apply {
        gravity = Gravity.CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Loading TV guide"
        addView(ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(context.dp(36), context.dp(36)))
        addView(context.label("Loading TV guide…", 18f).apply { gravity = Gravity.CENTER },
            LayoutParams(-2, -2).apply { topMargin = context.dp(18) })
    }
    private val navigation = context.row().apply {
        addView(context.headerAction("Earlier") { page(-1) })
        addView(context.headerAction("Now") { followsNow = true; start = nowStart(); selected = null; render(true) })
        addView(context.headerAction("Later") { page(1) })
        addView(context.headerAction("Refresh", retry))
    }
    private val scroll = ScrollView(context).apply { isFillViewport = true; isFocusable = false }
    private val table = context.column()
    private val rulerContainer = FrameLayout(context)
    private val retryButton = context.headerAction("Retry", retry).apply { visibility = View.GONE }
    init {
        id = if (overlayMode) R.id.tv_guide_overlay else R.id.tv_guide
        if (overlayMode) tag = "tv-guide-overlay"
        orientation = VERTICAL
        background = if (overlayMode) android.graphics.drawable.ColorDrawable(0xd907111d.toInt()) else context.browserBackground()
        setPadding(context.dp(36), context.dp(4), context.dp(36), context.dp(12))
        addView(context.row().apply {
            addView(closeButton, LayoutParams(-2, context.dp(48)))
            addView(period, LayoutParams(0, context.dp(48), 1f).apply { leftMargin = context.dp(16) })
            addView(navigation)
        })
        addView(message); addView(retryButton)
        addView(loadingPane, LayoutParams(-1, 0, 1f))
        addView(rulerContainer, LayoutParams(-1, context.dp(32)))
        scroll.addView(table); addView(scroll, LayoutParams(-1, 0, 1f))
        addView(detail, LayoutParams(-1, context.dp(40)).apply { topMargin = context.dp(8) })
        addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol && data != null) render(visibility == View.VISIBLE) }
    }
    private fun nowStart() = System.currentTimeMillis().let { it - it % (30 * 60 * 1000L) }
    fun loading() {
        val initial = data == null
        loadingPane.visibility = if (initial) View.VISIBLE else View.GONE
        navigation.visibility = if (initial) View.GONE else View.VISIBLE
        rulerContainer.visibility = if (initial) View.GONE else View.VISIBLE
        scroll.visibility = if (initial) View.GONE else View.VISIBLE
        detail.visibility = if (initial) View.GONE else View.VISIBLE
        message.text = if (initial) "" else "Refreshing guide…"
        message.visibility = if (initial) View.GONE else View.VISIBLE
        retryButton.visibility = View.GONE
        if (overlayMode && data == null) closeButton.requestFocusFromTouch()
    }
    fun failed() { loadingPane.visibility = View.GONE; message.text = "TV guide unavailable. Check your connection and retry."; message.visibility = View.VISIBLE; retryButton.visibility = View.VISIBLE; retryButton.requestFocus() }
    fun show(result: SavedGuide, available: List<Channel>) {
        data = result.guide; channels = available
        loadingPane.visibility = View.GONE; navigation.visibility = View.VISIBLE; rulerContainer.visibility = View.VISIBLE
        scroll.visibility = View.VISIBLE; detail.visibility = View.VISIBLE
        if (selected == null || result.guide.programs.none { key(it) == selected }) selectPreferredChannel()
        message.text = if (result.saved) "Saved schedule · Refresh to try again" else ""
        message.visibility = if (result.saved) View.VISIBLE else View.GONE
        retryButton.visibility = View.GONE; render(isShown)
    }
    fun focusChannel(id: String) { preferredChannel = id; if (data != null) { selectPreferredChannel(); render(isShown) } }
    private fun selectPreferredChannel() {
        val guide = data ?: return
        val id = guide.channels.firstOrNull { it.match(channels)?.id == preferredChannel }?.id ?: return
        val visible = guide.programs.filter { it.channelId == id && it.start < start + duration && it.end > start }
        val now = System.currentTimeMillis()
        selected = (visible.firstOrNull { it.start <= now && it.end > now } ?: visible.firstOrNull())?.let(::key)
    }
    private fun page(direction: Int) { followsNow = false; start += duration * direction; selected = null; render(true) }
    private fun key(p: GuideProgram) = "${p.channelId}:${p.start}"
    private fun render(focus: Boolean) {
        val guide = data ?: return
        if (followsNow) start = nowStart()
        val now = System.currentTimeMillis()
        val previous = guide.programs.firstOrNull { key(it) == selected && it.end <= now }
        if (previous != null) selected = guide.programs.firstOrNull {
            it.channelId == previous.channelId && it.start <= now && it.end > now
        }?.let(::key)
        period.text = "${date.format(Instant.ofEpochMilli(start))}–${time.format(Instant.ofEpochMilli(start + duration))}  ·  Paris"
        table.removeAllViews()
        val timelineWidth = (width - paddingLeft - paddingRight - context.dp(132)).coerceAtLeast(context.dp(400))
        val ruler = context.row()
        ruler.addView(View(context), LayoutParams(context.dp(132), context.dp(32)))
        repeat(6) { index -> ruler.addView(context.label(time.format(Instant.ofEpochMilli(start + index * duration / 6)), 12f, MUTED), LayoutParams(0, context.dp(32), 1f)) }
        rulerContainer.removeAllViews(); rulerContainer.addView(ruler)
        var initial: View? = null; var restored: View? = null
        var nextUpdate = if (followsNow) start + 30 * 60 * 1000L else Long.MAX_VALUE
        val mapped = guide.channels.mapNotNull { gc -> gc.match(channels)?.let { gc to it } }.sortedBy { (_, channel) -> channels.indexOf(channel) }
        val programs = guide.programs.groupBy { it.channelId }
        mapped.forEach { (gc, channel) ->
            val row = context.row()
            row.addView(context.label(channel.name, 15f).apply { bold(); maxLines = 2; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, context.dp(12), 0) }, LayoutParams(context.dp(132), context.dp(84)))
            val track = FrameLayout(context)
            val visible = programs[gc.id].orEmpty().filter { it.start < start + duration && it.end > start }
            if (visible.isEmpty()) track.addView(context.label("No schedule available", 14f, MUTED).apply { gravity = Gravity.CENTER_VERTICAL }, FrameLayout.LayoutParams(-1, -1))
            visible.forEach { p ->
                if (p.start > now) nextUpdate = min(nextUpdate, p.start)
                if (p.end > now) nextUpdate = min(nextUpdate, p.end)
                val left = ((max(p.start, start) - start).toDouble() / duration * timelineWidth).toInt()
                val right = ((min(p.end, start + duration) - start).toDouble() / duration * timelineWidth).toInt()
                val cell = context.column().apply {
                    id = View.generateViewId(); background = context.focusBackground(p.start <= now && p.end > now)
                    setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(8))
                    addView(context.label("${time.format(Instant.ofEpochMilli(p.start))}–${time.format(Instant.ofEpochMilli(p.end))}", 11f, MUTED).apply { maxLines = 1 })
                    addView(context.label(p.title, 15f).apply { bold(); maxLines = 2; includeFontPadding = true; ellipsize = android.text.TextUtils.TruncateAt.END }, LayoutParams(-1, -2).apply { topMargin = context.dp(5) })
                    contentDescription = "${channel.name}, ${p.title}, ${time.format(Instant.ofEpochMilli(p.start))}"
                    setOnClickListener { if (p.end > System.currentTimeMillis()) play(channel) }
                    isEnabled = p.end > now; isClickable = p.end > now; isFocusable = p.end > now; alpha = if (p.end > now) 1f else 0.45f
                    setOnFocusChangeListener { _, hasFocus -> if (hasFocus) { selected = key(p); detail.text = "${channel.name} · ${p.title}\n${p.description}" } }
                }
                track.addView(cell, FrameLayout.LayoutParams((right - left - context.dp(3)).coerceAtLeast(1), -1).apply { leftMargin = left })
                if (initial == null && p.end > now) initial = cell
                if (p.end > now && key(p) == selected) restored = cell
            }
            row.addView(track, LayoutParams(0, context.dp(84), 1f))
            table.addView(row, LayoutParams(-1, context.dp(90)))
        }
        if (mapped.isEmpty()) { message.text = "No guide channels in the current library."; message.visibility = View.VISIBLE }
        if (focus) (restored ?: initial ?: closeButton).post { (restored ?: initial ?: closeButton).requestFocusFromTouch() }
        removeCallbacks(tick)
        if (isShown) postDelayed(tick, min(30_000L, (nextUpdate - now).coerceAtLeast(1L)))
    }
    fun saveState() = android.os.Bundle().apply { putLong("start", start); putString("selected", selected); putBoolean("followsNow", followsNow) }
    fun restoreState(state: android.os.Bundle?) { state?.let { start = it.getLong("start", start); selected = it.getString("selected"); followsNow = it.getBoolean("followsNow", true) } }
    fun restoreFocus() { if (data != null) render(true) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
}
