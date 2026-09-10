package com.lucapiciollo.giocodel15.multiplayer.validation

/** Outcome of validating a remote [com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult]. */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}
