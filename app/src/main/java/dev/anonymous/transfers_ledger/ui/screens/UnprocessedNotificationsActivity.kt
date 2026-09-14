package dev.anonymous.transfers_ledger.ui.screens

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.app.TransfersLedgerApplication
import dev.anonymous.transfers_ledger.core.TimeUtils
import dev.anonymous.transfers_ledger.databinding.ActivityUnprocessedNotificationsBinding
import dev.anonymous.transfers_ledger.ui.adapters.UnprocessedNotificationAdapter
import dev.anonymous.transfers_ledger.ui.common.AppDialogs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UnprocessedNotificationsActivity : FragmentActivity() {

    private lateinit var binding: ActivityUnprocessedNotificationsBinding
    private lateinit var adapter: UnprocessedNotificationAdapter
    private val repository by lazy { (application as TransfersLedgerApplication).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnprocessedNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val locale = TimeUtils.getLocale(resources.configuration)
        adapter = UnprocessedNotificationAdapter(locale)

        binding.notificationsRecycler.layoutManager = LinearLayoutManager(this)
        binding.notificationsRecycler.itemAnimator = null
        binding.notificationsRecycler.adapter = adapter

        binding.backButton.setOnClickListener { finish() }

        binding.clearLogButton.setOnClickListener {
            showClearConfirmationDialog()
        }

        lifecycleScope.launch {
            repository.getPagedUnprocessedNotifications().collectLatest {
                adapter.submitData(it)
            }
        }

        lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                if (loadStates.refresh is LoadState.NotLoading && adapter.itemCount == 0) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.notificationsRecycler.visibility = View.GONE
                    binding.clearLogButton.visibility = View.GONE
                } else {
                    binding.emptyStateText.visibility = View.GONE
                    binding.notificationsRecycler.visibility = View.VISIBLE
                    binding.clearLogButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showClearConfirmationDialog() {
        AppDialogs.showConfirmation(
            fragmentManager = supportFragmentManager,
            title = getString(R.string.clear_log_confirm_title),
            message = getString(R.string.clear_log_confirm_message),
            positiveText = getString(R.string.clear_log_confirm_btn)
        ) {
            lifecycleScope.launch {
                repository.deleteAllUnprocessedNotifications()
                adapter.refresh()
            }
        }
    }
}
