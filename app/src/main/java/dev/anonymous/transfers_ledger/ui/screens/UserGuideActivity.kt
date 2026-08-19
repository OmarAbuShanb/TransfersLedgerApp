package dev.anonymous.transfers_ledger.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.databinding.ActivityUserGuideBinding

class UserGuideActivity : ComponentActivity() {
    private lateinit var binding: ActivityUserGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val rawText = getString(R.string.user_guide_content)
        binding.guideContentText.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(rawText, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(rawText)
        }

        binding.backButton.setOnClickListener {
            finish()
        }
    }
}
