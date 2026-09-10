package com.lucapiciollo.giocodel15.multiplayer.session

/**
 * Shared lifecycle of a multiplayer round, mirrored on every connected device so Activities
 * don't need to juggle their own ad-hoc booleans/flags.
 */
enum class RoundState {
    /** Lobby is open, players are joining, the host has not pressed START yet. */
    WAITING,

    /** START_GAME/NEW_ROUND received, waiting for the synchronized `startAt` instant. */
    COUNTDOWN,

    /** The round is running: board interaction and timers are active. */
    PLAYING,

    /** The local player finished the puzzle and is waiting for the host to close the round. */
    FINISHED,

    /** The host validated/closed the round and the ranking screen is shown. */
    RESULTS,

    /** The table/session has been torn down (host closed it, or a player left for good). */
    CLOSED
}
