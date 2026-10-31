package org.schabi.newpipe.videoprime

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R

/**
 * PulsePlay: a new version is offered inside the app when it comes to the front, on the phone,
 * the tablet and the TV alike (on a TV notifications are hardly seen, and on the phone the
 * notification can be missed). One glass card in the app's style, usable with the fingers and
 * with the TV remote; "Más tarde" offers it again the next time the app starts. Updating
 * downloads and installs it from here (UpdateActivity).
 */
object UpdateNotice {
    /** The update already offered while the app runs. */
    @Volatile
    private var offered: String? = null

    @JvmStatic
    fun offer(activity: AppCompatActivity) {
        val app = activity.applicationContext
        activity.lifecycleScope.launch {
            val url = withContext(Dispatchers.IO) {
                // asks GitHub at most every 15 minutes; a failure just leaves what is known
                runCatching { UpdateChecker.checkIfDue(app, UpdateChecker.OPEN_CHECK_INTERVAL_MILLIS) }
                UpdateChecker.pendingUpdate(app)
            } ?: return@launch
            if (url == offered || activity.isFinishing ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                (
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                        activity.isInPictureInPictureMode
                    )
            ) {
                return@launch
            }
            offered = url
            show(activity, url)
        }
    }

    private fun show(activity: AppCompatActivity, url: String) {
        val dialog = AppCompatDialog(activity)
        dialog.setContentView(
            ComposeView(activity).apply {
                setContent {
                    VideoPrimeTheme(dark = true) {
                        Card(
                            onUpdate = {
                                markAnswered()
                                dialog.dismiss()
                                activity.startActivity(
                                    Intent(activity, UpdateActivity::class.java)
                                        .putExtra(UpdateActivity.EXTRA_URL, url)
                                )
                            },
                            onLater = {
                                markAnswered()
                                dialog.dismiss()
                            }
                        )
                    }
                }
            }
        )
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        // the screen goes (turned, recreated): the card goes too and is offered again on the new one
        var answered = false
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (dialog.isShowing) dialog.dismiss()
            }
        }
        activity.lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            activity.lifecycle.removeObserver(observer)
            if (!answered) offered = null
        }
        markAnswered = { answered = true }
        dialog.show()
    }

    /** Set while a card is shown: the user chose "Actualizar" or "Más tarde". */
    private var markAnswered: () -> Unit = {}

    @Composable
    private fun Card(onUpdate: () -> Unit, onLater: () -> Unit) {
        val shape = RoundedCornerShape(28.dp)
        val pill = RoundedCornerShape(999.dp)
        val focus = remember { FocusRequester() }
        // the remote's focus starts on "Actualizar"
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, shape, ambientColor = VideoPrimeColors.Accent, spotColor = VideoPrimeColors.Accent)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF2A1F45),
                            0.45f to VideoPrimeColors.DarkSurface,
                            1f to VideoPrimeColors.DarkSurface
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))) {
                    Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
                }
                Text(
                    stringResource(R.string.pulseplay_update_badge),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = VideoPrimeColors.New
                )
                Text(
                    stringResource(R.string.videoprime_update_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = VideoPrimeColors.DarkText,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.pulseplay_update_tv_text),
                    fontSize = 15.sp,
                    color = VideoPrimeColors.DarkTextMuted,
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .focusRequester(focus)
                        .focusRing(pill)
                        .clip(pill)
                        .background(
                            Brush.horizontalGradient(listOf(VideoPrimeColors.Accent, Color(0xFFA21CAF)))
                        )
                        .clickable(onClick = onUpdate),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.pulseplay_update_now),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .focusRing(pill)
                        .clip(pill)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)), pill)
                        .clickable(onClick = onLater),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.pulseplay_update_later),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VideoPrimeColors.DarkText
                    )
                }
            }
        }
    }
}
