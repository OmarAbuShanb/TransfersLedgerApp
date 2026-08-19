package dev.anonymous.transfers_ledger.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import dev.anonymous.transfers_ledger.R
import androidx.core.graphics.drawable.toDrawable

object AnimatedPopupMenu {
    private var activePopup: PopupWindow? = null

    /** Tag identifying which menu is currently showing (for state restoration). */
    var activeTag: String? = null
        private set

    data class Action(
        val title: String,
        val destructive: Boolean = false,
        val checked: Boolean = false,
        val onClick: () -> Unit
    )

    /**
     * Force-dismiss any active popup.
     * Activities should call this from onDestroy() to avoid window leaks.
     */
    fun dismissAll() {
        activePopup?.let { p ->
            try {
                if (p.isShowing) p.dismiss()
            } catch (_: Exception) {
            }
            activePopup = null
        }
        activeTag = null
    }


    fun show(
        context: Context,
        anchor: View,
        actions: List<Action>,
        tag: String? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        if (actions.isEmpty()) return
        activePopup?.dismiss()

        val width = dp(context, 208)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Inherit the device layout direction (RTL for Arabic, LTR for English).
            // Hardcoding RTL here would break the menu on LTR devices and also break
            // the compound drawable logic (drawableEnd position depends on direction).
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            clipToOutline = true
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 16).toFloat()
                setColor(context.getColor(R.color.surface))
                setStroke(dp(context, 1), context.getColor(R.color.divider))
            }
            elevation = dp(context, 10).toFloat()
        }

        val popup = PopupWindow(
            container,
            width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            elevation = dp(context, 10).toFloat()
            setOnDismissListener {
                if (activePopup === this) {
                    activePopup = null
                    activeTag = null
                }
                onDismiss?.invoke()
            }
        }
        activePopup = popup
        activeTag = tag

        actions.forEach { action ->
            container.addView(menuItem(context, action, popup, container))
        }

        container.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = container.measuredHeight
        val margin = dp(context, 8)
        val visibleFrame = Rect()
        anchor.rootView.getWindowVisibleDisplayFrame(visibleFrame)
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val minX = visibleFrame.left + margin
        val maxX = (visibleFrame.right - width - margin).coerceAtLeast(minX)
        val x = (anchorLocation[0] + anchor.width - width).coerceIn(minX, maxX)
        val yBelow = anchorLocation[1] + anchor.height + margin
        val yAbove = anchorLocation[1] - popupHeight - margin
        val spaceBelow = visibleFrame.bottom - yBelow
        val spaceAbove = anchorLocation[1] - visibleFrame.top
        val showAbove = popupHeight > spaceBelow && spaceAbove > spaceBelow
        val y = if (showAbove) {
            yAbove.coerceAtLeast(visibleFrame.top + margin)
        } else {
            yBelow.coerceAtMost(visibleFrame.bottom - popupHeight - margin)
        }

        container.pivotY = if (showAbove) popupHeight.toFloat() else 0f
        popup.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, x, y)
        container.scaleY = 0.55f
        container.alpha = 0f
        container.animate()
            .scaleY(1f)
            .alpha(1f)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun menuItem(
        context: Context,
        action: Action,
        popup: PopupWindow,
        container: View
    ): TextView {
        val selectable = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
        val itemColor =
            context.getColor(if (action.destructive) R.color.outgoing else R.color.text_primary)
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 46)
            )
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            setBackgroundResource(selectable.resourceId)
            text = action.title
            textSize = 14f
            setTextColor(itemColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD

            // Checkmark at the END of the text — the far edge opposite to where the
            // text starts. With LAYOUT_DIRECTION_LOCALE:
            //   RTL (Arabic): text starts at RIGHT, drawableEnd appears on the LEFT ✓
            //   LTR (English): text starts at LEFT, drawableEnd appears on the RIGHT ✓
            if (action.checked) {
                val checkIcon = AppCompatResources.getDrawable(context, R.drawable.ic_check_24)
                checkIcon?.setTint(itemColor)
                val iconSize = dp(context, 18)
                checkIcon?.setBounds(0, 0, iconSize, iconSize)
                // setCompoundDrawablesRelative respects layout direction:
                //   param order: (start, top, end, bottom)
                setCompoundDrawablesRelative(null, null, checkIcon, null)
                compoundDrawablePadding = dp(context, 6)
            }

            setOnClickListener {
                container.animate()
                    .scaleY(0.92f)
                    .alpha(0f)
                    .setDuration(110L)
                    .withEndAction {
                        popup.dismiss()
                        action.onClick()
                    }
                    .start()
            }
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
