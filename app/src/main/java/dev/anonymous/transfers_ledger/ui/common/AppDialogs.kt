package dev.anonymous.transfers_ledger.ui.common

import android.content.Context
import android.view.Window
import androidx.fragment.app.FragmentManager
import dev.anonymous.transfers_ledger.data.local.db.CustomerEntity
import dev.anonymous.transfers_ledger.domain.model.DateRange
import dev.anonymous.transfers_ledger.ui.dialogs.*

object AppDialogs {

    fun configureDialogWindowSystemBars(window: Window?, context: Context) {
        // System bars inherit theme colors stably without runtime mutations,
        // preventing navigation bar flickering during dialog display.
    }

    fun showTextInput(
        fragmentManager: FragmentManager,
        title: String,
        hint: String,
        initialValue: String,
        onSave: (String) -> Unit
    ) {
        val tag = "TextInputDialog"
        val existing = fragmentManager.findFragmentByTag(tag) as? TextInputDialogFragment
        if (existing != null) {
            existing.onSaveListener = onSave
            return
        }
        val fragment = TextInputDialogFragment.newInstance(title, hint, initialValue).apply {
            onSaveListener = onSave
        }
        fragment.show(fragmentManager, tag)
    }

    fun showExportNotice(
        fragmentManager: FragmentManager,
        onApply: () -> Unit
    ) {
        val tag = "ExportNoticeDialog"
        val existing = fragmentManager.findFragmentByTag(tag) as? ExportNoticeDialogFragment
        if (existing != null) {
            existing.onApplyListener = onApply
            return
        }
        val fragment = ExportNoticeDialogFragment.newInstance().apply {
            onApplyListener = onApply
        }
        fragment.show(fragmentManager, tag)
    }

    fun showPrivacyPolicy(
        fragmentManager: FragmentManager,
        isCancelable: Boolean = false,
        onAccept: (() -> Unit)? = null
    ) {
        val tag = "PrivacyPolicyDialog"
        val existing = fragmentManager.findFragmentByTag(tag) as? PrivacyPolicyDialogFragment
        if (existing != null) {
            existing.onAcceptListener = { onAccept?.invoke() }
            return
        }
        val fragment = PrivacyPolicyDialogFragment.newInstance(isCancelable).apply {
            onAcceptListener = { onAccept?.invoke() }
        }
        fragment.show(fragmentManager, tag)
    }

    fun showOverview(
        fragmentManager: FragmentManager,
        onOpenUserGuide: (() -> Unit)? = null
    ) {
        val tag = "OverviewDialog"
        val existing = fragmentManager.findFragmentByTag(tag) as? OverviewDialogFragment
        if (existing != null) {
            existing.onOpenUserGuideListener = onOpenUserGuide
            return
        }
        val fragment = OverviewDialogFragment.newInstance().apply {
            onOpenUserGuideListener = onOpenUserGuide
        }
        fragment.show(fragmentManager, tag)
    }

    fun showConfirmation(
        fragmentManager: FragmentManager,
        title: String,
        message: String,
        positiveText: String,
        onConfirm: () -> Unit
    ) {
        val tag = "ConfirmationDialog"
        val existing = fragmentManager.findFragmentByTag(tag) as? ConfirmationDialogFragment
        if (existing != null) {
            existing.onConfirmListener = onConfirm
            return
        }
        val fragment = ConfirmationDialogFragment.newInstance(title, message, positiveText).apply {
            onConfirmListener = onConfirm
        }
        fragment.show(fragmentManager, tag)
    }

    fun showActivationDialog(
        fragmentManager: FragmentManager,
        deviceIdHash: String,
        message: String,
        isCancelable: Boolean,
        onWhatsappClick: () -> Unit,
        onActivate: (String) -> Boolean
    ) {
        val tag = "ActivationDialog"
        val existing = fragmentManager.findFragmentByTag(tag) as? ActivationDialogFragment
        if (existing != null) {
            existing.onWhatsappClickListener = onWhatsappClick
            existing.onActivateListener = onActivate
            return
        }
        val fragment = ActivationDialogFragment.newInstance(deviceIdHash, message, isCancelable).apply {
            onWhatsappClickListener = onWhatsappClick
            onActivateListener = onActivate
        }
        fragment.show(fragmentManager, tag)
    }

    fun showFeatureNotAvailableDialog(
        fragmentManager: FragmentManager,
        onActivateClick: () -> Unit
    ) {
        val tag = "FeatureLockedDialog"
        val existing = fragmentManager.findFragmentByTag(tag) as? FeatureLockedDialogFragment
        if (existing != null) {
            existing.onActivateClickListener = onActivateClick
            return
        }
        val fragment = FeatureLockedDialogFragment.newInstance().apply {
            onActivateClickListener = onActivateClick
        }
        fragment.show(fragmentManager, tag)
    }

    fun showCustomerLinkSheet(
        fragmentManager: FragmentManager,
        searchCustomers: suspend (String) -> List<CustomerEntity>,
        onLink: (CustomerEntity) -> Unit
    ) {
        val tag = "CustomerLinkBottomSheet"
        val existing = fragmentManager.findFragmentByTag(tag) as? CustomerLinkBottomSheetDialogFragment
        if (existing != null) {
            existing.searchCustomers = searchCustomers
            existing.onLinkListener = onLink
            return
        }
        val fragment = CustomerLinkBottomSheetDialogFragment.newInstance().apply {
            this.searchCustomers = searchCustomers
            this.onLinkListener = onLink
        }
        fragment.show(fragmentManager, tag)
    }

    fun showDateDialog(
        fragmentManager: FragmentManager,
        title: String,
        actionText: String,
        buildDayRange: (Long) -> DateRange,
        buildRange: (Long, Long) -> DateRange,
        minDate: Long? = null,
        maxDate: Long? = null,
        onApply: (DateRange, String, () -> Unit) -> Unit
    ) {
        val tag = "DateRangeDialog"
        val existing = fragmentManager.findFragmentByTag(tag) as? DateRangeDialogFragment
        if (existing != null) {
            existing.buildDayRange = buildDayRange
            existing.buildRange = buildRange
            existing.onApplyListener = onApply
            return
        }
        val fragment = DateRangeDialogFragment.newInstance(title, actionText, minDate, maxDate).apply {
            this.buildDayRange = buildDayRange
            this.buildRange = buildRange
            this.onApplyListener = onApply
        }
        fragment.show(fragmentManager, tag)
    }
}
