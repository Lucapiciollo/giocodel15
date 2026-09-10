package com.lucapiciollo.giocodel15.multiplayer.model

/** Outcome of a single player within a round's final ranking. */
enum class RoundParticipantStatus {
    /** The player submitted a result validated by the host. */
    FINISHED,

    /** The round ended before the player finished, but the player stayed connected. */
    DNF,

    /** The player disconnected from the table before finishing the round. */
    DISCONNECTED
}
