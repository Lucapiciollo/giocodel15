package com.lucapiciollo.giocodel15.multiplayer.validation

import com.lucapiciollo.giocodel15.game.engine.PuzzleEngine
import com.lucapiciollo.giocodel15.game.engine.PuzzleGenerator
import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult

/**
 * Host-side gatekeeper for remote [PlayerResult]s. Never trust the client: this class is the
 * only place allowed to decide whether a reported result is accepted into the round ranking.
 * Pure Kotlin (no Android/Nearby dependency) so it can be unit tested in isolation.
 */
class PlayerResultValidator(
    private val minElapsedMs: Long = MIN_ELAPSED_MS,
    private val maxElapsedMs: Long = MAX_ELAPSED_MS
) {

    fun validate(result: PlayerResult, context: RoundValidationContext): ValidationResult {
        if (result.roundId != context.roundId) {
            return ValidationResult.Invalid("roundId non corrisponde alla manche in corso")
        }
        if (result.playerId !in context.knownPlayerIds) {
            return ValidationResult.Invalid("giocatore non appartenente alla partita")
        }
        if (result.playerId in context.alreadyAcceptedPlayerIds) {
            return ValidationResult.Invalid("risultato già ricevuto per questo giocatore")
        }
        if (result.moves <= 0) {
            return ValidationResult.Invalid("numero di mosse non valido")
        }
        if (result.elapsedMs !in minElapsedMs..maxElapsedMs) {
            return ValidationResult.Invalid("elapsedMs non plausibile")
        }
        if (result.finishedAt < context.startAtMs) {
            return ValidationResult.Invalid("finish precedente allo start della manche")
        }

        val expectedBoardHash = PuzzleEngine.boardHash(
            PuzzleGenerator.generate(context.gridSize, context.seed)
        )
        if (result.boardHash != expectedBoardHash) {
            return ValidationResult.Invalid("board non coerente con seed/gridSize del round")
        }

        if (result.moveSequence.isNotEmpty()) {
            val replayed = PuzzleEngine.replay(context.gridSize, context.seed, result.moveSequence)
            if (!replayed.isSolved) {
                return ValidationResult.Invalid("la sequenza di mosse non porta alla soluzione")
            }
        }

        return ValidationResult.Valid
    }

    companion object {
        private const val MIN_ELAPSED_MS = 200L
        private const val MAX_ELAPSED_MS = 30 * 60 * 1000L
    }
}
