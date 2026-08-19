package dev.anonymous.transfers_ledger.ui.dialogs

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.databinding.DialogPrivacyPolicyBinding

class PrivacyPolicyDialogFragment : BaseAnimatedDialogFragment() {

    override val motion = Motion.HORIZONTAL_SLIDE

    var onAcceptListener: (() -> Unit)? = null
    private var isCancelableDialog: Boolean = false

    override val dismissOnOutsideClick: Boolean
        get() = isCancelableDialog

    companion object {
        private const val ARG_CANCELABLE = "cancelable"

        fun newInstance(isCancelable: Boolean = false): PrivacyPolicyDialogFragment {
            return PrivacyPolicyDialogFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_CANCELABLE, isCancelable)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelableDialog = arguments?.getBoolean(ARG_CANCELABLE, false) ?: false
        isCancelable = isCancelableDialog
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = DialogPrivacyPolicyBinding.inflate(inflater, container, false)
        val rawText = getString(R.string.privacy_policy_content)
        binding.privacyContent.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(rawText, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(rawText)
        }

        binding.acceptButton.setOnClickListener {
            dismissWithAnimation {
                onAcceptListener?.invoke()
            }
        }
        return binding.root
    }
}
