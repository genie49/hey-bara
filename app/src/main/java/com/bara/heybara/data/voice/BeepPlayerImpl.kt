package com.bara.heybara.data.voice

import com.bara.heybara.domain.voice.*

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.bara.heybara.R

class BeepPlayerImpl(context: Context) : BeepPlayer {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val beepId: Int = soundPool.load(context, R.raw.beep, 1)

    override fun playBeep(onDone: () -> Unit) {
        soundPool.setOnLoadCompleteListener { _, _, _ ->
            soundPool.play(beepId, 1f, 1f, 1, 0, 1f)
        }
        // SoundPool에 완료 콜백이 없어서 딜레이로 처리
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            onDone()
        }, 300) // beep duration + small buffer
    }

    override fun release() {
        soundPool.release()
    }
}
