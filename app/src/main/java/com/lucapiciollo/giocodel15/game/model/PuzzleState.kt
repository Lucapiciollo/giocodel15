package com.lucapiciollo.giocodel15.game.model

data class PuzzleState(
    val size: Int,
    val tiles: List<Int>,
    val moves: Int = 0
) {
    init {
        require(size >= MIN_SIZE) { "Grid size must be at least $MIN_SIZE" }
        require(tiles.size == size * size) { "Tiles count must match grid size" }
        require(tiles.toSet().size == tiles.size) { "Tiles must be unique" }
        require(tiles.contains(EMPTY_TILE)) { "Puzzle must contain an empty tile" }
    }

    val emptyIndex: Int
        get() = tiles.indexOf(EMPTY_TILE)

    val isSolved: Boolean
        get() = tiles.dropLast(1).withIndex().all { (index, value) -> value == index + 1 } &&
            tiles.last() == EMPTY_TILE

    companion object {
        const val EMPTY_TILE = 0
        const val MIN_SIZE = 3

        fun solved(size: Int): PuzzleState {
            require(size >= MIN_SIZE)
            return PuzzleState(
                size = size,
                tiles = (1 until size * size).toList() + EMPTY_TILE
            )
        }
    }
}
