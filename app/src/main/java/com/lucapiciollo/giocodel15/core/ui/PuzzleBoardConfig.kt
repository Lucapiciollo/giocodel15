package com.lucapiciollo.giocodel15.core.ui

data class PuzzleBoardConfig(
    val tileGapDp: Float = 6f,
    val cornerRadiusDp: Float = 12f,
    val animationDurationMs: Long = 140L,
    val interactionEnabled: Boolean = true,
    val showNumbers: Boolean = true,
    val hapticFeedback: Boolean = true
)
