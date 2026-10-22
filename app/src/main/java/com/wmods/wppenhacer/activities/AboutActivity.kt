package com.wmods.wppenhacer.activities

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.databinding.ActivityAboutBinding

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.aboutToolbar.setNavigationOnClickListener { finish() }
        binding.aboutVersion.text = getString(R.string.version_s, BuildConfig.VERSION_NAME)

        binding.btnProfile.setOnClickListener { openUrl("https://github.com/kherio") }
        binding.btnRepo.setOnClickListener { openUrl("https://github.com/kherio/WaToolkit") }
        binding.btnReleases.setOnClickListener { openUrl("https://github.com/kherio/WaToolkit/releases") }
        binding.btnIssues.setOnClickListener { openUrl("https://github.com/kherio/WaToolkit/issues") }
        // Credit for the project this one was forked from.
        binding.btnOriginal.setOnClickListener { openUrl("https://github.com/Dev4Mod/WaEnhancer") }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
