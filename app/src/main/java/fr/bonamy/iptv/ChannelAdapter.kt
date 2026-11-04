package fr.bonamy.iptv

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.FrameLayout
import coil.load
import coil.dispose
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import fr.bonamy.iptv.core.Channel

internal class ChannelAdapter(
    private val play: (Channel) -> Unit,
    private val options: (Channel) -> Unit,
    private val focused: (String) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.Holder>() {
    var guideAvailable = false
    var openGuide: () -> Unit = {}
    var guideFocused: () -> Unit = {}
    var channels = emptyList<Channel>()
    var favorites = emptySet<String>()
    private var showCountries = false
    class Holder(val card: LinearLayout, val name: TextView, val detail: TextView, val flag: ImageView, val logo: ImageView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val name = context.label("", 15f).apply {
            bold(); maxLines = 2; gravity = Gravity.CENTER
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val detail = context.label("", 12f, MUTED).apply { visibility = View.GONE; gravity = Gravity.CENTER }
        val flag = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.GONE }
        val logo = ImageView(context).apply {
            id = R.id.channel_logo; scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xfff3f5f7.toInt()); setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val artwork = FrameLayout(context).apply {
            addView(logo, FrameLayout.LayoutParams(-1, -1))
            addView(flag, FrameLayout.LayoutParams(context.dp(24), context.dp(18), Gravity.TOP or Gravity.END)
                .apply { topMargin = context.dp(5); rightMargin = context.dp(5) })
        }
        val card = context.column().apply {
            isFocusable = true; isClickable = true; isLongClickable = true
            background = context.focusBackground(); setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
            addView(artwork, LinearLayout.LayoutParams(-1, context.dp(112)))
            addView(name, LinearLayout.LayoutParams(-1, context.dp(40)).apply { topMargin = context.dp(4) })
            addView(detail, LinearLayout.LayoutParams(-1, -2))
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                setMargins(0, 0, context.dp(12), context.dp(12))
            }
        }
        return Holder(card, name, detail, flag, logo)
    }

    override fun getItemCount() = channels.size + if (guideAvailable) 1 else 0
    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (guideAvailable && position == 0) {
            holder.name.text = "TV guide"
            holder.logo.dispose()
            holder.logo.setBackgroundColor(android.graphics.Color.WHITE)
            holder.logo.setImageResource(R.drawable.ic_tv_guide)
            holder.detail.visibility = View.GONE; holder.flag.visibility = View.GONE
            holder.card.contentDescription = "TV guide"
            holder.card.setOnClickListener { openGuide() }
            holder.card.setOnLongClickListener(null)
            holder.card.setOnFocusChangeListener { _, focused -> if (focused) guideFocused() }
            return
        }
        val channel = channels[position - if (guideAvailable) 1 else 0]
        holder.name.text = channel.name
        holder.logo.setBackgroundColor(0xfff3f5f7.toInt())
        holder.logo.load(channel.logoUrl) {
            size(holder.card.context.dp(112), holder.card.context.dp(112))
            placeholder(R.drawable.ic_channel_placeholder)
            error(R.drawable.ic_channel_placeholder)
            fallback(R.drawable.ic_channel_placeholder)
            crossfade(false)
        }
        holder.detail.text = if (channel.streams.isEmpty()) "Unavailable" else ""
        holder.detail.visibility = if (channel.streams.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        val flag = if (showCountries) CountryFlags.resources[channel.country] else null
        holder.flag.visibility = if (flag != null) View.VISIBLE else View.GONE
        flag?.let(holder.flag::setImageResource)
        holder.flag.contentDescription = channel.country?.let(CountryAdapter::name)
        holder.card.contentDescription = channel.name + if (channel.id in favorites) ", favorite" else ""
        holder.card.setOnClickListener { play(channel) }
        holder.card.setOnLongClickListener { options(channel); true }
        holder.card.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) focused(channel.id) }
    }
    override fun onViewRecycled(holder: Holder) {
        holder.logo.dispose()
        holder.logo.setImageResource(R.drawable.ic_channel_placeholder)
        super.onViewRecycled(holder)
    }
    fun replace(items: List<Channel>, ids: List<String>, countries: Boolean = false, guide: Boolean = false) {
        val favoriteIds = ids.toSet()
        if (channels === items && favorites == favoriteIds && showCountries == countries && guideAvailable == guide) return
        guideAvailable = guide; channels = items; favorites = favoriteIds; showCountries = countries; notifyDataSetChanged()
    }
}
