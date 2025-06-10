package eu.kanade.tachiyomi.ui.library

import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.setCards

/**
 * Generic class used to hold the displayed data of a manga in the library.
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 */
abstract class LibraryHolder(
    view: View,
    val adapter: LibraryCategoryAdapter,
) : BaseFlexibleViewHolder(view, adapter) {

    protected val color = ColorUtils.setAlphaComponent(itemView.context.getResourceColor(R.attr.colorSecondary), 75)

    init {
        val card = itemView.findViewById<MaterialCardView>(R.id.card)
        val badgeView = itemView.findViewById<LibraryBadge>(R.id.unread_download_badge)
        if (card != null && badgeView != null) {
            setCards(adapter.showOutline, card, badgeView)
        }
    }

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param item the manga item to bind.
     */
    abstract fun onSetValues(item: LibraryItem)

    fun setUnreadBadge(badge: LibraryBadge, item: LibraryMangaItem) {
        val showTotal = item.header.category.sortingMode() == LibrarySort.TotalChapters
        badge.setUnreadDownload(
            when {
                showTotal -> item.manga.totalChapters
                item.unreadType == 2 -> item.manga.unread
                item.unreadType == 1 -> if (item.manga.unread > 0) -1 else -2
                else -> -2
            },
            when {
                item.downloadCount == -1 -> -1
                item.manga.manga.isLocal() -> -2
                else -> item.downloadCount
            },
            showTotal,
            item.sourceLanguage,
            this is LibraryGridHolder,
        )

        // Update rating badge
        itemView.findViewById<MaterialTextView>(R.id.rating_badge)?.let { ratingBadge ->
            ratingBadge.isVisible = item.rating != null
            if (item.rating != null) {
                ratingBadge.text = String.format("%.1f", item.rating)
                ratingBadge.setTextColor(itemView.context.getResourceColor(R.attr.colorOnPrimary))
                ratingBadge.setBackgroundColor(itemView.context.getResourceColor(R.attr.colorPrimary))
            }
        }
    }

    fun setReadingButton(item: LibraryMangaItem) {
        itemView.findViewById<View>(R.id.play_layout)?.isVisible =
            item.manga.unread > 0 && !item.hideReadingButton
    }

    /**
     * Called when an item is released.
     *
     * @param position The position of the released item.
     */
    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        (adapter as? LibraryCategoryAdapter)?.libraryListener?.onItemReleased(position)
    }

    override fun onLongClick(view: View?): Boolean {
        return if (adapter.isLongPressDragEnabled) {
            val manga = (adapter.getItem(flexibleAdapterPosition) as? LibraryMangaItem)?.manga
            if (manga != null && !isDraggable) {
                adapter.mItemLongClickListener.onItemLongClick(flexibleAdapterPosition)
                toggleActivation()
                true
            } else {
                super.onLongClick(view)
                false
            }
        } else {
            super.onLongClick(view)
        }
    }
}
