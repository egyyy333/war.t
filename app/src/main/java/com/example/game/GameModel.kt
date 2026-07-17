package com.example.game

import androidx.compose.runtime.Immutable

enum class Faction {
    ALLIES, // الحلفاء (Blue Theme)
    AXIS    // المحور (Red Theme)
}

enum class UnitType {
    INFANTRY,   // المشاة
    GRENADIER,  // قاذفي القنابل
    SNIPER      // القناصة
}

enum class UnitState {
    WALKING,
    IN_TRENCH,
    FIGHTING,
    DYING
}

data class Soldier(
    val id: String,
    val type: UnitType,
    val faction: Faction,
    var x: Float,
    var y: Float,
    var health: Float,
    val maxHealth: Float,
    var state: UnitState = UnitState.WALKING,
    var animFrame: Int = 0,
    var shootCooldown: Int = 0,
    var isEnemyInSight: Boolean = false
) {
    val isDead: Boolean get() = health <= 0f
}

data class Squad(
    val id: String,
    val faction: Faction,
    val type: UnitType,
    val soldiers: MutableList<Soldier>,
    var x: Float, // Center X position of the squad
    var isInTrench: Boolean = false,
    var trenchIndex: Int = -1, // -1 means no trench, 0 = Left Trench, 1 = Right Trench
    var orderedToExit: Boolean = false,
    var isFighting: Boolean = false
) {
    val isDead: Boolean get() = soldiers.all { it.isDead }
}

data class Trench(
    val id: Int, // 0 = Left Trench, 1 = Right Trench
    val centerX: Float,
    val width: Float = 140f,
    val depth: Float = 80f,
    val squads: MutableList<Squad> = mutableListOf(),
    var occupiedFaction: Faction? = null
) {
    val maxCapacity = 3
    val isFull: Boolean get() = squads.size >= maxCapacity
}

data class Projectile(
    val id: String,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    var currentX: Float,
    var currentY: Float,
    val damage: Float,
    val isGrenade: Boolean,
    val faction: Faction,
    var progress: Float = 0f, // 0.0 to 1.0
    val speed: Float = 0.08f
)

@Immutable
data class GameUIState(
    val playerFaction: Faction = Faction.ALLIES,
    val playerSupply: Float = 20f,
    val maxSupply: Float = 100f,
    val playerTrenchSquads: List<Squad> = emptyList(),
    val leftTrenchOccupiedBy: Faction? = null,
    val rightTrenchOccupiedBy: Faction? = null,
    val leftTrenchSquadCount: Int = 0,
    val rightTrenchSquadCount: Int = 0,
    val isGameOver: Boolean = false,
    val didPlayerWin: Boolean = false,
    val gameStage: Int = 1,
    val waveNumber: Int = 1
)
