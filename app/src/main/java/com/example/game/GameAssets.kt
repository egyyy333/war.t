package com.example.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import java.io.IOException

object GameAssets {
    private const val TAG = "GameAssets"

    var isMuted = false
    var isLoaded by mutableStateOf(false)
        private set

    // Diagnostic logs list that UI can observe in real-time
    val loadLogs = mutableStateListOf<String>()

    // Bitmaps (Null if not found in assets)
    var bgBitmap: Bitmap? = null
    var trenchBitmap: Bitmap? = null
    
    // Sprites
    var spriteInfantryWalk: Bitmap? = null
    var spriteInfantryShoot: Bitmap? = null
    var spriteGrenadierThrow: Bitmap? = null
    var spriteSniperWalk: Bitmap? = null
    var spriteSniperShoot: Bitmap? = null

    // UI Cards
    var cardInfantry: Bitmap? = null
    var cardGrenadier: Bitmap? = null
    var cardSniper: Bitmap? = null

    // Sound IDs (0 if not loaded)
    private var soundPool: SoundPool? = null
    var soundSpawn: Int = 0
    var soundShootInfantry: Int = 0
    var soundShootSniper: Int = 0
    var soundExplosion: Int = 0
    var soundHit: Int = 0
    var soundWin: Int = 0
    var soundLose: Int = 0

    fun loadAll(context: Context, force: Boolean = false) {
        if (!force && isLoaded && bgBitmap != null && trenchBitmap != null && spriteInfantryWalk != null) {
            Log.i(TAG, "Assets already fully loaded, skipping reload.")
            return
        }
        
        loadLogs.clear()
        loadLogs.add("⏳ البدء في تحميل ملفات اللعبة والأصوات...")
        Log.i(TAG, "Pre-loading game assets from assets directory (force = $force)...")
        
        // Clear stale cached audio assets from previous runs to force reload clean ones
        try {
            val cacheFiles = context.cacheDir.listFiles()
            cacheFiles?.forEach { file ->
                if (file.name.contains("audio_") || file.name.contains("spawn") || file.name.contains("win") || file.name.contains("lose")) {
                    Log.i(TAG, "Deleting stale cached file: ${file.name}")
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear old cached assets", e)
        }
        
        // Load Images from assets
        bgBitmap = loadBitmap(context, "images/Background Landscape.png")
        trenchBitmap = loadBitmap(context, "images/Trench.png")
        
        // Sprites
        spriteInfantryWalk = loadBitmap(context, "images/sprites/infantry_walk.png")
        spriteInfantryShoot = loadBitmap(context, "images/sprites/infantry_shoot.png")
        spriteGrenadierThrow = loadBitmap(context, "images/sprites/grenadier_throw.png")
        spriteSniperWalk = loadBitmap(context, "images/sprites/sniper_walk.png")
        spriteSniperShoot = loadBitmap(context, "images/sprites/sniper_shoot.png")

        // UI Cards
        cardInfantry = loadBitmap(context, "images/ui/card_infantry.png")
        cardGrenadier = loadBitmap(context, "images/ui/card_grenadier.png")
        cardSniper = loadBitmap(context, "images/ui/card_sniper.png")

        // Load Sounds
        initSoundPool(context)
        
        isLoaded = true
        
        val summaryMsg = "✅ اكتمل التحميل: الخلفية=${bgBitmap != null}, خنادق=${trenchBitmap != null}, جنود=${spriteInfantryWalk != null}, صوت قتال=${soundShootInfantry != 0}"
        Log.i(TAG, summaryMsg)
        loadLogs.add(summaryMsg)
    }

    private fun loadBitmap(context: Context, filename: String): Bitmap? {
        // First try standard high quality decoding
        try {
            context.assets.open(filename).use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) {
                    val msg = "نجح تحميل: $filename (${bmp.width}x${bmp.height})"
                    Log.i(TAG, msg)
                    loadLogs.add("✅ $msg")
                    return bmp
                } else {
                    val msg = "فشل فك تشفير $filename (المعالج أرجع null - يحتمل نقص الذاكرة)"
                    Log.e(TAG, msg)
                    loadLogs.add("⚠️ $msg")
                }
            }
        } catch (oom: OutOfMemoryError) {
            val msg = "نفاد الذاكرة (OOM) أثناء تحميل $filename، جاري إعادة المحاولة بحجم أصغر..."
            Log.e(TAG, msg, oom)
            loadLogs.add("⚠️ $msg")
        } catch (e: Throwable) {
            val msg = "خطأ أثناء فتح ملف الصورة $filename: ${e.localizedMessage}"
            Log.e(TAG, msg, e)
            loadLogs.add("❌ $msg")
            return null
        }

        // Retries with progressive downsampling if initial decode returned null (native OOM) or threw OOM
        for (sampleSize in listOf(2, 4)) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                context.assets.open(filename).use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream, null, options)
                    if (bmp != null) {
                        val msg = "تم تحميل نسخة مصغرة (1/$sampleSize) من: $filename (${bmp.width}x${bmp.height})"
                        Log.i(TAG, msg)
                        loadLogs.add("✅ $msg")
                        return bmp
                    }
                }
            } catch (e2: Throwable) {
                val msg = "فشل التحميل المصغر لـ $filename بالحجم 1/$sampleSize"
                Log.e(TAG, msg, e2)
                loadLogs.add("❌ $msg")
            }
        }
        return null
    }

    private fun initSoundPool(context: Context) {
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(attributes)
                .build()

            soundPool?.let { pool ->
                soundSpawn = loadSound(context, pool, "spawn.wav") // fallback
                soundShootInfantry = loadSound(context, pool, "audio/infantry_shoot.mp3")
                soundShootSniper = loadSound(context, pool, "audio/sniper_shoot.mp3")
                soundExplosion = loadSound(context, pool, "audio/explode.mp3")
                soundHit = loadSound(context, pool, "audio/soldier_hit.mp3")
                soundWin = loadSound(context, pool, "win.wav") // fallback
                soundLose = loadSound(context, pool, "lose.wav") // fallback
            }
        } catch (e: Exception) {
            val msg = "فشل تهيئة مجمع الأصوات SoundPool: ${e.localizedMessage}"
            Log.e(TAG, msg, e)
            loadLogs.add("❌ $msg")
        }
    }

    private fun loadSound(context: Context, pool: SoundPool, filename: String): Int {
        // 1. Primary standard secure method via AssetFileDescriptor (perfect for physical devices)
        try {
            context.assets.openFd(filename).use { afd ->
                val soundId = pool.load(afd, 1)
                if (soundId != 0) {
                    val msg = "نجح تحميل الصوت الأصلي: $filename (مُعرف: $soundId)"
                    Log.i(TAG, msg)
                    loadLogs.add("✅ $msg")
                    return soundId
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed direct openFd for sound $filename, falling back to cache file approach", e)
        }

        // 2. Fallback to copy-to-cache approach (for cases where AAPT compressed it on custom build setups)
        return try {
            val cacheFile = java.io.File(context.cacheDir, filename.replace("/", "_"))
            context.assets.open(filename).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile.setReadable(true, false) // Ensure world readable permissions
            val soundId = pool.load(cacheFile.absolutePath, 1)
            val msg = "تم تحميل الصوت عبر التخزين المؤقت: $filename (مُعرف: $soundId)"
            Log.i(TAG, msg)
            loadLogs.add("✅ $msg")
            soundId
        } catch (e: Exception) {
            val msg = "فشل تحميل ملف الصوت $filename: ${e.localizedMessage}"
            Log.d(TAG, msg)
            if (!filename.endsWith(".wav")) { // Hide failures of optional fallback wav files
                loadLogs.add("⚠️ $msg")
            }
            0
        }
    }

    // Sound playing functions
    fun playSpawn() {
        if (isMuted) return
        soundPool?.let { pool ->
            if (soundSpawn != 0) {
                pool.play(soundSpawn, 1f, 1f, 1, 0, 1f)
            } else if (soundHit != 0) {
                pool.play(soundHit, 0.5f, 0.5f, 1, 0, 1.2f)
            }
        }
    }

    fun playShoot(isSniper: Boolean = false) {
        if (isMuted) return
        soundPool?.let { pool ->
            val soundId = if (isSniper) soundShootSniper else soundShootInfantry
            if (soundId != 0) {
                pool.play(soundId, 0.8f, 0.8f, 1, 0, 1f)
            } else if (soundShootInfantry != 0) {
                pool.play(soundShootInfantry, 0.8f, 0.8f, 1, 0, 1f)
            }
        }
    }

    fun playExplosion() {
        if (isMuted) return
        soundPool?.let { pool ->
            if (soundExplosion != 0) pool.play(soundExplosion, 1f, 1f, 1, 0, 1f)
        }
    }

    fun playHit() {
        if (isMuted) return
        soundPool?.let { pool ->
            if (soundHit != 0) pool.play(soundHit, 0.8f, 0.8f, 1, 0, 1f)
        }
    }

    fun playWin() {
        if (isMuted) return
        soundPool?.let { pool ->
            if (soundWin != 0) {
                pool.play(soundWin, 1f, 1f, 1, 0, 1f)
            } else if (soundExplosion != 0) {
                pool.play(soundExplosion, 1f, 1f, 1, 0, 1.2f)
            }
        }
    }

    fun playLose() {
        if (isMuted) return
        soundPool?.let { pool ->
            if (soundLose != 0) {
                pool.play(soundLose, 1f, 1f, 1, 0, 1f)
            } else if (soundHit != 0) {
                pool.play(soundHit, 1f, 1f, 1, 0, 0.6f)
            }
        }
    }
}
