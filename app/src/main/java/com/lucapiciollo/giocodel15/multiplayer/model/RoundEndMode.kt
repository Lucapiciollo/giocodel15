package com.lucapiciollo.giocodel15.multiplayer.model

/**
 * Configurable rule that decides when a round is considered over.
 */
enum class RoundEndMode {
    /** The round ends as soon as the first valid result is accepted. */
    SPRINT,

    /** The round ends when the first 3 valid results arrive (or all players if fewer than 3). */
    PODIUM,

    /** The round ends only when every connected player has finished or been marked DNF. */
    FULL_RANKING
}
