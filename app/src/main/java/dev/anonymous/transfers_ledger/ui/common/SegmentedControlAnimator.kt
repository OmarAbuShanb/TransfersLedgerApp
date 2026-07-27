package dev.anonymous.transfers_ledger.ui.common

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/**
 * Animates a sliding indicator view to sit behind the selected segment button.
 *
 * ## Position calculation — Relative Layout Coordinates
 *
 * Instead of relying on `getLocationInWindow` (which can return scaled or offset coordinates
 * during Dialog entry scale animations) or standard RTL FrameLayout gravity (which aligns
 * children to the right in RTL), we enforce `Gravity.TOP | Gravity.LEFT` on the indicator.
 *
 * This anchors the indicator's physical layout `left` edge to `container.paddingLeft` regardless
 * of the layout direction (RTL or LTR). We then calculate the target position of [selected]
 * relative to [container] by accumulating `view.left` up the hierarchy tree.
 *
 * Setting `indicator.x = targetX` translates the indicator from its fixed `left` coordinate to
 * the exact target button, making positioning robust against layout direction (RTL/LTR),
 * screen density, orientation changes, and window entry animations.
 */
object SegmentedControlAnimator {

    /**
     * Moves [indicator] to sit behind [selected] inside [container].
     *
     * @param animate `true` = slide animation (user button tap, view already laid out).
     *                `false` = snap without animation (initial display or config change).
     */
    fun select(container: ViewGroup, indicator: View, selected: View, animate: Boolean) {
        if (animate && selected.isAttachedToWindow && selected.isLaidOut && selected.width > 0) {
            applySelection(container, indicator, selected, animate = true)
            return
        }

        container.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (selected.width <= 0 || selected.height <= 0) return
                    val obs = container.viewTreeObserver
                    if (obs.isAlive) obs.removeOnGlobalLayoutListener(this)
                    applySelection(container, indicator, selected, animate = false)
                }
            }
        )
    }

    /**
     * Snaps [indicator] to [selected] immediately, with no animation.
     */
    fun snap(container: ViewGroup, indicator: View, selected: View) {
        applySelection(container, indicator, selected, animate = false)
    }

    private fun applySelection(
        container: ViewGroup,
        indicator: View,
        selected: View,
        animate: Boolean
    ) {
        if (!container.isAttachedToWindow || !selected.isAttachedToWindow) return
        if (selected.width <= 0 || selected.height <= 0) return

        indicator.layoutDirection = View.LAYOUT_DIRECTION_LTR

        val targetX = getRelativeLeft(selected, container)
        val targetWidth = selected.width

        val params = indicator.layoutParams
        var paramsChanged = false
        if (params is FrameLayout.LayoutParams) {
            val desiredGravity = Gravity.TOP or Gravity.LEFT
            if (params.gravity != desiredGravity) {
                params.gravity = desiredGravity
                paramsChanged = true
            }
        }
        if (params.width != targetWidth) {
            params.width = targetWidth
            paramsChanged = true
        }
        if (paramsChanged) {
            indicator.layoutParams = params
        }

        indicator.animate().cancel()
        if (animate && indicator.isLaidOut && indicator.width > 0) {
            indicator.animate()
                .x(targetX)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            indicator.x = targetX
        }
    }

    private fun getRelativeLeft(view: View, container: ViewGroup): Float {
        var left = 0f
        var current: View? = view
        while (current != null && current != container) {
            left += current.left.toFloat()
            val parent = current.parent
            current = if (parent is View) parent else null
        }
        return left
    }
}
