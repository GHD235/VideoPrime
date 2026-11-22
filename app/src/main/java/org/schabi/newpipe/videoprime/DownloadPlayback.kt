package org.schabi.newpipe.videoprime

import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.NavigationHelper
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.get.Mission

/**
 * PulsePlay: tapping a download in the downloads screen plays it in the app (NewPipe opened the
 * file in another app). A finished one plays the file (audio in the background player, video in
 * the main player, as in Library → Downloads). One still downloading opens its video from the
 * internet in the app's player while the download goes on: the part already downloaded cannot be
 * played on its own (video and audio are downloaded apart, in pieces out of order, and only
 * joined into a playable file at the end).
 */
object DownloadPlayback {
    @JvmStatic
    fun play(context: Context, mission: Mission) {
        if (mission is FinishedMission) playFile(context, mission) else playSource(context, mission)
    }

    private fun playFile(context: Context, mission: FinishedMission) {
        val app = context.applicationContext
        val uri = runCatching { mission.storage?.uri?.toString() }.getOrNull() ?: return
        Single.fromCallable { LocalMedia.listDownloads(app).firstOrNull { it.url == uri } ?: NONE }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ file ->
                if (file === NONE) {
                    Toast.makeText(app, R.string.missing_file, Toast.LENGTH_SHORT).show()
                    return@subscribe
                }
                val queue = SinglePlayQueue(file.toInfoItem())
                if (file.isAudio) {
                    NavigationHelper.playOnBackgroundPlayer(app, queue, true)
                } else {
                    NavigationHelper.playOnMainPlayer(context, queue, false)
                }
            }, { Toast.makeText(app, R.string.missing_file, Toast.LENGTH_SHORT).show() })
    }

    private fun playSource(context: Context, mission: Mission) {
        val source = mission.source ?: return
        runCatching {
            val intent = NavigationHelper.getIntentByLink(context, source)
            intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
            context.startActivity(intent)
        }
    }

    private val NONE = LocalFile("", "", "", null, 0, false, 0, 0, "", "")
}
