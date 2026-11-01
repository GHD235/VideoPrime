package org.schabi.newpipe.videoprime

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp

/**
 * PulsePlay: a white ring around what has the focus of the TV remote (or a keyboard). Put it
 * before the clickable it belongs to. With the fingers nothing ever takes the focus, so on the
 * phone it never shows.
 *
 * A modifier node, not `composed`: it is on every card of every list, and a `composed` modifier
 * cost a composition (and a state) per card and stopped the lists from skipping unchanged cards.
 * A change of focus only redraws the card.
 */
fun Modifier.focusRing(shape: Shape): Modifier = this then FocusRingElement(shape)

private data class FocusRingElement(val shape: Shape) : ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(shape)

    override fun update(node: FocusRingNode) {
        if (node.shape != shape) {
            node.shape = shape
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusRing"
    }
}

private class FocusRingNode(var shape: Shape) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {
    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focused != focusState.hasFocus) {
            focused = focusState.hasFocus
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        val width = RING_WIDTH.toPx()
        // inside the bounds, like Modifier.border
        val inner = Size(size.width - width, size.height - width)
        if (inner.width <= 0f || inner.height <= 0f) return
        val outline: Outline = shape.createOutline(inner, layoutDirection, this)
        translate(width / 2, width / 2) {
            drawOutline(outline, Color.White, style = Stroke(width))
        }
    }

    private companion object {
        val RING_WIDTH = 3.dp
    }
}
