package mw.phoneguard.app.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.util.Log

/**
 * Plays the device's default alarm sound on the ALARM audio stream, looping
 * for the duration of the grace period.
 *
 * Why MediaPlayer + RingtoneManager URI (not Ringtone.play):
 *  - Ringtone.play() plays once and stops. It doesn't loop.
 *  - MediaPlayer with isLooping=true keeps playing until we call stop().
 *
 * Why STREAM_ALARM (via AudioAttributes USAGE_ALARM):
 *  - Independent volume from music/media
 *  - Android prioritizes alarm audio
 *  - Users expect "urgent" audio on this stream
 */
class AlarmPlayer(private val context: Context) {

    private val TAG = "PhoneGuardAlarmPlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    fun play() {
        if (isPlaying) {
            Log.d(TAG, "Alarm already playing")
            return
        }

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: run {
                    Log.e(TAG, "No alarm URI available on this device")
                    return
                }

            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context, alarmUri)
            player.isLooping = true
            player.prepare()
            player.start()

            mediaPlayer = player
            isPlaying = true
            Log.i(TAG, "Alarm started (looping) from URI: $alarmUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm: ${e.message}", e)
        }
    }

    fun stop() {
        if (!isPlaying) return
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            Log.i(TAG, "Alarm stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop alarm: ${e.message}", e)
        }
    }

    fun isActive(): Boolean = isPlaying
}