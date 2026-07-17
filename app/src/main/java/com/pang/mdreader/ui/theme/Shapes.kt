package com.pang.mdreader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Custom shapes for MD Reader.
 * Adds deliberate contrast between rounded and sharp forms,
 * moving away from uniform rounded rectangles on every element.
 *
 * - Small components use a generous pill shape (20dp)
 * - Medium components use a soft rounded shape (12dp)
 * - Large surfaces use a gentle curve (16dp)
 * - The reading area itself is left unshaped (full width)
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),      // Chips, badges
    small = RoundedCornerShape(12.dp),          // Cards, buttons
    medium = RoundedCornerShape(16.dp),         // Dialogs, bottom sheets
    large = RoundedCornerShape(20.dp),          // Modal surfaces
    extraLarge = RoundedCornerShape(24.dp),     // Full-screen modals
)
