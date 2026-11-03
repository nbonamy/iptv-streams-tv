package fr.bonamy.iptv

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

internal data class CountryTile(val code: String?, val name: String)
internal class CountryAdapter(private val focused: (String?) -> Unit, private val select: (String?) -> Unit) : RecyclerView.Adapter<CountryAdapter.Holder>() {
    var countries = emptyList<CountryTile>()
        set(value) { field = value; notifyDataSetChanged() }
    class Holder(val card: LinearLayout, val code: TextView, val name: TextView, val icon: ImageView) : RecyclerView.ViewHolder(card)
    override fun getItemCount() = countries.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val code = context.label("", 26f, ACCENT).apply { bold(); gravity = Gravity.CENTER }
        val name = context.label("", 13f).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val icon = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val card = context.column().apply {
            isFocusable = true; isClickable = true; gravity = Gravity.CENTER
            background = context.focusBackground(); setPadding(context.dp(18), context.dp(10), context.dp(18), context.dp(10))
            addView(icon, LinearLayout.LayoutParams(context.dp(32), context.dp(32)).apply { bottomMargin = context.dp(10) })
            addView(code, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(10) })
            addView(name, LinearLayout.LayoutParams(-1, -2))
            layoutParams = RecyclerView.LayoutParams(-1, context.dp(94)).apply { setMargins(0, 0, context.dp(12), context.dp(12)) }
        }
        return Holder(card, code, name, icon)
    }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val country = countries[position]
        val flag = CountryFlags.resources[country.code]
        val icon = if (country.code == "FAVORITES") R.drawable.ic_favorite else flag
        holder.icon.layoutParams = (holder.icon.layoutParams as LinearLayout.LayoutParams).apply {
            width = holder.card.context.dp(if (flag != null) 40 else 32)
            height = holder.card.context.dp(if (flag != null) 30 else 32)
        }
        holder.code.visibility = if (icon == null) View.VISIBLE else View.GONE
        holder.icon.visibility = if (icon == null) View.GONE else View.VISIBLE
        icon?.let(holder.icon::setImageResource)
        holder.code.text = country.code ?: "ALL"
        holder.name.text = country.name
        holder.card.contentDescription = country.name
        holder.card.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) focused(country.code) }
        holder.card.setOnClickListener { select(country.code) }
    }
    companion object {
        fun name(code: String) = if (code == "ZZ") "Unassigned" else Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)
        fun tiles(codes: List<String>): List<CountryTile> {
            val priorities = listOf(CountryTile("FAVORITES", "Favorites"), CountryTile("FR", "France"),
                CountryTile("US", "USA"), CountryTile("GB", "UK"), CountryTile("CA", "Canada"))
            return priorities + codes.distinct().filterNot { it in setOf("FR", "US", "GB", "CA") }.sortedBy(::name).map { CountryTile(it, name(it)) }
        }
    }
}
