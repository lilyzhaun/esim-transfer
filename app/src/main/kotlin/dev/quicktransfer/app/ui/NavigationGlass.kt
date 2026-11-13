// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ui

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

/** The lens sees both the page and the already-painted navigation glyphs. */
internal class NavigationBackdrop(private val page: Backdrop, private val navigation: Backdrop) : Backdrop {
    override val isCoordinatesDependent = true
    override val offsetResidualX get() = navigation.offsetResidualX
    override val offsetResidualY get() = navigation.offsetResidualY
    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Int) {
        with(page) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(navigation) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

/** A rounded cylindrical lens, using an analytic edge normal and separate RGB samples. */
internal fun BackdropEffectScope.navigationLens(depth: Float, band: Float, dispersion: Float = 0f) {
    if (depth <= 0f) return
    padding = maxOf(padding, depth + 2f)
    val sampling = downscaleFactor.coerceAtLeast(1).toFloat()
    runtimeShaderEffect("NavigationRefraction", NAVIGATION_LENS, "image") {
        setFloatUniform("bounds", size.width / sampling, size.height / sampling)
        setFloatUniform("origin", padding / sampling, padding / sampling)
        setFloatUniform("depth", depth / sampling)
        setFloatUniform("band", band / sampling)
        setFloatUniform("dispersion", dispersion)
    }
}

private const val NAVIGATION_LENS = """
uniform shader image;
uniform float2 bounds;
uniform float2 origin;
uniform float depth;
uniform float band;
uniform float dispersion;
half4 main(float2 coordinate) {
    float2 p = coordinate - origin - bounds * 0.5;
    float radius = min(bounds.x, bounds.y) * 0.5;
    float2 spine = clamp(p, -max(bounds * 0.5 - radius, float2(0)), max(bounds * 0.5 - radius, float2(0)));
    float2 radial = p - spine;
    float radialLength = max(length(radial), 0.001);
    float inside = max(radius - radialLength, 0.0);
    float edge = 1.0 - smoothstep(0.0, max(band, 0.001), inside);
    float2 displacement = radial / radialLength * depth * edge * edge;
    half4 center = image.eval(coordinate - displacement);
    half red = image.eval(coordinate - displacement * (1.0 + dispersion)).r;
    half blue = image.eval(coordinate - displacement * (1.0 - dispersion)).b;
    return half4(red, center.g, blue, center.a);
}
"""
