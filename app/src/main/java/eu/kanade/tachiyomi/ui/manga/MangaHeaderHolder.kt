package eu.kanade.tachiyomi.ui.manga

import android.animation.AnimatorInflater
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.text.buildSpannedString
import androidx.core.text.scale
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.transition.TransitionSet
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import coil3.asDrawable
import coil3.request.CachePolicy
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.databinding.ChapterHeaderItemBinding
import eu.kanade.tachiyomi.databinding.MangaHeaderItemBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.nameBasedOnEnabledLanguages
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.toNormalized
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.view.resetStrokeColor
import yokai.i18n.MR
import yokai.util.coil.loadManga
import yokai.util.lang.getString
import android.R as AR
import android.app.AlertDialog
import android.widget.RatingBar
import com.google.android.material.textfield.TextInputEditText

@SuppressLint("ClickableViewAccessibility")
class MangaHeaderHolder(
    view: View,
    private val adapter: MangaDetailsAdapter,
    startExpanded: Boolean,
    private val isTablet: Boolean = false,
) : BaseFlexibleViewHolder(view, adapter) {

    val binding: MangaHeaderItemBinding? = try {
        MangaHeaderItemBinding.bind(view)
    } catch (e: Exception) {
        null
    }
    private val chapterBinding: ChapterHeaderItemBinding? = try {
        ChapterHeaderItemBinding.bind(view)
    } catch (e: Exception) {
        null
    }

    private var showReadingButton = true
    private var showMoreButton = true
    var hadSelection = false
    private var canCollapse = true

    init {

        if (binding == null) {
            with(chapterBinding) {
                this ?: return@with
                chapterLayout.setOnClickListener { adapter.delegate.showChapterFilter() }
            }
        }
        with(binding) {
            this ?: return@with
            startReadingButton.transitionName = "details start reading transition"
            chapterLayout.setOnClickListener { adapter.delegate.showChapterFilter() }
            startReadingButton.setOnClickListener { adapter.delegate.readNextChapter(it) }
            topView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = adapter.delegate.topCoverHeight()
            }
            moreButton.setOnClickListener {
                expandDesc(true)
            }
            mangaSummary.setOnClickListener {
                if (moreButton.isVisible) {
                    expandDesc(true)
                } else if (!hadSelection) {
                    collapseDesc(true)
                } else {
                    hadSelection = false
                }
            }
            mangaSummary.setOnLongClickListener {
                if (mangaSummary.isTextSelectable && !adapter.recyclerView.canScrollVertically(
                        -1,
                    )
                ) {
                    (adapter.delegate as MangaDetailsController).binding.swipeRefresh.isEnabled =
                        false
                }
                false
            }
            mangaSummary.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    view.requestFocus()
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    hadSelection = mangaSummary.hasSelection()
                    (adapter.delegate as MangaDetailsController).binding.swipeRefresh.isEnabled =
                        true
                }
                false
            }
            if (!itemView.resources.isLTR) {
                moreBgGradient.rotation = 180f
            }
            lessButton.setOnClickListener {
                collapseDesc(true)
            }

            webviewButton.setOnClickListener { adapter.delegate.openInWebView() }
            shareButton.setOnClickListener { adapter.delegate.prepareToShareManga() }
            favoriteButton.setOnClickListener {
                adapter.delegate.favoriteManga(false)
            }
            favoriteButton.setOnLongClickListener {
                adapter.delegate.favoriteManga(true)
                true
            }
            title.setOnClickListener { view ->
                title.text?.toString()?.toNormalized()?.let {
                    adapter.delegate.showFloatingActionMode(view as TextView, it)
                }
            }
            title.setOnLongClickListener {
                title.text?.toString()?.toNormalized()?.let {
                    adapter.delegate.copyContentToClipboard(it, MR.strings.title)
                }
                true
            }
            mangaAuthor.setOnClickListener { view ->
                mangaAuthor.text?.toString()?.let {
                    adapter.delegate.showFloatingActionMode(view as TextView, it)
                }
            }
            mangaAuthor.setOnLongClickListener {
                mangaAuthor.text?.toString()?.let {
                    adapter.delegate.copyContentToClipboard(it, MR.strings.author)
                }
                true
            }
            mangaSummary.customSelectionActionModeCallback = adapter.delegate.customActionMode(mangaSummary)
            applyBlur()
            mangaCover.setOnClickListener { adapter.delegate.zoomImageFromThumb(coverCard) }
            trackButton.setOnClickListener { adapter.delegate.showTrackingSheet() }
            if (startExpanded) {
                expandDesc()
            } else {
                collapseDesc()
            }
            if (isTablet) {
                chapterLayout.isVisible = false
                expandDesc()
            }
        }
    }

    private fun applyBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding?.backdrop?.alpha = 0.2f
            binding?.backdrop?.setRenderEffect(
                RenderEffect.createBlurEffect(
                    20f,
                    20f,
                    Shader.TileMode.MIRROR,
                ),
            )
        }
    }

    private fun expandDesc(animated: Boolean = false) {
        binding ?: return
        if (binding.moreButton.visibility == View.VISIBLE || isTablet) {
            androidx.transition.TransitionManager.endTransitions(adapter.controller.binding.recycler)
            binding.mangaSummary.maxLines = Integer.MAX_VALUE
            binding.mangaSummary.setTextIsSelectable(true)
            setDescription()
            binding.mangaGenresTags.isVisible = true
            binding.lessButton.isVisible = !isTablet
            binding.moreButtonGroup.isVisible = false
            if (animated) {
                val animVector = AnimatedVectorDrawableCompat.create(binding.root.context, R.drawable.anim_expand_more_to_less)
                binding.lessButton.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, animVector, null)
                animVector?.start()
            }
            binding.title.maxLines = Integer.MAX_VALUE
            binding.mangaAuthor.maxLines = Integer.MAX_VALUE
            binding.mangaSummary.requestFocus()
            if (animated) {
                val transition = TransitionSet()
                    .addTransition(androidx.transition.ChangeBounds())
                    .addTransition(androidx.transition.Fade())
                    .addTransition(androidx.transition.Slide())
                transition.duration = binding.root.resources.getInteger(
                    AR.integer.config_shortAnimTime,
                ).toLong()
                androidx.transition.TransitionManager.beginDelayedTransition(
                    adapter.controller.binding.recycler,
                    transition,
                )
            }
        }
    }

    private fun collapseDesc(animated: Boolean = false) {
        binding ?: return
        if (isTablet || !canCollapse) return
        binding.moreButtonGroup.isVisible = !isTablet
        if (animated) {
            androidx.transition.TransitionManager.endTransitions(adapter.controller.binding.recycler)
            val animVector = AnimatedVectorDrawableCompat.create(
                binding.root.context,
                R.drawable.anim_expand_less_to_more,
            )
            binding.moreButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                animVector,
                null,
            )
            animVector?.start()
            val transition = TransitionSet()
                .addTransition(androidx.transition.ChangeBounds())
                .addTransition(androidx.transition.Fade())
            transition.duration = binding.root.resources.getInteger(
                AR.integer.config_shortAnimTime,
            ).toLong()
            androidx.transition.TransitionManager.beginDelayedTransition(
                adapter.controller.binding.recycler,
                transition,
            )
        }
        binding.mangaSummary.setTextIsSelectable(false)
        binding.mangaSummary.isClickable = true
        binding.mangaSummary.maxLines = 3
        setDescription()
        binding.mangaGenresTags.isVisible = isTablet
        binding.lessButton.isVisible = false
        binding.title.maxLines = 4
        binding.mangaAuthor.maxLines = 2
        adapter.recyclerView.post {
            adapter.delegate.updateScroll()
        }
    }

    private fun setDescription() {
        if (binding != null) {
            val desc = adapter.controller.mangaPresenter().manga.description
            binding.mangaSummary.text = when {
                desc.isNullOrBlank() -> itemView.context.getString(MR.strings.no_description)
                binding.mangaSummary.maxLines != Int.MAX_VALUE -> desc.replace(
                    Regex(
                        "[\\r\\n\\s*]{2,}",
                        setOf(RegexOption.MULTILINE),
                    ),
                    "\n",
                )
                else -> desc.trim()
            }
        }
    }

    fun bindChapters() {
        val presenter = adapter.delegate.mangaPresenter()
        val count = presenter.chapters.size
        if (binding != null) {
            binding.chaptersTitle.text =
                itemView.context.getString(MR.plurals.chapters_plural, count, count)
            binding.filtersText.text = presenter.currentFilters()
        } else if (chapterBinding != null) {
            chapterBinding.chaptersTitle.text =
                itemView.context.getString(MR.plurals.chapters_plural, count, count)
            chapterBinding.filtersText.text = presenter.currentFilters()
        }
    }

    @SuppressLint("SetTextI18n", "StringFormatInvalid")
    fun bind(item: MangaHeaderItem) {
        val presenter = adapter.delegate.mangaPresenter()
        val manga = presenter.manga
        val libraryManga = presenter.manga

        if (binding == null) {
            if (chapterBinding != null) {
                val count = presenter.chapters.size
                chapterBinding.chaptersTitle.text =
                    itemView.context.getString(MR.plurals.chapters_plural, count, count)
                chapterBinding.filtersText.text = presenter.currentFilters()
                if (adapter.preferences.themeMangaDetails().get()) {
                    val accentColor = adapter.delegate.accentColor() ?: return
                    chapterBinding.filterButton.imageTintList = ColorStateList.valueOf(accentColor)
                }
            }
            return
        }
        binding.title.text = manga.title

        setGenreTags(binding, manga)

        if (manga.hasSameAuthorAndArtist) {
            binding.mangaAuthor.text = manga.author?.trim()
        } else {
            binding.mangaAuthor.text = listOfNotNull(manga.author?.trim(), manga.artist?.trim()).joinToString(", ")
        }
        setDescription()

        binding.mangaSummary.post {
            if (binding.subItemGroup.isVisible) {
                if (binding.mangaSummary.lineCount < 3 && manga.genre.isNullOrBlank() &&
                    binding.moreButton.isVisible && manga.initialized
                ) {
                    expandDesc()
                    binding.lessButton.isVisible = false
                    showMoreButton = binding.lessButton.isVisible
                    canCollapse = false
                }
            }
            if (adapter.hasFilter()) {
                collapse()
            } else {
                expand()
            }
        }
        binding.mangaSummaryLabel.text = itemView.context.getString(
            MR.strings.about_this_,
            manga.seriesType(itemView.context),
        )
        with(binding.favoriteButton) {
            icon = ContextCompat.getDrawable(
                itemView.context,
                when {
                    item.isLocked -> R.drawable.ic_lock_24dp
                    manga.favorite -> R.drawable.ic_heart_24dp
                    else -> R.drawable.ic_heart_outline_24dp
                },
            )
            text = itemView.context.getString(
                when {
                    item.isLocked -> MR.strings.unlock
                    manga.favorite -> MR.strings.in_library
                    else -> MR.strings.add_to_library
                },
            )
            checked(!item.isLocked && manga.favorite)
            adapter.delegate.setFavButtonPopup(this)
        }

        val rating: Double = presenter.rating!!

        binding.rateButton?.apply {
            isVisible = manga.favorite
            icon = ContextCompat.getDrawable(
                itemView.context,
                when {
                    rating > 0 -> R.drawable.ic_star_24dp
                    else -> R.drawable.ic_star_outline_24dp
                },
            )
            text = when {
                rating > 0 -> String.format("%.1f", rating)
                else -> MR.strings.rate.getString(itemView.context)
            }
            checked(rating > 0)
            setOnClickListener {
                val dialog = MaterialAlertDialogBuilder(itemView.context)
                    .setTitle(MR.strings.rating.getString(itemView.context))
                    .setView(R.layout.dialog_rating)
                    .setPositiveButton(MR.strings.action_ok.getString(itemView.context)) { dialog, _ ->
                        val ratingInput = (dialog as androidx.appcompat.app.AlertDialog).findViewById<TextInputEditText>(R.id.rating_input)
                        val ratingText = ratingInput?.text?.toString()
                        if (!ratingText.isNullOrBlank()) {
                            val rating = ratingText.toDoubleOrNull()
                            if (rating != null && rating in 0.0..10.0) {
                                adapter.delegate.mangaPresenter().setMangaRating(rating)
                                updateRating(rating)
                            }
                        }
                    }
                    .setNegativeButton(MR.strings.action_cancel.getString(itemView.context), null)
                    .create()

                dialog.setOnShowListener {
                    val ratingInput = (dialog as androidx.appcompat.app.AlertDialog).findViewById<TextInputEditText>(R.id.rating_input)
                    ratingInput?.setText(when {
                        rating > 0 -> String.format("%.1f", rating)
                        else -> ""
                    })
                }

                dialog.show()
            }
        }

        binding.trueBackdrop.setBackgroundColor(
            adapter.delegate.coverColor()
                ?: itemView.context.getResourceColor(R.attr.background),
        )

        val tracked = presenter.isTracked() && !item.isLocked

        with(binding.trackButton) {
            isVisible = presenter.hasTrackers()
            text = itemView.context.getString(
                if (tracked) {
                    MR.strings.tracked
                } else {
                    MR.strings.tracking
                },
            )

            icon = ContextCompat.getDrawable(
                itemView.context,
                if (tracked) R.drawable.ic_check_24dp else R.drawable.ic_sync_24dp,
            )
            checked(tracked)
        }

        with(binding.startReadingButton) {
            val nextChapter = presenter.getNextUnreadChapter()
            isVisible = presenter.chapters.isNotEmpty() && !item.isLocked && !adapter.hasFilter()
            showReadingButton = isVisible
            isEnabled = (nextChapter != null)
            text = if (nextChapter != null) {
                val number = adapter.decimalFormat.format(nextChapter.chapter_number.toDouble())
                if (nextChapter.chapter_number > 0) {
                    context.getString(
                        if (nextChapter.last_page_read > 0) {
                            MR.strings.continue_reading_chapter_
                        } else {
                            MR.strings.start_reading_chapter_
                        },
                        number,
                    )
                } else {
                    context.getString(
                        if (nextChapter.last_page_read > 0) {
                            MR.strings.continue_reading
                        } else {
                            MR.strings.start_reading
                        },
                    )
                }
            } else {
                context.getString(MR.strings.all_chapters_read)
            }
        }

        val count = presenter.chapters.size
        binding.chaptersTitle.text = itemView.context.getString(MR.plurals.chapters_plural, count, count)

        binding.topView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = adapter.delegate.topCoverHeight()
        }

        binding.mangaStatus.isVisible = manga.status != 0
        binding.mangaStatus.text = (
            itemView.context.getString(
                when (manga.status) {
                    SManga.ONGOING -> MR.strings.ongoing
                    SManga.COMPLETED -> MR.strings.completed
                    SManga.LICENSED -> MR.strings.licensed
                    SManga.PUBLISHING_FINISHED -> MR.strings.publishing_finished
                    SManga.CANCELLED -> MR.strings.cancelled
                    SManga.ON_HIATUS -> MR.strings.on_hiatus
                    else -> MR.strings.unknown_status
                },
            )
            )
        with(binding.mangaSource) {
            val enabledLanguages = presenter.preferences.enabledLanguages().get()

            text = buildSpannedString {
                append(presenter.source.nameBasedOnEnabledLanguages(enabledLanguages))
                if (presenter.source is SourceManager.StubSource &&
                    presenter.source.name != presenter.source.id.toString()
                ) {
                    scale(0.9f) {
                        append(" (${context.getString(MR.strings.source_not_installed)})")
                    }
                }
            }
        }

        binding.filtersText.text = presenter.currentFilters()

        if (manga.isLocal()) {
            binding.webviewButton.isVisible = false
            binding.shareButton.isVisible = false
        }

        if (!manga.initialized) return
        updateCover(manga)
        if (adapter.preferences.themeMangaDetails().get()) {
            updateColors(false)
        }
    }

    private fun setGenreTags(binding: MangaHeaderItemBinding, manga: Manga) {
        with(binding.mangaGenresTags) {
            removeAllViews()
            val dark = context.isInNightMode()
            val amoled = adapter.delegate.mangaPresenter().preferences.themeDarkAmoled().get()
            val baseTagColor = context.getResourceColor(R.attr.background)
            val bgArray = FloatArray(3)
            val accentArray = FloatArray(3)

            ColorUtils.colorToHSL(baseTagColor, bgArray)
            ColorUtils.colorToHSL(adapter.delegate.accentColor() ?: context.getResourceColor(R.attr.colorSecondary), accentArray)
            val downloadedColor = ColorUtils.setAlphaComponent(
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        if (adapter.delegate.accentColor() != null) accentArray[0] else bgArray[0],
                        bgArray[1],
                        (
                            when {
                                amoled && dark -> 0.1f
                                dark -> 0.225f
                                else -> 0.85f
                            }
                            ),
                    ),
                ),
                199,
            )
            val textColor = ColorUtils.HSLToColor(
                floatArrayOf(
                    accentArray[0],
                    accentArray[1],
                    if (dark) 0.945f else 0.175f,
                ),
            )
            val states = arrayOf(
                intArrayOf(-AR.attr.state_activated),
                intArrayOf(),
            )
            val colors = intArrayOf(
                downloadedColor,
                ColorUtils.blendARGB(
                    downloadedColor,
                    context.getResourceColor(R.attr.colorControlNormal),
                    0.25f,
                ),
            )
            val colorStateList = ColorStateList(states, colors)
            if (manga.genre.isNullOrBlank().not()) {
                (manga.getGenres() ?: emptyList()).map { genreText ->
                    val chip = LayoutInflater.from(binding.root.context).inflate(
                        R.layout.genre_chip,
                        this,
                        false,
                    ) as Chip
                    val id = View.generateViewId()
                    chip.id = id
                    chip.chipBackgroundColor = colorStateList
                    chip.setTextColor(textColor)
                    chip.text = genreText
                    chip.setOnClickListener {
                        adapter.delegate.showFloatingActionMode(chip, isTag = true)
                    }
                    chip.setOnLongClickListener {
                        adapter.delegate.copyContentToClipboard(genreText, genreText)
                        true
                    }
                    this.addView(chip)
                }
            }
        }
    }

    fun clearDescFocus() {
        binding ?: return
        binding.mangaSummary.setTextIsSelectable(false)
        binding.mangaSummary.clearFocus()
    }

    private fun MaterialButton.checked(checked: Boolean) {
        if (checked) {
            stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.icon_btn_state_list_anim)
            backgroundTintList = ColorStateList.valueOf(
                ColorUtils.blendARGB(
                    adapter.delegate.accentColor() ?: context.getResourceColor(R.attr.colorSecondary),
                    context.getResourceColor(R.attr.background),
                    0.706f,
                ),
            )
            strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
        } else {
            stateListAnimator = null
            resetStrokeColor()
            backgroundTintList =
                ColorStateList.valueOf(context.getResourceColor(R.attr.background))
        }
    }

    fun setTopHeight(newHeight: Int) {
        binding ?: return
        if (newHeight == binding.topView.height) return
        binding.topView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = newHeight
        }
    }

    fun setBackDrop(color: Int) {
        binding ?: return
        binding.trueBackdrop.setBackgroundColor(color)
    }

    fun updateColors(updateAll: Boolean = true) {
        val accentColor = adapter.delegate.accentColor() ?: return
        if (binding == null) {
            if (chapterBinding != null) {
                chapterBinding.filterButton.imageTintList = ColorStateList.valueOf(accentColor)
            }
            return
        }
        val manga = adapter.presenter.manga
        with(binding) {
            trueBackdrop.setBackgroundColor(
                adapter.delegate.coverColor()
                    ?: trueBackdrop.context.getResourceColor(R.attr.background),
            )
            TextViewCompat.setCompoundDrawableTintList(moreButton, ColorStateList.valueOf(accentColor))
            moreButton.setTextColor(accentColor)
            TextViewCompat.setCompoundDrawableTintList(lessButton, ColorStateList.valueOf(accentColor))
            lessButton.setTextColor(accentColor)
            shareButton.imageTintList = ColorStateList.valueOf(accentColor)
            webviewButton.imageTintList = ColorStateList.valueOf(accentColor)
            filterButton.imageTintList = ColorStateList.valueOf(accentColor)

            val states = arrayOf(
                intArrayOf(-AR.attr.state_enabled),
                intArrayOf(),
            )

            val colors = intArrayOf(
                ColorUtils.setAlphaComponent(root.context.getResourceColor(R.attr.tabBarIconInactive), 43),
                accentColor,
            )

            startReadingButton.backgroundTintList = ColorStateList(states, colors)

            val textColors = intArrayOf(
                ColorUtils.setAlphaComponent(root.context.getResourceColor(R.attr.colorOnSurface), 97),
                root.context.getResourceColor(AR.attr.textColorPrimaryInverse),
            )
            startReadingButton.setTextColor(ColorStateList(states, textColors))
            trackButton.iconTint = ColorStateList.valueOf(accentColor)
            favoriteButton.iconTint = ColorStateList.valueOf(accentColor)
            if (updateAll) {
                trackButton.checked(trackButton.stateListAnimator != null)
                favoriteButton.checked(favoriteButton.stateListAnimator != null)
                setGenreTags(this, manga)
            }
        }
    }

    fun updateTracking() {
        binding ?: return
        val presenter = adapter.delegate.mangaPresenter()
        val tracked = presenter.isTracked()
        with(binding.trackButton) {
            text = itemView.context.getString(
                if (tracked) {
                    MR.strings.tracked
                } else {
                    MR.strings.tracking
                },
            )

            icon = ContextCompat.getDrawable(
                itemView.context,
                if (tracked) {
                    R.drawable
                        .ic_check_24dp
                } else {
                    R.drawable.ic_sync_24dp
                },
            )
            checked(tracked)
        }
    }

    fun collapse() {
        binding ?: return
        if (!canCollapse) return
        binding.subItemGroup.isVisible = false
        binding.startReadingButton.isVisible = false
        if (binding.moreButton.isVisible || binding.moreButton.isInvisible) {
            binding.moreButtonGroup.isInvisible = !isTablet
        } else {
            binding.lessButton.isVisible = false
            binding.mangaGenresTags.isVisible = isTablet
        }
    }

    fun updateCover(manga: Manga) {
        binding ?: return
        if (!manga.initialized) return
        val drawable = adapter.controller.binding.mangaCoverFull.drawable
        binding.mangaCover.loadManga(manga) {
            placeholder(drawable)
            error(drawable)
            if (manga.favorite) networkCachePolicy(CachePolicy.READ_ONLY)
            diskCachePolicy(CachePolicy.READ_ONLY)
        }
        binding.backdrop.loadManga(manga) {
            placeholder(drawable)
            error(drawable)
            if (manga.favorite) networkCachePolicy(CachePolicy.READ_ONLY)
            diskCachePolicy(CachePolicy.READ_ONLY)
            target(
                onSuccess = {
                    val result = it.asDrawable(itemView.resources)
                    val bitmap = (result as? BitmapDrawable)?.bitmap
                    if (bitmap == null) {
                        binding.backdrop.setImageDrawable(result)
                        return@target
                    }
                    val yOffset = (bitmap.height / 2 * 0.33).toInt()

                    binding.backdrop.setImageDrawable(
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height - yOffset)
                            .toDrawable(itemView.resources),
                    )
                    applyBlur()
                },
            )
        }
    }

    fun expand() {
        binding ?: return
        binding.subItemGroup.isVisible = true
        if (!showMoreButton) {
            binding.moreButtonGroup.isVisible = false
        } else {
            if (binding.mangaSummary.maxLines != Integer.MAX_VALUE) {
                binding.moreButtonGroup.isVisible = !isTablet
            } else {
                binding.lessButton.isVisible = !isTablet
                binding.mangaGenresTags.isVisible = true
            }
        }
        binding.startReadingButton.isVisible = showReadingButton
    }

    fun updateRating(rating: Double) {
        binding ?: return
        binding.rateButton?.apply {
            icon = ContextCompat.getDrawable(
                itemView.context,
                when {
                    rating > 0 -> R.drawable.ic_star_24dp
                    else -> R.drawable.ic_star_outline_24dp
                },
            )
            text = when {
                rating > 0 -> String.format("%.1f", rating)
                else -> MR.strings.rate.getString(itemView.context)
            }
            checked(rating > 0)
        }
    }

    override fun onLongClick(view: View?): Boolean {
        super.onLongClick(view)
        return false
    }
}
