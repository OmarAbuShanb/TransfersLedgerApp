package dev.anonymous.transfers_ledger.ui.dialogs

import android.content.Intent
import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.ui.screens.UserGuideActivity
import dev.anonymous.transfers_ledger.databinding.DialogOverviewBinding

class OverviewDialogFragment : BaseAnimatedDialogFragment() {

    override val motion = Motion.HORIZONTAL_SLIDE

    var onOpenUserGuideListener: (() -> Unit)? = null

    companion object {
        fun newInstance() = OverviewDialogFragment()
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = DialogOverviewBinding.inflate(inflater, container, false)
        val rawText = getString(R.string.overview_content)
        binding.overviewContent.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(rawText, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(rawText)
        }

        binding.okButton.setOnClickListener {
            dismissWithAnimation()
        }

        binding.userGuideButton.setOnClickListener {
            dismissWithAnimation {
                if (onOpenUserGuideListener != null) {
                    onOpenUserGuideListener?.invoke()
                } else {
                    val intent = Intent(requireContext(), UserGuideActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        return binding.root
    }
}
