package org.schabi.newpipe.videoprime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentLiveBinding
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment
import org.schabi.newpipe.util.ThemeHelper

/**
 * VideoPrime: the "Live" section. The most watched live streams of Twitch or YouTube, chosen
 * with the chips on top (the same as in search).
 */
class LiveFragment : Fragment() {
    private var binding: FragmentLiveBinding? = null
    private var serviceId by mutableIntStateOf(ServiceList.Twitch.serviceId)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentLiveBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding ?: return
        savedInstanceState?.let { serviceId = it.getInt(KEY_SERVICE, serviceId) }
        val dark = !ThemeHelper.isLightThemeSelected(requireContext())
        binding.livePlatforms.setContent {
            VideoPrimeTheme(dark = dark) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PLATFORMS.forEach { (id, label, dot) ->
                        val selected = id == serviceId
                        VideoPrimeChip(label, selected, dot) {
                            if (!selected) {
                                serviceId = id
                                showPlatform(id)
                            }
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null) {
            showPlatform(serviceId)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SERVICE, serviceId)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun showPlatform(serviceId: Int) {
        val kiosk = KioskFragment.getInstance(serviceId, LIVE_KIOSK_ID)
        kiosk.useAsFrontPage(true)
        childFragmentManager.beginTransaction()
            .replace(R.id.live_list, kiosk)
            .commit()
    }

    companion object {
        /** Both YouTube and Twitch call their live kiosk "live". */
        private const val LIVE_KIOSK_ID = "live"
        private const val KEY_SERVICE = "videoprime_live_service"

        private val PLATFORMS = listOf(
            Triple(ServiceList.Twitch.serviceId, "Twitch", VideoPrimeColors.Twitch),
            Triple(ServiceList.YouTube.serviceId, "YouTube", VideoPrimeColors.YouTube)
        )
    }
}
