package io.github.bbzq.feats.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object MediaMuxerUtil {

    /**
     * 合并视频和音频流到一个 MP4 文件中。
     *
     * @param videoPath 视频文件路径 (m4s)
     * @param audioPath 音频文件路径 (m4s)
     * @param outPath   输出文件路径 (mp4)
     * @return 成功返回 true，失败返回 false
     */
    fun mux(videoPath: String, audioPath: String, outPath: String): Boolean {
        if (!File(videoPath).exists() || !File(audioPath).exists()) {
            return false
        }

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoPath)
            var videoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIndex = i
                    break
                }
            }

            audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(audioPath)
            var audioTrackIndex = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioTrackIndex = i
                    break
                }
            }

            if (videoTrackIndex == -1 || audioTrackIndex == -1) {
                return false
            }

            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)

            muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outVideoTrackIndex = muxer.addTrack(videoFormat)
            val outAudioTrackIndex = muxer.addTrack(audioFormat)

            muxer.start()

            val bufferSize = 1024 * 1024 // 1MB buffer
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Write video
            var videoPtsOffset = 0L
            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    break
                }
                val pts = videoExtractor.sampleTime
                if (videoPtsOffset == 0L) {
                    videoPtsOffset = pts
                }
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = pts - videoPtsOffset
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(outVideoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Write audio
            var audioPtsOffset = 0L
            while (true) {
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    break
                }
                val pts = audioExtractor.sampleTime
                if (audioPtsOffset == 0L) {
                    audioPtsOffset = pts
                }
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = pts - audioPtsOffset
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(outAudioTrackIndex, buffer, bufferInfo)
                audioExtractor.advance()
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try {
                videoExtractor?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                audioExtractor?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
