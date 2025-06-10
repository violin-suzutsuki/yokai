package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.MaterialShapeDrawable
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.databinding.UnreadDownloadBadgeBinding
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.makeShapeCorners

class LibraryBadge @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    MaterialCardView(context, attrs) {

    private lateinit var binding: UnreadDownloadBadgeBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = UnreadDownloadBadgeBinding.bind(this)

        shapeAppearanceModel = makeShapeCorners(radius, radius)
    }

    fun setUnreadDownload(
        unread: Int,
        downloads: Int,
        showTotalChapters: Boolean,
        lang: String?,
        changeShape: Boolean,
    ) {
        // Update the unread count and its visibility.

        val unreadBadgeBackground = if (showTotalChapters) {
            context.contextCompatColor(R.color.total_badge)
        } else {
            context.getResourceColor(R.attr.colorSecondary)
        }

        with(binding.unreadText) {
            isVisible = unread > 0 || unread == -1 || showTotalChapters
            if (!isVisible) {
                return@with
            }
            text = if (unread == -1) "0" else unread.toString()
            setTextColor(
                // hide the badge text when preference is only show badge
                when {
                    unread == -1 && !showTotalChapters -> unreadBadgeBackground
                    showTotalChapters -> context.contextCompatColor(R.color.total_badge_text)
                    else -> context.getResourceColor(R.attr.colorOnSecondary)
                },
            )
            setBackgroundColor(unreadBadgeBackground)
        }

        // Update the download count or local status and its visibility.
        with(binding.downloadText) {
            isVisible = downloads == -2 || downloads > 0
            if (!isVisible) {
                return@with
            }
            text = if (downloads == -2) {
                context.getString(MR.strings.local)
            } else {
                downloads.toString()
            }

            setTextColor(context.getResourceColor(R.attr.colorOnTertiary))
            setBackgroundColor(context.getResourceColor(R.attr.colorTertiary))
        }

        with(binding.langImage) {
            isVisible = !lang.isNullOrBlank()
            if (!lang.isNullOrBlank()) {
                val flagId = resources.getIdentifier(
                    "ic_flag_${lang.replace("-", "_")}",
                    "drawable",
                    context.packageName,
                ).takeIf { it != 0 } ?: (
                    if (lang.contains("-")) {
                        resources.getIdentifier(
                            "ic_flag_${lang.split("-").first()}",
                            "drawable",
                            context.packageName,
                        ).takeIf { it != 0 }
                    } else {
                        null
                    }
                    )
                if (flagId != null) {
                    setImageResource(flagId)
                } else {
                    isVisible = false
                }
            }
        }

        binding.unreadAngle.isVisible = false
        binding.downloadAngle.isVisible = false
        val visibleChildren: List<View> = (0 until binding.cardConstraint.childCount).mapNotNull {
            binding.cardConstraint.getChildAt(it)
        }.filter { it.isVisible }
        shapeAppearanceModel = shapeAppearanceModel.withCornerSize(radius)
        if (changeShape) {
            if (visibleChildren.size == 1 && binding.unreadText.isVisible && unread == -1) {
                binding.unreadText.setBackgroundColor(unreadBadgeBackground)
                shapeAppearanceModel = shapeAppearanceModel.withCornerSize(radius)
            } else {
                shapeAppearanceModel = makeShapeCorners(radius, radius)
                visibleChildren.forEachIndexed { index, view ->
                    val startRadius = if (index == 0) radius else 0f
                    val endRadius = if (index == visibleChildren.size - 1) radius else 0f
                    val bgColor = when (view) {
                        binding.downloadText -> context.getResourceColor(R.attr.colorTertiary)
                        binding.unreadText -> unreadBadgeBackground
                        else -> context.getResourceColor(R.attr.background)
                    }
                    if (view is ShapeableImageView) {
                        view.shapeAppearanceModel =
                            makeShapeCorners(topStart = startRadius, bottomEnd = endRadius)
                    } else {
                        view.background = MaterialShapeDrawable(
                            makeShapeCorners(topStart = startRadius, bottomEnd = endRadius),
                        ).apply {
                            this.fillColor = ColorStateList.valueOf(bgColor)
                        }
                    }
                }
            }
        }

        // Show the badge card if unread or downloads exists
        isVisible = visibleChildren.isNotEmpty()

        // Show the angles divider if both unread and downloads exists
        binding.unreadAngle.isVisible =
            binding.unreadText.isVisible && visibleChildren.size > 1
        binding.downloadAngle.isVisible =
            binding.downloadText.isVisible && binding.langImage.isVisible

        binding.unreadAngle.setColorFilter(unreadBadgeBackground)
        if (binding.unreadAngle.isVisible) {
            binding.downloadText.updatePaddingRelative(end = 8.dpToPx)
            binding.unreadText.updatePaddingRelative(start = 2.dpToPx)
        } else {
            binding.downloadText.updatePaddingRelative(end = 5.dpToPx)
            binding.unreadText.updatePaddingRelative(start = 5.dpToPx)
        }
        binding.downloadText.updatePaddingRelative(
            start = if (binding.downloadAngle.isVisible) {
                8.dpToPx
            } else {
                5.dpToPx
            },
        )
    }

    fun setChapters(chapters: Int?) {
        setUnreadDownload(chapters ?: 0, 0, chapters != null, null, true)
    }

    fun setInLibrary(inLibrary: Boolean) {
        this.isVisible = inLibrary
        binding.unreadAngle.isVisible = false
        binding.unreadText.updatePaddingRelative(start = 5.dpToPx)
        binding.unreadText.isVisible = inLibrary
        binding.unreadText.text = context.getString(MR.strings.in_library)
        binding.unreadText.background =
            MaterialShapeDrawable(makeShapeCorners(radius, radius)).apply {
                this.fillColor =
                    ColorStateList.valueOf(context.getResourceColor(R.attr.colorSecondary))
            }
    }
}
