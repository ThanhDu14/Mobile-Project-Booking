package com.example.booking_app.feature.auth.ui.welcome

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAuthWelcomeBinding
import com.example.booking_app.databinding.ItemWelcomeFeatureBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Màn hình chào theo wireframe `buoc1_man-chao`. */
@AndroidEntryPoint
class WelcomeFragment : Fragment(R.layout.fragment_auth_welcome) {

    private val viewModel: WelcomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAuthWelcomeBinding.bind(view)
        binding.featureFind.bind(R.drawable.ic_location_on_24, R.string.auth_welcome_feature_find)
        binding.featureHold.bind(R.drawable.ic_timer_24, R.string.auth_welcome_feature_hold)
        binding.featureSocial.bind(R.drawable.ic_groups_24, R.string.auth_welcome_feature_social)

        binding.createAccountButton.setOnClickListener { viewModel.onCreateAccount() }
        binding.loginButton.setOnClickListener { viewModel.onLogin() }
        binding.browseButton.setOnClickListener { viewModel.onBrowseAsGuest() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        WelcomeEvent.OpenPlayerHome -> findNavController().navigate(
                            R.id.nav_player,
                            null,
                            navOptions { popUpTo(R.id.nav_main) { inclusive = true } },
                        )
                        WelcomeEvent.NotReady ->
                            Snackbar.make(view, R.string.auth_welcome_coming_soon, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun ItemWelcomeFeatureBinding.bind(@DrawableRes icon: Int, @StringRes text: Int) {
        this.icon.setImageResource(icon)
        this.text.setText(text)
    }
}
