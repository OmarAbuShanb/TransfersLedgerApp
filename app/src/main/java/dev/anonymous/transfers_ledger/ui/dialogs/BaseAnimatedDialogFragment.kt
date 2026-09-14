package dev.anonymous.transfers_ledger.ui.dialogs

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.annotation.StyleRes
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import dev.anonymous.transfers_ledger.R

abstract class BaseAnimatedDialogFragment : DialogFragment() {

    enum class Motion(@param:StyleRes val windowAnimStyle: Int) {
        BOTTOM_SCALE_FADE(R.style.DialogWindowAnimation_BottomScaleFade),
        HORIZONTAL_SLIDE(R.style.DialogWindowAnimation_HorizontalSlide)
    }

    protected open val motion: Motion = Motion.BOTTOM_SCALE_FADE
    protected open val dismissOnOutsideClick: Boolean = true

    /**
     * Set to true in subclasses that perform dynamic layout height animations
     * (e.g. expanding/collapsing content) to prevent WindowManager re-centering jitter.
     * Default is false (WRAP_CONTENT window) which allows proper keyboard panning/resizing.
     */
    protected open val useFullScreenWindow: Boolean = false

    /**
     * Soft-input adjustment flag (e.g. [WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN]).
     * Keyboard *showing* is handled automatically when [getFocusTarget] is non-null.
     */
    protected open val softInputMode: Int = 0

    /**
     * Return the [EditText] that should receive focus and show the keyboard when
     * the dialog opens, or null to leave the keyboard state unchanged.
     *
     * The view reference is typically available after [createContentView] stores
     * the binding; override this once the binding is initialised.
     */
    protected open fun getFocusTarget(): EditText? = null

    /**
     * Inflate and return the dialog's content view.
     * Store any view references you need (e.g. a binding field) here.
     */
    abstract fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val content = createContentView(inflater, container)

        if (!useFullScreenWindow) {
            return content
        }

        val wrapper = FrameLayout(requireContext()).apply {
            clipChildren = false
            clipToPadding = false
        }

        val hPad = dpToPx(24)
        val screenWidth = resources.displayMetrics.widthPixels
        val maxAllowedWidth = dpToPx(420)
        val targetWidth = minOf(screenWidth - (hPad * 2), maxAllowedWidth)

        val contentParams = FrameLayout.LayoutParams(
            targetWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )

        wrapper.addView(content, contentParams)

        if (dismissOnOutsideClick && isCancelable) {
            wrapper.setOnClickListener {
                dismissWithAnimation()
            }
        }
        content.setOnClickListener {
            // Consume clicks inside the content card so they don't dismiss
        }

        return wrapper
    }

    /**
     * Called via [View.post] after the window is laid out.
     * Safe to measure views or position indicators here.
     */
    protected open fun onDialogShown() {}

    override fun onStart() {
        super.onStart()
        val dlg = dialog ?: return

        dlg.setCanceledOnTouchOutside(dismissOnOutsideClick && isCancelable)

        dlg.window?.let { window ->
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.4f)

            if (useFullScreenWindow) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                window.decorView.setPadding(0, 0, 0, 0)
                // Without FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS the window cannot
                // control system bar colours; omitting it causes Android to fall
                // back to painting them solid black.
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.TRANSPARENT

                val ctx = requireContext()
                val activityWindow = (ctx as? android.app.Activity)?.window
                    ?: (ctx as? android.content.ContextWrapper)?.baseContext?.let { (it as? android.app.Activity)?.window }
                if (activityWindow != null) {
                    val hostController = WindowCompat.getInsetsController(activityWindow, activityWindow.decorView)
                    val dialogController = WindowCompat.getInsetsController(window, window.decorView)
                    dialogController.isAppearanceLightStatusBars = hostController.isAppearanceLightStatusBars
                    dialogController.isAppearanceLightNavigationBars = hostController.isAppearanceLightNavigationBars
                }
            }
            applyDialogDimensions()

            val attrs = window.attributes
            attrs.windowAnimations = motion.windowAnimStyle
            window.attributes = attrs

            val resolvedInputMode = buildSoftInputMode()
            if (resolvedInputMode != 0) window.setSoftInputMode(resolvedInputMode)
        }

        view?.post { onDialogShown() }

        // Show the keyboard on the explicitly chosen field, if any.
        getFocusTarget()?.let { target ->
            target.post {
                target.requestFocus()
                dialog?.window?.let { window ->
                    WindowCompat.getInsetsController(window, target)
                        .show(WindowInsetsCompat.Type.ime())
                }
            }
        }
    }

    /**
     * Dismiss the dialog. The window exit animation ([motion]) plays automatically.
     *
     * [onEnd] is invoked immediately after [dismiss] is called; it is **not**
     * delayed until the animation finishes, since the window lifecycle owns that.
     */
    fun dismissWithAnimation(onEnd: (() -> Unit)? = null) {
        dismiss()
        onEnd?.invoke()
    }

    private fun buildSoftInputMode(): Int {
        val adjustFlag = softInputMode
        val stateFlag = if (getFocusTarget() != null)
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        else
            0
        return adjustFlag or stateFlag
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyDialogDimensions()
    }

    private fun applyDialogDimensions() {
        val dlg = dialog ?: return
        val window = dlg.window ?: return
        val hPad = dpToPx(24)
        val screenWidth = resources.displayMetrics.widthPixels
        val maxAllowedWidth = dpToPx(420)
        val targetWidth = minOf(screenWidth - (hPad * 2), maxAllowedWidth)

        if (useFullScreenWindow) {
            val wrapper = view as? FrameLayout
            val card = wrapper?.getChildAt(0)
            val params = card?.layoutParams as? FrameLayout.LayoutParams
            if (params != null) {
                params.width = targetWidth
                card.layoutParams = params
            }
        } else {
            window.setLayout(
                targetWidth,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.decorView.setPadding(0, 0, 0, 0)
        }
    }

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
