package org.schabi.newpipe.videoprime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentLiveBinding
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment

/**
 * VideoPrime: the "Live" section. The most watched live streams of Twitch or YouTube, or the
 * free-to-air TV channels, chosen with the chips on top.
 */
class LiveFragment : Fragment() {
    private var binding: FragmentLiveBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentLiveBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding ?: return

        binding.livePlatforms.setOnCheckedStateChangeListener { _, checkedIds ->
            showPlatform(checkedIds.firstOrNull() ?: R.id.live_twitch)
        }
        if (savedInstanceState == null) {
            showPlatform(binding.livePlatforms.checkedChipId)
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun showPlatform(chipId: Int) {
        val fragment: Fragment = when (chipId) {
            R.id.live_tv -> TvFragment()
            R.id.live_youtube -> liveKiosk(ServiceList.YouTube.serviceId)
            else -> liveKiosk(ServiceList.Twitch.serviceId)
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.live_list, fragment)
            .commit()
    }

    private fun liveKiosk(serviceId: Int): Fragment = KioskFragment.getInstance(serviceId, LIVE_KIOSK_ID).also { it.useAsFrontPage(true) }

    companion object {
        /** Both YouTube and Twitch call their live kiosk "live". */
        private const val LIVE_KIOSK_ID = "live"
    }
}
