package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.GameAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Trench War (حرب الخنادق)", appName)
  }

  @Test
  fun `verify game assets load successfully`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    GameAssets.loadAll(context)
    
    assertNotNull("Background Landscape should load successfully", GameAssets.bgBitmap)
    assertNotNull("Trench should load successfully", GameAssets.trenchBitmap)
    assertNotNull("Infantry walk sprite should load successfully", GameAssets.spriteInfantryWalk)
    assertNotNull("Infantry shoot sprite should load successfully", GameAssets.spriteInfantryShoot)
    assertNotNull("Grenadier throw sprite should load successfully", GameAssets.spriteGrenadierThrow)
    assertNotNull("Sniper walk sprite should load successfully", GameAssets.spriteSniperWalk)
    assertNotNull("Sniper shoot sprite should load successfully", GameAssets.spriteSniperShoot)
    assertNotNull("Card infantry should load successfully", GameAssets.cardInfantry)
    assertNotNull("Card grenadier should load successfully", GameAssets.cardGrenadier)
    assertNotNull("Card sniper should load successfully", GameAssets.cardSniper)
  }
}
