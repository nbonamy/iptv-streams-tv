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
internal class GuideScreen(context: Context, private val play: (Channel) -> Unit, close: () -> Unit, retry: () -> Unit) : LinearLayout(context) {
    private val zone = ZoneId.of("Europe/Paris")
    private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    private val date = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", java.util.Locale.ENGLISH).withZone(zone)
    private val duration = 3 * 60 * 60 * 1000L
    private var start = nowStart()
    private var data: TvGuide? = null
    private var channels = emptyList<Channel>()
    private var selected: String? = null
    private val period = context.label("", 17f).apply { bold(); gravity = Gravity.CENTER_VERTICAL }
    private val detail = context.label("Select a program to watch its channel live", 15f, MUTED).apply { maxLines = 2 }
    private val message = context.label("Loading TV guide…", 18f, MUTED)
    private val scroll = ScrollView(context).apply { isFillViewport = true; isFocusable = false }
    private val table = context.column()
    private val rulerContainer = FrameLayout(context)
    private val retryButton = context.headerAction("Retry", retry).apply { visibility = View.GONE }
    init {
        id = R.id.tv_guide; orientation = VERTICAL; background = context.browserBackground()
        setPadding(context.dp(36), 0, context.dp(36), context.dp(24))
        addView(context.row().apply {
            addView(context.headerAction("IPTV  /  France  /  TV guide", close).apply { textSize = 17f; gravity = Gravity.START or Gravity.CENTER_VERTICAL }, LayoutParams(0, context.dp(56), 1f))
            addView(context.headerAction("Refresh", retry))
        })
        addView(context.row().apply {
            addView(period, LayoutParams(0, context.dp(48), 1f))
            addView(context.headerAction("Earlier") { page(-1) })
            addView(context.headerAction("Now") { start = nowStart(); render(true) })
            addView(context.headerAction("Later") { page(1) })
        })
        addView(detail, LayoutParams(-1, context.dp(48)).apply { topMargin = context.dp(8); bottomMargin = context.dp(12) })
        addView(message); addView(retryButton)
        addView(rulerContainer, LayoutParams(-1, context.dp(32)))
        scroll.addView(table); addView(scroll, LayoutParams(-1, 0, 1f))
        addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol && data != null) render(visibility == View.VISIBLE) }
    }
    private fun nowStart() = System.currentTimeMillis().let { it - it % (30 * 60 * 1000L) }
    fun loading() { message.text = "Loading TV guide…"; message.visibility = View.VISIBLE; retryButton.visibility = View.GONE }
    fun failed() { message.text = "TV guide unavailable. Check your connection and retry."; message.visibility = View.VISIBLE; retryButton.visibility = View.VISIBLE; retryButton.requestFocus() }
    fun show(result: SavedGuide, available: List<Channel>) {
        data = result.guide; channels = available
        message.text = if (result.saved) "Saved schedule · Refresh to try again" else ""
        message.visibility = if (result.saved) View.VISIBLE else View.GONE
        retryButton.visibility = View.GONE; render(true)
    }
    private fun page(direction: Int) { start += duration * direction; selected = null; render(true) }
    private fun key(p: GuideProgram) = "${p.channelId}:${p.start}"
    private fun render(focus: Boolean) {
        val guide = data ?: return
        period.text = "${date.format(Instant.ofEpochMilli(start))}–${time.format(Instant.ofEpochMilli(start + duration))}  ·  Paris"
        table.removeAllViews()
        val timelineWidth = (width - paddingLeft - paddingRight - context.dp(132)).coerceAtLeast(context.dp(400))
        val ruler = context.row()
        ruler.addView(context.label("CHANNEL", 11f, MUTED), LayoutParams(context.dp(132), context.dp(32)))
        repeat(6) { index -> ruler.addView(context.label(time.format(Instant.ofEpochMilli(start + index * duration / 6)), 12f, MUTED), LayoutParams(0, context.dp(32), 1f)) }
        rulerContainer.removeAllViews(); rulerContainer.addView(ruler)
        var initial: View? = null; var restored: View? = null
        val mapped = guide.channels.mapNotNull { gc -> gc.match(channels)?.let { gc to it } }.sortedBy { (_, channel) -> channels.indexOf(channel) }
        val programs = guide.programs.groupBy { it.channelId }
        mapped.forEach { (gc, channel) ->
            val row = context.row()
            row.addView(context.label(channel.name, 15f).apply { bold(); maxLines = 2; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, context.dp(12), 0) }, LayoutParams(context.dp(132), context.dp(70)))
            val track = FrameLayout(context)
            val visible = programs[gc.id].orEmpty().filter { it.start < start + duration && it.end > start }
            if (visible.isEmpty()) track.addView(context.label("No schedule available", 14f, MUTED).apply { gravity = Gravity.CENTER_VERTICAL }, FrameLayout.LayoutParams(-1, -1))
            visible.forEach { p ->
                val left = ((max(p.start, start) - start).toDouble() / duration * timelineWidth).toInt()
                val right = ((min(p.end, start + duration) - start).toDouble() / duration * timelineWidth).toInt()
                val cell = context.column().apply {
                    id = View.generateViewId(); isFocusable = true; isClickable = true; background = context.focusBackground(p.start <= System.currentTimeMillis() && p.end > System.currentTimeMillis())
                    setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(8))
                    addView(context.label("${time.format(Instant.ofEpochMilli(p.start))}–${time.format(Instant.ofEpochMilli(p.end))}", 11f, MUTED).apply { maxLines = 1 })
                    addView(context.label(p.title, 15f).apply { bold(); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, LayoutParams(-1, -2).apply { topMargin = context.dp(5) })
                    contentDescription = "${channel.name}, ${p.title}, ${time.format(Instant.ofEpochMilli(p.start))}"
                    setOnClickListener { play(channel) }
                    setOnFocusChangeListener { _, hasFocus -> if (hasFocus) { selected = key(p); detail.text = "${channel.name} · ${p.title}\n${p.description}" } }
                }
                track.addView(cell, FrameLayout.LayoutParams((right - left - context.dp(3)).coerceAtLeast(1), -1).apply { leftMargin = left })
                if (initial == null) initial = cell
                if (key(p) == selected) restored = cell
            }
            row.addView(track, LayoutParams(0, context.dp(70), 1f))
            table.addView(row, LayoutParams(-1, context.dp(76)))
        }
        if (mapped.isEmpty()) { message.text = "No guide channels in the current library."; message.visibility = View.VISIBLE }
        if (focus) (restored ?: initial)?.post { (restored ?: initial)?.requestFocusFromTouch() }
    }
    fun saveState() = android.os.Bundle().apply { putLong("start", start); putString("selected", selected) }
    fun restoreState(state: android.os.Bundle?) { state?.let { start = it.getLong("start", start); selected = it.getString("selected") } }
    fun restoreFocus() { if (data != null) render(true) }
}
