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
    var isLoaded = false

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
        if (isLoaded) return
        
        Log.i(TAG, "Pre-loading game assets from assets directory...")
        
        // Clear stale cached audio assets from previous corrupted runs to force reload clean ones
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
        Log.i(TAG, "Pre-loading game assets completed.")
    }

    private fun loadBitmap(context: Context, filename: String): Bitmap? {
        return try {
            context.assets.open(filename).use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp == null) {
                    Log.e(TAG, "Failed to decode bitmap stream for $filename")
                } else {
                    Log.i(TAG, "Successfully loaded asset: $filename (${bmp.width}x${bmp.height})")
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
            // To bypass assets.openFd restriction on compressed files,
            // copy the asset file to a cache file and load from there.
            val cacheFile = java.io.File(context.cacheDir, filename.replace("/", "_"))
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                context.assets.open(filename).use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            pool.load(cacheFile.absolutePath, 1)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to load sound asset: $filename (might be optional fallback)")
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
                // Pitch-shifted Hit sound as fallback for Spawn
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
                // Celebration explosion
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
                // Low pitch hit sound representing defeat
                pool.play(soundHit, 1f, 1f, 1, 0, 0.6f)
            }
        }
    }
}
