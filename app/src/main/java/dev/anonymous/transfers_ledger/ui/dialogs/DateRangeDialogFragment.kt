package dev.anonymous.transfers_ledger.ui.dialogs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.databinding.DialogDateRangeBinding
import dev.anonymous.transfers_ledger.domain.model.DateRange
import dev.anonymous.transfers_ledger.ui.common.SegmentedControlAnimator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.core.view.isVisible

class DateRangeDialogFragment : BaseAnimatedDialogFragment() {

    override val motion = Motion.BOTTOM_SCALE_FADE
    override val useFullScreenWindow = true

    var buildDayRange: ((Long) -> DateRange)? = null
    var buildRange: ((Long, Long) -> DateRange)? = null
    var onApplyListener: ((DateRange, String, () -> Unit) -> Unit)? = null

    private val formatter = SimpleDateFormat("d/M/yyyy", Locale.forLanguageTag("ar"))
    private var singleMode = true
    private var titleText: String = ""
    private var actionText: String = ""
    private var minDate: Long? = null
    private var maxDate: Long? = null
    private var startDate: Long = System.currentTimeMillis()
    private var endDate: Long = System.currentTimeMillis()

    private var _binding: DialogDateRangeBinding? = null
    private var modeAnimator: ValueAnimator? = null

    // Tracks the intended visibility state to avoid redundant animations (e.g. when
    // DatePickerDialog closes and refreshLabels() fires without a mode change).
    private var endTextShown: Boolean = false

    private val fullContainerHeightPx by lazy { dpToPx(66) } // 56dp height + 10dp margin

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_ACTION_TEXT = "action_text"
        private const val ARG_MIN_DATE = "min_date"
        private const val ARG_MAX_DATE = "max_date"

        private const val STATE_SINGLE_MODE = "state_single_mode"
        private const val STATE_START_DATE = "state_start_date"
        private const val STATE_END_DATE = "state_end_date"
        private const val STATE_ACTIVE_PICKER = "state_active_picker"
        private const val MODE_CHANGE_DURATION = 220L

        private const val PICKER_NONE = 0
        private const val PICKER_START = 1
        private const val PICKER_END = 2

        fun newInstance(
            title: String,
            actionText: String,
            minDate: Long? = null,
            maxDate: Long? = null
        ): DateRangeDialogFragment {
            return DateRangeDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_ACTION_TEXT, actionText)
                    minDate?.let { putLong(ARG_MIN_DATE, it) }
                    maxDate?.let { putLong(ARG_MAX_DATE, it) }
                }
            }
        }
    }

    private var activePickerType: Int = PICKER_NONE
    private var activeDatePickerDialog: DatePickerDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            titleText = it.getString(ARG_TITLE, "")
            actionText = it.getString(ARG_ACTION_TEXT, "")
            if (it.containsKey(ARG_MIN_DATE)) minDate = it.getLong(ARG_MIN_DATE)
            if (it.containsKey(ARG_MAX_DATE)) maxDate = it.getLong(ARG_MAX_DATE)
        }
        if (savedInstanceState != null) {
            singleMode = savedInstanceState.getBoolean(STATE_SINGLE_MODE, true)
            startDate = savedInstanceState.getLong(STATE_START_DATE, System.currentTimeMillis())
            endDate = savedInstanceState.getLong(STATE_END_DATE, System.currentTimeMillis())
            activePickerType = savedInstanceState.getInt(STATE_ACTIVE_PICKER, PICKER_NONE)
        } else {
            val defaultTime = maxDate ?: System.currentTimeMillis()
            startDate = clampDay(defaultTime)
            endDate = clampDay(defaultTime)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SINGLE_MODE, singleMode)
        outState.putLong(STATE_START_DATE, startDate)
        outState.putLong(STATE_END_DATE, endDate)
        outState.putInt(STATE_ACTIVE_PICKER, activePickerType)
    }

    private var isPickerShowing: Boolean = false

    private fun showDatePicker(type: Int) {
        if (!isAdded || isStateSaved) return
        if (isPickerShowing || activeDatePickerDialog?.isShowing == true) return
        isPickerShowing = true
        val current = if (type == PICKER_START) startDate else endDate
        val calendar = Calendar.getInstance().apply { timeInMillis = current }
        val picker = DatePickerDialog(
            // Wrap in ContextThemeWrapper so Material3 color tokens (including dark mode
            // surface/background) resolve correctly at dialog-creation time.
            ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_App_DatePickerDialog),
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val clamped = clampDay(picked)
                if (type == PICKER_START) {
                    startDate = clamped
                } else {
                    endDate = clamped
                }
                activePickerType = PICKER_NONE
                isPickerShowing = false
                activeDatePickerDialog = null
                refreshLabels()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        minDate?.let { picker.datePicker.minDate = startOfDay(it) }
        maxDate?.let { picker.datePicker.maxDate = startOfDay(it) }
        picker.setOnDismissListener {
            if (activePickerType == type) {
                activePickerType = PICKER_NONE
            }
            isPickerShowing = false
            activeDatePickerDialog = null
        }
        activePickerType = type
        activeDatePickerDialog = picker
        picker.show()
    }

    private fun animateEndTextVisibility(show: Boolean) {
        val binding = _binding ?: return

        // Guard: don't animate if already in the target state and no animation is running.
        // This prevents the Glitch that occurred when DatePickerDialog closed and
        // refreshLabels() fired without an actual mode change.
        if (show == endTextShown && modeAnimator?.isRunning != true) return
        endTextShown = show

        modeAnimator?.cancel()

        val startHeight = if (binding.endTextContainer.isVisible && binding.endTextContainer.height > 0) {
            binding.endTextContainer.height
        } else if (show) {
            0
        } else {
            fullContainerHeightPx
        }

        val targetHeight = if (show) fullContainerHeightPx else 0
        val startAlpha = binding.endText.alpha
        val targetAlpha = if (show) 1f else 0f

        binding.endTextContainer.visibility = View.VISIBLE
        binding.endText.visibility = View.VISIBLE

        modeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MODE_CHANGE_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                val currentH = (startHeight + (targetHeight - startHeight) * progress).toInt()

                binding.endTextContainer.layoutParams = binding.endTextContainer.layoutParams.apply {
                    height = currentH
                }
                binding.endText.alpha = startAlpha + (targetAlpha - startAlpha) * progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!show) {
                        binding.endTextContainer.visibility = View.GONE
                        binding.endText.visibility = View.GONE
                    }
                    binding.endTextContainer.layoutParams = binding.endTextContainer.layoutParams.apply {
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }
            })
            start()
        }
    }

    private fun updateModeUi(animateLayout: Boolean = true) {
        val binding = _binding ?: return
        if (!isAdded) return

        binding.singleButton.setTextColor(
            requireContext().getColor(if (singleMode) android.R.color.white else R.color.text_secondary)
        )
        binding.rangeButton.setTextColor(
            requireContext().getColor(if (!singleMode) android.R.color.white else R.color.text_secondary)
        )

        if (animateLayout && binding.root.isLaidOut) {
            animateEndTextVisibility(show = !singleMode)
        } else {
            modeAnimator?.cancel()
            binding.endTextContainer.visibility = if (singleMode) View.GONE else View.VISIBLE
            binding.endText.visibility = if (singleMode) View.GONE else View.VISIBLE
            binding.endText.alpha = if (singleMode) 0f else 1f
            binding.endTextContainer.layoutParams = binding.endTextContainer.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }

        val selected = if (singleMode) binding.singleButton else binding.rangeButton
        val shouldAnimate = animateLayout && binding.modeIndicator.width > 0
        SegmentedControlAnimator.select(
            container = binding.modeSegment,
            indicator = binding.modeIndicator,
            selected = selected,
            animate = shouldAnimate
        )
    }

    private fun refreshLabels(animateLayout: Boolean = true) {
        val binding = _binding ?: return
        if (!isAdded) return
        val startPrefix =
            getString(if (singleMode) R.string.single_day_date_label else R.string.start_date)
        binding.startText.text = "$startPrefix: ${formatter.format(startDate)}"
        binding.endText.text = "${getString(R.string.end_date)}: ${formatter.format(endDate)}"
        updateModeUi(animateLayout)
    }

    override fun onDialogShown() {
        val binding = _binding ?: return
        updateModeUi(animateLayout = false)
        val selected = if (singleMode) binding.singleButton else binding.rangeButton
        SegmentedControlAnimator.snap(
            container = binding.modeSegment,
            indicator = binding.modeIndicator,
            selected = selected
        )
        if (activePickerType != PICKER_NONE && activeDatePickerDialog == null) {
            binding.root.post {
                showDatePicker(activePickerType)
            }
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = DialogDateRangeBinding.inflate(inflater, container, false)
        _binding = binding

        binding.titleText.text = titleText
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
        binding.startText.setOnClickListener { showDatePicker(PICKER_START) }
        binding.endText.setOnClickListener { showDatePicker(PICKER_END) }
        binding.cancelButton.setOnClickListener { dismissWithAnimation() }

        binding.applyButton.setOnClickListener {
            val start = minOf(startDate, endDate)
            val end = maxOf(startDate, endDate)
            val range = if (singleMode) {
                buildDayRange?.invoke(startDate) ?: DateRange(
                    startOfDay(startDate),
                    endOfDay(startDate)
                )
            } else {
                buildRange?.invoke(start, end) ?: DateRange(startOfDay(start), endOfDay(end))
            }
            val label = if (singleMode) formatter.format(startDate)
            else "${formatter.format(start)} - ${formatter.format(end)}"

            onApplyListener?.invoke(range, label) { dismissWithAnimation() }
        }

        // Set initial text and colors before the view is attached to a window.
        val initialStartPrefix =
            getString(if (singleMode) R.string.single_day_date_label else R.string.start_date)
        binding.startText.text = "$initialStartPrefix: ${formatter.format(startDate)}"
        binding.endText.text = "${getString(R.string.end_date)}: ${formatter.format(endDate)}"
        binding.singleButton.setTextColor(requireContext().getColor(if (singleMode) android.R.color.white else R.color.text_secondary))
        binding.rangeButton.setTextColor(requireContext().getColor(if (!singleMode) android.R.color.white else R.color.text_secondary))
        binding.endTextContainer.visibility = if (singleMode) View.GONE else View.VISIBLE
        binding.endText.visibility = if (singleMode) View.GONE else View.VISIBLE

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

        return binding.root
    }

    override fun onDestroyView() {
        modeAnimator?.cancel()
        modeAnimator = null
        activeDatePickerDialog?.dismiss()
        activeDatePickerDialog = null
        _binding = null
        super.onDestroyView()
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

    private fun endOfDay(value: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = value
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()
}
