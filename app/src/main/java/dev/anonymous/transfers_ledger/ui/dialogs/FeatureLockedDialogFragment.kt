package dev.anonymous.transfers_ledger.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.anonymous.transfers_ledger.databinding.DialogFeatureLockedBinding

class FeatureLockedDialogFragment : BaseAnimatedDialogFragment() {

    override val motion = Motion.BOTTOM_SCALE_FADE
    override val dismissOnOutsideClick = true

    var onActivateClickListener: (() -> Unit)? = null

    companion object {
        fun newInstance() = FeatureLockedDialogFragment()
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = DialogFeatureLockedBinding.inflate(inflater, container, false)

        binding.cancelButton.setOnClickListener { dismissWithAnimation() }
        binding.activateButton.setOnClickListener {
            dismissWithAnimation {
                onActivateClickListener?.invoke()
            }
        }

        return binding.root
    }
}
