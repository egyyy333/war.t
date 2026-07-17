package com.example.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow

class GameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GameUIState())
    val uiState: StateFlow<GameUIState> = _uiState.asStateFlow()

    // Internal game state
    var selectedFaction = Faction.ALLIES
    var playerSupply = 30f
    var enemySupply = 30f
    val maxSupply = 100f

    val playerSquads = mutableListOf<Squad>()
    val enemySquads = mutableListOf<Squad>()

    val leftTrench = Trench(id = 0, centerX = 280f)
    val rightTrench = Trench(id = 1, centerX = 720f)

    val projectiles = mutableListOf<Projectile>()

    // Cooldowns (in frames, 60fps)
    var spawnCooldownInfantry = 0
    var spawnCooldownGrenadier = 0
    var spawnCooldownSniper = 0

    private var gameJob: Job? = null
    private var isEngineRunning = false

    init {
        // We start with main menu state
    }

    fun startGame(faction: Faction, stage: Int = 1) {
        selectedFaction = faction
        playerSupply = 35f
        enemySupply = 25f + stage * 10f // Enemy starts with more supply in higher stages

        playerSquads.clear()
        enemySquads.clear()
        projectiles.clear()

        leftTrench.squads.clear()
        leftTrench.occupiedFaction = if (faction == Faction.ALLIES) Faction.AXIS else Faction.ALLIES // Enemy starts in their trench
        
        rightTrench.squads.clear()
        rightTrench.occupiedFaction = faction // Player starts with their trench

        // Spawn 1 initial squad for enemy in their trench to make it challenging
        spawnSquadForEnemy(UnitType.INFANTRY, inTrench = true)

        spawnCooldownInfantry = 0
        spawnCooldownGrenadier = 0
        spawnCooldownSniper = 0

        _uiState.value = GameUIState(
            playerFaction = faction,
            playerSupply = playerSupply,
            maxSupply = maxSupply,
            playerTrenchSquads = emptyList(),
            leftTrenchOccupiedBy = leftTrench.occupiedFaction,
            rightTrenchOccupiedBy = rightTrench.occupiedFaction,
            leftTrenchSquadCount = leftTrench.squads.size,
            rightTrenchSquadCount = rightTrench.squads.size,
            isGameOver = false,
            didPlayerWin = false,
            gameStage = stage
        )

        startLoop()
    }

    private fun startLoop() {
        gameJob?.cancel()
        isEngineRunning = true
        gameJob = viewModelScope.launch {
            while (isEngineRunning) {
                updateGameFrame()
                delay(16) // Approx 60 FPS
            }
        }
    }

    fun pauseGame() {
        isEngineRunning = false
        gameJob?.cancel()
    }

    fun resumeGame() {
        if (!uiState.value.isGameOver) {
            startLoop()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pauseGame()
    }

    // --- GAME ACTIONS ---

    fun spawnPlayerSquad(type: UnitType): Boolean {
        if (uiState.value.isGameOver) return false

        val cost = getUnitCost(type)
        if (playerSupply < cost) return false

        // Check individual cooldowns
        when (type) {
            UnitType.INFANTRY -> if (spawnCooldownInfantry > 0) return false
            UnitType.GRENADIER -> if (spawnCooldownGrenadier > 0) return false
            UnitType.SNIPER -> if (spawnCooldownSniper > 0) return false
        }

        playerSupply -= cost

        // Set cooldowns (in frames, e.g. 60 frames = 1 sec)
        when (type) {
            UnitType.INFANTRY -> spawnCooldownInfantry = 120 // 2 seconds
            UnitType.GRENADIER -> spawnCooldownGrenadier = 240 // 4 seconds
            UnitType.SNIPER -> spawnCooldownSniper = 360 // 6 seconds
        }

        // Spawn on the RIGHT side, since player moves from Right (1000f) to Left (0f)
        val squadId = UUID.randomUUID().toString()
        val numSoldiers = getSoldierCountForType(type)
        val soldiers = mutableListOf<Soldier>()

        for (i in 0 until numSoldiers) {
            val offsetMultiplier = if (numSoldiers > 1) (i - (numSoldiers - 1) / 2f) else 0f
            val soldierX = 1000f + offsetMultiplier * 15f
            val soldierY = 340f + (i % 2) * 8f - 4f // Small Y offset for natural squad depth
            
            soldiers.add(
                Soldier(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    faction = selectedFaction,
                    x = soldierX,
                    y = soldierY,
                    health = getUnitMaxHealth(type),
                    maxHealth = getUnitMaxHealth(type)
                )
            )
        }

        val squad = Squad(
            id = squadId,
            faction = selectedFaction,
            type = type,
            soldiers = soldiers,
            x = 1000f
        )

        playerSquads.add(squad)
        return true
    }

    fun orderSquadExitTrench(squadId: String) {
        val squad = playerSquads.find { it.id == squadId } ?: return
        if (squad.isInTrench) {
            squad.isInTrench = false
            squad.orderedToExit = true
            
            // Remove from the trench it was in
            if (squad.trenchIndex == 0) {
                leftTrench.squads.remove(squad)
                if (leftTrench.squads.isEmpty()) {
                    leftTrench.occupiedFaction = null
                }
            } else if (squad.trenchIndex == 1) {
                rightTrench.squads.remove(squad)
                if (rightTrench.squads.isEmpty()) {
                    rightTrench.occupiedFaction = null
                }
            }
            squad.trenchIndex = -1

            // Instantly transition soldiers back to walking ground Y level smoothly
            squad.soldiers.forEach {
                it.state = UnitState.WALKING
                it.y = 340f + (playerSquads.indexOf(squad) % 3) * 6f
            }
        }
    }

    // --- GAME LOOP LOGIC ---

    private fun updateGameFrame() {
        // 1. Supply Regeneration
        if (playerSupply < maxSupply) {
            playerSupply = (playerSupply + 0.05f).coerceAtMost(maxSupply)
        }
        if (enemySupply < maxSupply) {
            enemySupply = (enemySupply + 0.04f + (uiState.value.gameStage * 0.01f)).coerceAtMost(maxSupply)
        }

        // Decrement Cooldowns
        if (spawnCooldownInfantry > 0) spawnCooldownInfantry--
        if (spawnCooldownGrenadier > 0) spawnCooldownGrenadier--
        if (spawnCooldownSniper > 0) spawnCooldownSniper--

        // 2. Enemy AI Spawning and Decisions
        handleEnemyAI()

        // 3. Projectile Updates
        updateProjectiles()

        // 4. Update Squads and Soldier Movements/Combat
        updateSquads(playerSquads, isPlayer = true)
        updateSquads(enemySquads, isPlayer = false)

        // 5. Check Win/Loss Conditions
        checkGameStatus()

        // 6. Update UI State
        _uiState.update {
            GameUIState(
                playerFaction = selectedFaction,
                playerSupply = playerSupply,
                maxSupply = maxSupply,
                playerTrenchSquads = playerSquads.filter { it.isInTrench },
                leftTrenchOccupiedBy = leftTrench.occupiedFaction,
                rightTrenchOccupiedBy = rightTrench.occupiedFaction,
                leftTrenchSquadCount = leftTrench.squads.size,
                rightTrenchSquadCount = rightTrench.squads.size,
                isGameOver = uiState.value.isGameOver,
                didPlayerWin = uiState.value.didPlayerWin,
                gameStage = uiState.value.gameStage
            )
        }
    }

    private fun handleEnemyAI() {
        // Enemy is opposite faction
        val enemyFaction = if (selectedFaction == Faction.ALLIES) Faction.AXIS else Faction.ALLIES

        // AI Spawning Strategy
        val decisionFactor = Math.random()
        if (decisionFactor < 0.005) { // Slow chance of spawning
            val types = listOf(UnitType.INFANTRY, UnitType.GRENADIER, UnitType.SNIPER)
            val randomType = types.random()
            val cost = getUnitCost(randomType)
            if (enemySupply >= cost) {
                enemySupply -= cost
                spawnSquadForEnemy(randomType, inTrench = false)
            }
        }

        // AI Trench Exit Decisions
        // Every ~3 seconds, enemy AI checks its squads in Left Trench (its home) and might order them to attack the player
        if (Math.random() < 0.004) {
            if (leftTrench.squads.isNotEmpty()) {
                val squadToExit = leftTrench.squads.random()
                if (squadToExit.faction == enemyFaction) {
                    squadToExit.isInTrench = false
                    squadToExit.orderedToExit = true
                    leftTrench.squads.remove(squadToExit)
                    if (leftTrench.squads.isEmpty()) {
                        leftTrench.occupiedFaction = null
                    }
                    squadToExit.trenchIndex = -1
                    squadToExit.soldiers.forEach {
                        it.state = UnitState.WALKING
                        it.y = 340f + (enemySquads.indexOf(squadToExit) % 3) * 6f
                    }
                }
            }
        }

        // AI can also order exit from Right Trench if it captures it
        if (Math.random() < 0.005) {
            if (rightTrench.squads.isNotEmpty()) {
                val squadToExit = rightTrench.squads.random()
                if (squadToExit.faction == enemyFaction) {
                    squadToExit.isInTrench = false
                    squadToExit.orderedToExit = true
                    rightTrench.squads.remove(squadToExit)
                    if (rightTrench.squads.isEmpty()) {
                        rightTrench.occupiedFaction = null
                    }
                    squadToExit.trenchIndex = -1
                    squadToExit.soldiers.forEach {
                        it.state = UnitState.WALKING
                        it.y = 340f + (enemySquads.indexOf(squadToExit) % 3) * 6f
                    }
                }
            }
        }
    }

    private fun spawnSquadForEnemy(type: UnitType, inTrench: Boolean) {
        val enemyFaction = if (selectedFaction == Faction.ALLIES) Faction.AXIS else Faction.ALLIES
        val squadId = UUID.randomUUID().toString()
        val numSoldiers = getSoldierCountForType(type)
        val soldiers = mutableListOf<Soldier>()

        // Enemy spawns on the LEFT side (0f)
        val startX = if (inTrench) leftTrench.centerX else 0f

        for (i in 0 until numSoldiers) {
            val offsetMultiplier = if (numSoldiers > 1) (i - (numSoldiers - 1) / 2f) else 0f
            val soldierX = startX + offsetMultiplier * 15f
            val soldierY = if (inTrench) {
                // Vertical slot coordinates inside trench
                250f + i * 40f
            } else {
                340f + (i % 2) * 8f - 4f
            }

            soldiers.add(
                Soldier(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    faction = enemyFaction,
                    x = soldierX,
                    y = soldierY,
                    health = getUnitMaxHealth(type),
                    maxHealth = getUnitMaxHealth(type),
                    state = if (inTrench) UnitState.IN_TRENCH else UnitState.WALKING
                )
            )
        }

        val squad = Squad(
            id = squadId,
            faction = enemyFaction,
            type = type,
            soldiers = soldiers,
            x = startX,
            isInTrench = inTrench,
            trenchIndex = if (inTrench) 0 else -1
        )

        enemySquads.add(squad)
        if (inTrench) {
            leftTrench.squads.add(squad)
            leftTrench.occupiedFaction = enemyFaction
        }
    }

    private fun updateSquads(squads: MutableList<Squad>, isPlayer: Boolean) {
        val iterator = squads.iterator()
        while (iterator.hasNext()) {
            val squad = iterator.next()

            // Remove dead soldiers
            squad.soldiers.removeAll { it.isDead }

            if (squad.isDead) {
                // If it was in a trench, remove from trench
                if (squad.trenchIndex == 0) {
                    leftTrench.squads.remove(squad)
                    if (leftTrench.squads.isEmpty()) leftTrench.occupiedFaction = null
                } else if (squad.trenchIndex == 1) {
                    rightTrench.squads.remove(squad)
                    if (rightTrench.squads.isEmpty()) rightTrench.occupiedFaction = null
                }
                iterator.remove()
                continue
            }

            // Update squad average X position
            if (squad.soldiers.isNotEmpty()) {
                squad.x = squad.soldiers.map { it.x }.average().toFloat()
            }

            // Handle States & Movements
            if (squad.isInTrench) {
                squad.isFighting = false
                // Position soldiers vertically inside the trench
                val slotIndex = getSquadTrenchSlot(squad)
                val trenchObj = if (squad.trenchIndex == 0) leftTrench else rightTrench

                squad.soldiers.forEachIndexed { i, soldier ->
                    soldier.state = UnitState.IN_TRENCH
                    // Each slot inside trench stands at a specific vertical zone:
                    // Slot 0 -> top (Y = 230f)
                    // Slot 1 -> middle (Y = 310f)
                    // Slot 2 -> bottom (Y = 390f)
                    val targetY = 220f + slotIndex * 65f + (i * 12f)
                    soldier.x = soldier.x * 0.9f + trenchObj.centerX * 0.1f // smooth center snap
                    soldier.y = soldier.y * 0.8f + targetY * 0.2f // smooth slide down
                }

                // Combat while defending trench: auto fights any approaching enemy!
                val enemies = if (isPlayer) enemySquads else playerSquads
                val targetSquad = findClosestEnemySquad(squad, enemies)
                if (targetSquad != null) {
                    val distance = abs(squad.x - targetSquad.x)
                    squad.soldiers.forEach { soldier ->
                        val range = getUnitRange(soldier.type)
                        if (distance <= range) {
                            soldier.isEnemyInSight = true
                            soldier.state = UnitState.IN_TRENCH // Keep trench state
                            handleSoldierShooting(soldier, targetSquad)
                        } else {
                            soldier.isEnemyInSight = false
                        }
                    }
                }
            } else {
                // WALKING / COMBAT FIELD LOGIC
                val enemies = if (isPlayer) enemySquads else playerSquads
                val targetSquad = findClosestEnemySquad(squad, enemies)
                
                var isBlockedByCombat = false

                if (targetSquad != null) {
                    val distance = abs(squad.x - targetSquad.x)
                    val minRange = squad.soldiers.maxOfOrNull { getUnitRange(it.type) } ?: 150f
                    
                    if (distance <= minRange) {
                        isBlockedByCombat = true
                        squad.isFighting = true
                        
                        squad.soldiers.forEach { soldier ->
                            val range = getUnitRange(soldier.type)
                            if (distance <= range) {
                                soldier.state = UnitState.FIGHTING
                                handleSoldierShooting(soldier, targetSquad)
                            } else {
                                // If not in range, walk towards it
                                soldier.state = UnitState.WALKING
                                walkTowardsEnemy(soldier, isPlayer)
                            }
                        }
                    }
                }

                if (!isBlockedByCombat) {
                    squad.isFighting = false
                    squad.soldiers.forEach { soldier ->
                        soldier.state = UnitState.WALKING
                        
                        // Trench Entering checks
                        val enteredTrench = checkTrenchEntrance(squad, soldier, isPlayer)
                        if (!enteredTrench) {
                            walkTowardsEnemy(soldier, isPlayer)
                        }
                    }
                }
            }
        }
    }

    private fun walkTowardsEnemy(soldier: Soldier, isPlayer: Boolean) {
        val speed = getUnitSpeed(soldier.type)
        if (isPlayer) {
            // Player moves right to left (decrease X)
            soldier.x -= speed
        } else {
            // Enemy moves left to right (increase X)
            soldier.x += speed
        }
        // Walk animation
        if (Math.random() < 0.1) {
            soldier.animFrame = (soldier.animFrame + 1) % 4
        }
    }

    private fun checkTrenchEntrance(squad: Squad, soldier: Soldier, isPlayer: Boolean): Boolean {
        // Player moves Right -> Left.
        // First trench on the way is Right Trench (centerX = 720f)
        // Second trench on the way is Left Trench (centerX = 280f)
        
        if (isPlayer) {
            // If they are between 715f and 735f, and haven't ordered to exit Right Trench yet:
            if (soldier.x in 710f..745f && !squad.orderedToExit && squad.trenchIndex != 1) {
                if (rightTrench.squads.size < rightTrench.maxCapacity) {
                    if (!rightTrench.squads.contains(squad)) {
                        rightTrench.squads.add(squad)
                        rightTrench.occupiedFaction = selectedFaction
                    }
                    squad.isInTrench = true
                    squad.trenchIndex = 1
                    return true
                } else {
                    // This is player's 4th squad! "تنزل الى الخندق ثم تخرج هذه المجموعة الرابعة فقط من الخندق تلقاءي"
                    // It descends briefly (we let it descend by making it enter but marking it exit immediately!)
                    if (!rightTrench.squads.contains(squad)) {
                        // We trigger immediate automatic exit so it walks past!
                        squad.orderedToExit = true
                    }
                }
            }

            // Left Trench entrance (enemy trench)
            // Can enter Left Trench if empty or occupied by Player (Allies)
            if (soldier.x in 270f..300f && squad.trenchIndex != 0) {
                if (leftTrench.occupiedFaction == null || leftTrench.occupiedFaction == selectedFaction) {
                    if (leftTrench.squads.size < leftTrench.maxCapacity) {
                        if (!leftTrench.squads.contains(squad)) {
                            leftTrench.squads.add(squad)
                            leftTrench.occupiedFaction = selectedFaction
                            // Reset orderedToExit so they stay in this new trench!
                            squad.orderedToExit = false
                        }
                        squad.isInTrench = true
                        squad.trenchIndex = 0
                        return true
                    }
                }
            }
        } else {
            // Enemy moves Left -> Right
            // First trench on the way is Left Trench (centerX = 280f)
            val enemyFaction = if (selectedFaction == Faction.ALLIES) Faction.AXIS else Faction.ALLIES
            if (soldier.x in 265f..295f && !squad.orderedToExit && squad.trenchIndex != 0) {
                if (leftTrench.squads.size < leftTrench.maxCapacity) {
                    if (!leftTrench.squads.contains(squad)) {
                        leftTrench.squads.add(squad)
                        leftTrench.occupiedFaction = enemyFaction
                    }
                    squad.isInTrench = true
                    squad.trenchIndex = 0
                    return true
                } else {
                    // Enemy's 4th squad descends then exits automatically
                    squad.orderedToExit = true
                }
            }

            // Right Trench entrance for enemy
            if (soldier.x in 700f..730f && squad.trenchIndex != 1) {
                if (rightTrench.occupiedFaction == null || rightTrench.occupiedFaction == enemyFaction) {
                    if (rightTrench.squads.size < rightTrench.maxCapacity) {
                        if (!rightTrench.squads.contains(squad)) {
                            rightTrench.squads.add(squad)
                            rightTrench.occupiedFaction = enemyFaction
                            squad.orderedToExit = false
                        }
                        squad.isInTrench = true
                        squad.trenchIndex = 1
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun handleSoldierShooting(soldier: Soldier, targetSquad: Squad) {
        if (soldier.shootCooldown > 0) {
            soldier.shootCooldown--
            return
        }

        // Target a random living soldier in the target squad
        val aliveTargets = targetSquad.soldiers.filter { !it.isDead }
        if (aliveTargets.isEmpty()) return

        val targetSoldier = aliveTargets.random()

        // Spawn a visual projectile
        val projId = UUID.randomUUID().toString()
        val isGrenade = soldier.type == UnitType.GRENADIER
        
        val p = Projectile(
            id = projId,
            startX = soldier.x,
            startY = soldier.y - 15f, // Muzzle height
            endX = targetSoldier.x,
            endY = targetSoldier.y - 12f,
            currentX = soldier.x,
            currentY = soldier.y - 15f,
            damage = getUnitDamage(soldier.type),
            isGrenade = isGrenade,
            faction = soldier.faction
        )
        projectiles.add(p)

        // Play weapon firing sound
        if (!isGrenade) {
            GameAssets.playShoot(isSniper = (soldier.type == UnitType.SNIPER))
        }

        // Set shooter cooldown
        soldier.shootCooldown = getUnitShootCooldown(soldier.type)
    }

    private fun updateProjectiles() {
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.progress += p.speed
            
            if (p.progress >= 1.0f) {
                // Impact! Apply damage
                applyProjectileImpact(p)
                iterator.remove()
            } else {
                // Update position
                p.currentX = p.startX + (p.endX - p.startX) * p.progress
                if (p.isGrenade) {
                    // Parabolic arc for grenade: Y goes UP (negative) in the middle
                    val arcHeight = -50f
                    val arc = 4f * p.progress * (1.0f - p.progress) * arcHeight
                    p.currentY = p.startY + (p.endY - p.startY) * p.progress + arc
                } else {
                    p.currentY = p.startY + (p.endY - p.startY) * p.progress
                }
            }
        }
    }

    private fun applyProjectileImpact(proj: Projectile) {
        // Apply damage to units close to end position
        val targetSquads = if (proj.faction == selectedFaction) enemySquads else playerSquads

        if (proj.isGrenade) {
            // Grenade deals area damage to ALL soldiers in a small radius!
            targetSquads.forEach { squad ->
                squad.soldiers.forEach { soldier ->
                    val dist = abs(soldier.x - proj.endX)
                    if (dist < 40f && !soldier.isDead) {
                        val baseDamage = proj.damage
                        val damageDealt = calculateDamageModified(baseDamage, soldier, UnitType.GRENADIER)
                        soldier.health -= damageDealt
                    }
                }
            }
        } else {
            // Bullet hits closest soldier
            var closestSoldier: Soldier? = null
            var minDist = 9999f
            targetSquads.forEach { squad ->
                squad.soldiers.forEach { soldier ->
                    val dist = abs(soldier.x - proj.endX)
                    if (dist < minDist && !soldier.isDead) {
                        minDist = dist
                        closestSoldier = soldier
                    }
                }
            }

            closestSoldier?.let { soldier ->
                val baseDamage = proj.damage
                // Find shooter type to get defense reduction
                // We assume default multipliers if shooter is unknown
                val damageDealt = calculateDamageModified(baseDamage, soldier, if (baseDamage > 40f) UnitType.SNIPER else UnitType.INFANTRY)
                soldier.health -= damageDealt
                GameAssets.playHit()
            }
        }
    }

    private fun calculateDamageModified(baseDamage: Float, target: Soldier, shooterType: UnitType): Float {
        if (target.state == UnitState.IN_TRENCH) {
            // Fortified defense multipliers:
            // "المشاة تأثيرهم قليل على من في الخندق واما قاذفي القنابل فتأثيرهم اقوى ثم القناصة"
            // Infantry deals 15% damage (85% reduction)
            // Grenadier deals 65% damage (35% reduction)
            // Sniper deals 85% damage (15% reduction)
            return when (shooterType) {
                UnitType.INFANTRY -> baseDamage * 0.15f
                UnitType.GRENADIER -> baseDamage * 0.65f
                UnitType.SNIPER -> baseDamage * 0.85f
            }
        }
        return baseDamage
    }

    private fun checkGameStatus() {
        if (uiState.value.isGameOver) return

        // Win Condition: Player squad reaches Left Screen Edge (X <= 30f)
        val playerReachedLeft = playerSquads.any { squad -> squad.x <= 40f && !squad.isInTrench }
        if (playerReachedLeft) {
            _uiState.update { it.copy(isGameOver = true, didPlayerWin = true) }
            pauseGame()
            GameAssets.playWin()
            return
        }

        // Loss Condition: Enemy squad reaches Right Screen Edge (X >= 970f)
        val enemyReachedRight = enemySquads.any { squad -> squad.x >= 960f && !squad.isInTrench }
        if (enemyReachedRight) {
            _uiState.update { it.copy(isGameOver = true, didPlayerWin = false) }
            pauseGame()
            GameAssets.playLose()
            return
        }
    }

    // --- CONVENIENCE HELPERS ---

    private fun findClosestEnemySquad(squad: Squad, enemies: List<Squad>): Squad? {
        if (enemies.isEmpty()) return null
        return enemies.minByOrNull { abs(it.x - squad.x) }
    }

    private fun getSquadTrenchSlot(squad: Squad): Int {
        val trenchObj = if (squad.trenchIndex == 0) leftTrench else rightTrench
        return trenchObj.squads.indexOf(squad).coerceIn(0, 2)
    }

    fun getUnitCost(type: UnitType): Float {
        return when (type) {
            UnitType.INFANTRY -> 15f
            UnitType.GRENADIER -> 25f
            UnitType.SNIPER -> 35f
        }
    }

    private fun getSoldierCountForType(type: UnitType): Int {
        return when (type) {
            UnitType.INFANTRY -> 3
            UnitType.GRENADIER -> 2
            UnitType.SNIPER -> 1
        }
    }

    private fun getUnitMaxHealth(type: UnitType): Float {
        return when (type) {
            UnitType.INFANTRY -> 80f
            UnitType.GRENADIER -> 100f
            UnitType.SNIPER -> 60f
        }
    }

    private fun getUnitDamage(type: UnitType): Float {
        return when (type) {
            UnitType.INFANTRY -> 12f
            UnitType.GRENADIER -> 35f // Deals area damage
            UnitType.SNIPER -> 45f
        }
    }

    private fun getUnitRange(type: UnitType): Float {
        return when (type) {
            UnitType.INFANTRY -> 180f
            UnitType.GRENADIER -> 130f
            UnitType.SNIPER -> 330f
        }
    }

    private fun getUnitSpeed(type: UnitType): Float {
        return when (type) {
            UnitType.INFANTRY -> 0.8f
            UnitType.GRENADIER -> 0.6f
            UnitType.SNIPER -> 0.5f
        }
    }

    private fun getUnitShootCooldown(type: UnitType): Int {
        return when (type) {
            UnitType.INFANTRY -> 80 // Frames
            UnitType.GRENADIER -> 150
            UnitType.SNIPER -> 180
        }
    }
}
