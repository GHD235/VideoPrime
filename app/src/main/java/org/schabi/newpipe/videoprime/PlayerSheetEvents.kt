package org.schabi.newpipe.videoprime

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject

/**
 * PulsePlay: the video page goes down to the mini player or closes. The screen under it (e.g.
 * the Home) was there all along, so Android doesn't tell it that it is seen again; it hears it
 * here and catches up (what was just watched, new videos).
 */
object PlayerSheetEvents {
    private val lowered = PublishSubject.create<Unit>()

    @JvmStatic
    fun onLowered() = lowered.onNext(Unit)

    @JvmStatic
    fun lowered(): Observable<Unit> = lowered
}
