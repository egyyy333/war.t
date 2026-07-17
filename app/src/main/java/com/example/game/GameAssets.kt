package com.example.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.IOException

object GameAssets {
    private const val TAG = "GameAssets"

    var isMuted = false

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
    private var soundSpawn: Int = 0
    private var soundShootInfantry: Int = 0
    private var soundShootSniper: Int = 0
    private var soundExplosion: Int = 0
    private var soundHit: Int = 0
    private var soundWin: Int = 0
    private var soundLose: Int = 0

    fun loadAll(context: Context) {
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
    }

    private fun loadBitmap(context: Context, filename: String): Bitmap? {
        return try {
            // Safe decoding to avoid OutOfMemoryError on large files
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.assets.open(filename).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Limit maximum dimension to 1024px to keep memory consumption low
            val reqWidth = 1024
            val reqHeight = 1024
            var inSampleSize = 1

            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            context.assets.open(filename).use { stream ->
                val bmp = BitmapFactory.decodeStream(stream, null, decodeOptions)
                if (bmp == null) {
                    Log.e(TAG, "Failed to decode bitmap stream for $filename")
                } else {
                    Log.i(TAG, "Successfully loaded asset: $filename (${bmp.width}x${bmp.height}) at sampleSize $inSampleSize")
                }
                bmp
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception opening or decoding asset file: $filename", e)
            null
        }
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
            Log.e(TAG, "Failed to initialize SoundPool", e)
        }
    }

    private fun loadSound(context: Context, pool: SoundPool, filename: String): Int {
        return try {
            val fd = context.assets.openFd(filename)
            pool.load(fd, 1)
        } catch (e: IOException) {
            Log.d(TAG, "Asset sound not found: $filename")
            0
        }
    }

    // Sound playing functions
    fun playSpawn() {
        if (isMuted) return
        soundPool?.let { pool ->
            if (soundSpawn != 0) pool.play(soundSpawn, 1f, 1f, 1, 0, 1f)
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
            if (soundWin != 0) pool.play(soundWin, 1f, 1f, 1, 0, 1f)
        }
    }

    fun playLose() {
        if (isMuted) return
        soundPool?.let { pool ->
            if (soundLose != 0) pool.play(soundLose, 1f, 1f, 1, 0, 1f)
        }
    }
}
