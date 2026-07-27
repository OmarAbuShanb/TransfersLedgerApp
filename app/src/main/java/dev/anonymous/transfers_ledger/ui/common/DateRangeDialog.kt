package dev.anonymous.transfers_ledger.ui.common

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.databinding.DialogDateRangeBinding
import dev.anonymous.transfers_ledger.domain.model.DateRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DateRangeDialog(
    private val context: Context,
    private val title: String,
    private val actionText: String,
    private val buildDayRange: (Long) -> DateRange,
    private val buildRange: (Long, Long) -> DateRange,
    private val minDate: Long? = null,
    private val maxDate: Long? = null,
    private val onApply: (DateRange, String, () -> Unit) -> Unit
) {
    private val formatter = SimpleDateFormat("d/M/yyyy", Locale.forLanguageTag("ar"))
    private var singleMode = true
    private var startDate = clampDay(maxDate ?: System.currentTimeMillis())
    private var endDate = clampDay(maxDate ?: System.currentTimeMillis())

    fun show() {
        if (activeDialog?.isShowing == true) return

        val binding = DialogDateRangeBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        // ---------------------------------------------------------------------------
        // updateModeUi — updates text colors, end-date visibility, and indicator.
        //
        // The indicator (SegmentedControlAnimator.select) is split into two paths:
        //   animate = true  → user just tapped a mode button; the view is already laid
        //                      out, so we apply the slide animation immediately.
        //   animate = false → called at initial display or during size changes; we let
        //                      SegmentedControlAnimator wait for the layout pass.
        //
        // IMPORTANT: never call select() before dialog.show(). An unattached view has
        // no window, so OnGlobalLayoutListener fires immediately on attachment instead
        // of after the layout pass, producing wrong positions.
        // ---------------------------------------------------------------------------
        fun updateModeUi(animateLayout: Boolean = true) {
            binding.singleButton.setTextColor(
                context.getColor(if (singleMode) android.R.color.white else R.color.text_secondary)
            )
            binding.rangeButton.setTextColor(
                context.getColor(if (!singleMode) android.R.color.white else R.color.text_secondary)
            )
            if (animateLayout && binding.root.isLaidOut) {
                TransitionManager.beginDelayedTransition(
                    binding.root as ViewGroup,
                    AutoTransition().apply { duration = 180L }
                )
            }
            binding.endText.visibility = if (singleMode) View.GONE else View.VISIBLE

            val selected = if (singleMode) binding.singleButton else binding.rangeButton
            // animate=true → indicator is already on screen, slide immediately.
            // animate=false → wait for the layout listener (initial or resize).
            val shouldAnimate = animateLayout && binding.modeIndicator.width > 0
            SegmentedControlAnimator.select(
                container = binding.modeSegment,
                indicator = binding.modeIndicator,
                selected = selected,
                animate = shouldAnimate
            )
        }

        fun refreshLabels(animateLayout: Boolean = true) {
            val startPrefix = context.getString(if (singleMode) R.string.single_day_date_label else R.string.start_date)
            binding.startText.text = "$startPrefix: ${formatter.format(startDate)}"
            binding.endText.text = "${context.getString(R.string.end_date)}: ${formatter.format(endDate)}"
            updateModeUi(animateLayout)
        }

        fun dismissWithAnimation() {
            binding.root.animate().cancel()
            binding.root.animate()
                .alpha(0f)
                .translationY(dp(42).toFloat())
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(190L)
                .withEndAction { dialog.dismiss() }
                .start()
        }

        fun pickDate(current: Long, onPicked: (Long) -> Unit) {
            val calendar = Calendar.getInstance().apply { timeInMillis = current }
            val picker = DatePickerDialog(
                context,
                { _, year, month, day ->
                    val picked = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    onPicked(clampDay(picked))
                    refreshLabels()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            minDate?.let { picker.datePicker.minDate = startOfDay(it) }
            maxDate?.let { picker.datePicker.maxDate = startOfDay(it) }
            picker.show()
        }

        binding.titleText.text = title
        binding.applyButton.text = actionText
        binding.singleButton.setOnClickListener {
            if (!singleMode) {
                singleMode = true
                refreshLabels()
            }
        }
        binding.rangeButton.setOnClickListener {
            if (singleMode) {
                singleMode = false
                refreshLabels()
            }
        }
        binding.startText.setOnClickListener { pickDate(startDate) { startDate = it } }
        binding.endText.setOnClickListener { pickDate(endDate) { endDate = it } }
        binding.cancelButton.setOnClickListener { dismissWithAnimation() }
        binding.applyButton.setOnClickListener {
            val start = minOf(startDate, endDate)
            val end = maxOf(startDate, endDate)
            val range = if (singleMode) buildDayRange(startDate) else buildRange(start, end)
            val label = if (singleMode) formatter.format(startDate)
            else "${formatter.format(start)} - ${formatter.format(end)}"
            onApply(range, label) { dismissWithAnimation() }
        }

        // Populate text labels and visibility BEFORE show(), but do NOT call
        // updateModeUi() / select() here — the view is not attached to a window
        // yet and the indicator positions cannot be read.
        val initialStartPrefix = context.getString(if (singleMode) R.string.single_day_date_label else R.string.start_date)
        binding.startText.text = "$initialStartPrefix: ${formatter.format(startDate)}"
        binding.endText.text = "${context.getString(R.string.end_date)}: ${formatter.format(endDate)}"
        binding.singleButton.setTextColor(context.getColor(android.R.color.white))
        binding.rangeButton.setTextColor(context.getColor(R.color.text_secondary))
        binding.endText.visibility = if (singleMode) View.GONE else View.VISIBLE

        val wrapper = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        // MATCH_PARENT + dp margins: density-independent and recalculates on rotation.
        // A pixel-frozen percentage (widthPixels * 0.9) would give wrong width after
        // rotation because the activity is not recreated (configChanges handled).
        val hMarginPx = dp(24)
        val contentParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        contentParams.marginStart = hMarginPx
        contentParams.marginEnd = hMarginPx
        wrapper.addView(binding.root, contentParams)
        wrapper.setOnClickListener {
            dismissWithAnimation()
        }
        binding.root.setOnClickListener {
            // Consume clicks inside the content card so they don't propagate to wrapper
        }
        dialog.setContentView(wrapper)
        activeDialog = dialog
        dialog.setOnDismissListener {
            if (activeDialog == dialog) activeDialog = null
        }
        AppDialogs.configureDialogWindowSystemBars(dialog.window, context)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        // Entry animation.
        binding.root.alpha = 0f
        binding.root.translationY = dp(42).toFloat()
        binding.root.scaleX = 0.96f
        binding.root.scaleY = 0.96f
        binding.root.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .start()

        // AFTER show(): view is attached → SegmentedControlAnimator can read positions.
        // animate = false → select() waits for the layout listener.
        updateModeUi(animateLayout = false)

        // Persistent listener: keeps the indicator correct if the dialog window
        // is resized while open (e.g., screen rotation). We use snap() directly
        // instead of select() to avoid adding a new OnGlobalLayoutListener on every
        // call, which would create a feedback loop with the persistent listener.
        var lastSegmentWidth = binding.modeSegment.width
        binding.modeSegment.viewTreeObserver.addOnGlobalLayoutListener {
            val w = binding.modeSegment.width
            if (w > 0 && w != lastSegmentWidth) {
                lastSegmentWidth = w
                SegmentedControlAnimator.snap(
                    container = binding.modeSegment,
                    indicator = binding.modeIndicator,
                    selected = if (singleMode) binding.singleButton else binding.rangeButton
                )
            }
        }
    }

    private fun clampDay(value: Long): Long {
        val min = minDate?.let { startOfDay(it) }
        val max = maxDate?.let { startOfDay(it) }
        return value
            .let { if (min != null) it.coerceAtLeast(min) else it }
            .let { if (max != null) it.coerceAtMost(max) else it }
    }

    private fun startOfDay(value: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = value
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private var activeDialog: Dialog? = null
    }
}
