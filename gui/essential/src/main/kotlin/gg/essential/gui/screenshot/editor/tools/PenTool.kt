/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
package gg.essential.gui.screenshot.editor.tools

import dev.folomeev.kotgl.matrix.vectors.Vec2
import dev.folomeev.kotgl.matrix.vectors.dot
import dev.folomeev.kotgl.matrix.vectors.length
import dev.folomeev.kotgl.matrix.vectors.mutables.minus
import dev.folomeev.kotgl.matrix.vectors.mutables.normalize
import dev.folomeev.kotgl.matrix.vectors.mutables.plus
import dev.folomeev.kotgl.matrix.vectors.mutables.times
import dev.folomeev.kotgl.matrix.vectors.vec2
import gg.essential.elementa.renderer.SpecialRenderer
import gg.essential.gui.screenshot.editor.ScreenshotCanvas
import gg.essential.gui.screenshot.editor.change.EditHistory
import gg.essential.gui.screenshot.editor.change.VectorStroke
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gg.essential.universal.render.UGpuBuffer
import gg.essential.universal.render.UGpuTextureView
import gg.essential.universal.render.URenderPassDescriptor
import gg.essential.universal.shader.BlendState
import gg.essential.universal.vertex.UVertexConsumer
import gg.essential.util.GuiEssentialPlatform.Companion.platform
import org.intellij.lang.annotations.Language
import java.awt.Color
import kotlin.math.abs
import kotlin.use

/**
 * This tool works by storing points of where the cursor has been dragged on the canvas
 * then drawing a line between those points.
 */
class PenTool(private val editHistory: EditHistory, editableScreenshot: ScreenshotCanvas) : Tool(editableScreenshot) {
    var color: Color = Color.WHITE
    var width: Float = 1f

    // stores the last x position called from the mouseDrag event
    var previousMouseX = -1f

    // stores the last y position called from the mouseDrag event
    var previousMouseY = -1f

    // Stores the stroke that is currently being drawn
    var currentVectorStroke: PenVectorStroke? = null

    override fun enable() {
        editableScreenshot.onDraw = { mouseX, mouseY, mouseButton ->
            val relativeX = (mouseX - getLeft()).coerceIn(0f, getWidth())
            val relativeY = (mouseY - getTop()).coerceIn(0f, getHeight())
            // makes sure its inside the bounds of [uiImage]
            if (!(relativeX < 0 || relativeY < 0
                    || relativeX > editableScreenshot.getWidth() || relativeY > editableScreenshot.getHeight())
            ) {
                // left click is mouse button 0
                val LEFT_CLICK = 0
                if (mouseButton == LEFT_CLICK) {
                    // every frame during drag get previousMouse and draw a line from currentMouse
                    // if previous mouse is -1 then it is the first point therefore nothing is drawn
                    if (previousMouseX != -1f || previousMouseY != -1f) {
                        if (relativeX != previousMouseX || relativeY != previousMouseY) {
                            // add a line to draw from the previous mouse pointer to the current
                            // this mimics the function of a "pen"
                            currentVectorStroke?.list?.add(relativeX / getWidth() to relativeY / getHeight())
                        }
                    }
                    // set previous mouse pointers to the current mouse point
                    previousMouseX = relativeX
                    previousMouseY = relativeY
                }
            }
        }
        editableScreenshot.screenshotDisplay.onMouseClick {
            if (it.mouseButton != 0) {
                return@onMouseClick
            }
            currentVectorStroke = PenVectorStroke(color, width).also { editHistory.pushChange(it) }
        }
        editableScreenshot.screenshotDisplay.onMouseRelease {
            previousMouseX = -1f
            previousMouseY = -1f
            currentVectorStroke = null
        }
    }

    /**
     * impl of [VectorStroke]
     * handles drawing lines as quadratic beziers with anti-aliasing (by using SDFs)
     */
    inner class PenVectorStroke(val colorObj: Color, val strokeWidth: Float) :
        VectorStroke(editableScreenshot, colorObj.rgb) {
        val list = mutableListOf<Pair<Float, Float>>()
    }

    class RenderState(
        val scale: Float,
        val strokes: List<Stroke>,
    ) {
        class Stroke(
            val points: List<Vec2>,
            val strokeWidth: Float,
            val color: Color,
        )

        companion object {
            fun create(
                guiScale: Float,
                imageWidth: Float,
                imageHeight: Float,
                scale: Float,
                strokes: List<PenVectorStroke>,
            ): RenderState {
                return RenderState(
                    scale,
                    strokes.mapNotNull { stroke ->
                        if (stroke.list.size < 2) return@mapNotNull null

                        Stroke(
                            // Convert list to Vec2 points in screen space (0/0 is top left; unit is real pixels)
                            stroke.list.map { (x, y) -> vec2(x * imageWidth * guiScale, y * imageHeight * guiScale) },
                            stroke.strokeWidth,
                            stroke.colorObj,
                        )
                    }
                )
            }
        }
    }

    object RendererFactory : SpecialRenderer.Factory<RenderState> {
        override fun create(): SpecialRenderer<RenderState> =
            Renderer()
    }

    private class Renderer : SpecialRenderer<RenderState> {
        override val supportsScissor: Boolean
            get() = false // we could, but there'll only ever be one of these anyway, so doesn't really matter
        override val onlyDrawsInBounds: Boolean
            get() = false

        override fun close() {
        }

        override fun render(destination: UGpuTextureView, instances: List<SpecialRenderer.Instance<RenderState>>) {
            val device = UGraphics.getDevice()

            device.createRenderPass(
                URenderPassDescriptor { "PenTool" }
                    .withColorAttachment(destination)
            ).use { renderPass ->
                renderPass.pipeline(PIPELINE)

                for (instance in instances) {
                    val w = destination.texture.width
                    val h = destination.texture.height
                    val x = instance.dstX
                    val y = instance.dstY
                    renderPass.projectionMatrix(floatArrayOf(
                        2f/w, 0f,    0f,   0f,
                        0f,   -2f/h, 0f,   0f,
                        0f,   0f,    1f,   0f,
                        -1f + x * 2f/w,  1f - y * 2f/h,    0f,   1f,
                    ))

                    val state = instance.args
                    for (stroke in state.strokes) {
                        val builder = platform.newPenToolBufferBuilder(UGraphics.DrawMode.TRIANGLES)
                        val vertexCount = render(builder, state, stroke)
                        builder.build()!!.use { device.createBuffer(UGpuBuffer.Usage.VERTEX, it.toByteBuffer()) }.use { vertexBuffer ->
                            renderPass.uniform("uThickness", stroke.strokeWidth * state.scale)
                            renderPass.vertexBuffer(0, vertexBuffer.slice())
                            renderPass.draw(vertexCount)
                        }
                    }
                }
            }
        }

        private fun render(
            builder: UVertexConsumer,
            state: RenderState,
            strokeState: RenderState.Stroke,
        ): Int {
            fun Vec2.rotate90CW() = vec2(-y, x)
            fun Vec2.rotate90CCW() = vec2(y, -x)

            /** A line defined by a point and a (unit) direction. */
            data class LinePD(val point: Vec2, val direction: Vec2) {
                val normal: Vec2
                    get() = direction.rotate90CW()

                fun intersect(other: LinePD): Vec2? {
                    val dot = direction.dot(other.normal)
                    if (abs(dot) < 0.001) {
                        return null // lines are parallel
                    }
                    return point.plus(direction.times(other.point.minus(point).dot(other.normal) / dot))
                }
            }

            val points = strokeState.points
            val strokeWidth = strokeState.strokeWidth
            val colorObj = strokeState.color
            val scale = state.scale

            // To smooth out the pen strokes, instead of just drawing a linear line between each segment (pair of
            // points), we'll draw a quadratic bezier curve.
            // A quadratic bezier curve is defined by three points: The start point (which we have), the end point
            // (which we also have), and a single control point (which we need to come up with).
            //
            // There isn't really a "right" way to decide on the control point.
            // There is however a single constraint which we need our control points to fulfill: We want our overall
            // spline to be G1 continuous. Meaning, for each point we want the incoming curve to have the same direction
            // as the outgoing curve at that point, so we have no sudden changes in direction (i.e. the line looks
            // smooth).
            // This is the case for a quadratic bezier curve spline when the control point of the previous curve, the
            // point in question, and the control point of the next curve all lie on a line.
            // As such, for each control point (except the first one), we have exactly one degree of freedom:
            // It needs to be somewhere on the line defined by its start point and the previous control point, and we
            // can chose where to put it.
            //
            // Given this is just a simple drawing program, we'll go with a relatively simple (and more art than
            // science) algorithm, which:
            // - Sets the very first control point to be in the middle of the first two points. This does mean the first
            // segment will always just degenerate to simple straight line.
            // - To decide how far away from the starting point to place the next control points, we take the average
            //  of the length of the current and the prior segment, and scale that by a magic factor. There's no
            //  particular logic behind that scale factor, any value greater 0 and less than 0.5 makes sense. The value
            //  I've chosen just gives a curvature which looks decent enough to me.
            val segments = 0 until points.size - 1
            var prevControlPoint = vec2() // initialized on first iteration
            val controls = segments.map { i ->
                val controlPoint = if (i == 0) {
                    points[0].plus(points[1]).times(0.5f)
                } else {
                    val p = points[i]
                    val prevP = points[i - 1]
                    val nextP = points[i + 1]

                    val prevSegmentLen = p.minus(prevP).length()
                    val currSegmentLen = nextP.minus(p).length()
                    val averageLength = (prevSegmentLen + currSegmentLen) * 0.5f
                    val distanceFromP = averageLength * 0.35f // aforementioned magic factor

                    val dir = p.minus(prevControlPoint).normalize()
                    p.plus(dir.times(distanceFromP))
                }

                controlPoint.also { prevControlPoint = it }
            }

            // Bezier curves by themselves are one dimensional: They have a length, but zero width. The curves we draw
            // should have some thickness to them though (specifically [strokeWidth]).
            // This variable contains the distance from the 1D curve to the edges of the drawn curve in pixels.
            // Note: We need to expand the curve we draw with the triangles ever so slightly, so the fragment shader
            //       will run for all pixels of the curve (even those that are only partially covered), so we can do
            //       anti-aliasing.
            //       Technically just one (or maybe even a half) extra real pixel should be sufficient but I don't want
            //       to have to carefully think through it all (MC scaling, vertex data precision, potential
            //       multi-sampling, etc.), so we'll just expand the poligon by 2 full pixels, which should be plenty,
            //       and call it a day.
            // Note: We don't multiply this one by [guiScale] because the resizing the window shouldn't change the size
            //       of the lines (relative to the image).
            //       FIXME we should probably store the [strokeWidth] as a value relative to full image width/height
            //             then, otherwise if you increase the size of the image, the strokes will appear smaller in
            //             comparison
            val scaledStrokeWidth = strokeWidth * scale
            val edgeOffset = scaledStrokeWidth * 0.5f + 2

            // So for each point, we compute two new points which are [strokeWidth]/2 from the point, on the edges of
            // that drawn curve.
            // These will be used later to construct a polygon which encompasses the curves of the previous and next
            // segments. Computed here so we don't have to compute them twice, once for prior and once for next segment.
            val edgePoints = points.mapIndexed { i, point ->
                // As per definition of bezier curves, the tangent of the curve at one of our points is exactly the same
                // as the line from the prior (or next) control point to our point.
                val dir = if (i > 0) {
                    point.minus(controls[i - 1]).normalize()
                } else {
                    // For point 0, we use the next control point, because it doesn't have prior one.
                    // Note: Also need to correspondingly flip the `minus`, because we rely on consistent direction in
                    //       our polygon assembly code.
                    controls[0].minus(point).normalize()
                }
                // We can then scale that direction by the thickness of the curve we want to draw and rotate it 90
                // degrees CW and CWW from our point, to get the two points on the outline.
                val scaledDir = dir.times(edgeOffset)
                val p1 = point.plus(scaledDir.rotate90CCW())
                val p2 = point.plus(scaledDir.rotate90CW())
                Pair(p1, p2)
            }

            var vertexCount = 0
            for (i in segments) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val c = controls[i]

                fun vertex(pos: Vec2) {
                    builder.pos(UMatrixStack.UNIT, pos.x.toDouble(), pos.y.toDouble(), 0.0)
                    builder.color(colorObj)
                    // Note: tex uses float32 while overlay and light use int16 component types.
                    //       To avoid the same point having different precision for different curves (the p2 of this
                    //       segment becomes the p1 of the next segment), we always pass p1 and p2 via the int16 types.
                    //       The float type is then used for the control point, which is only used by this segment
                    builder.tex(c.x.toDouble(), c.y.toDouble())
                    builder.overlay(p1.x.toInt(), p1.y.toInt())
                    builder.light(p2.x.toInt(), p2.y.toInt())
                    builder.endVertex()
                    vertexCount++
                }
                fun tri(a: Vec2, b: Vec2, c: Vec2) {
                    vertex(a)
                    vertex(b)
                    vertex(c)
                }

                // Traveling along the line:
                // r* points lie on the right edge of the line, l* points on the left
                // *1 points are on the start point of the line segment, *2 points on the end point of that segment
                val (l1, r1) = edgePoints[i]
                val (l2, r2) = edgePoints[i + 1]

                // Determine the side of the line segment on which the control point lies
                val controlOnRight = p2.minus(p1).rotate90CW().dot(c.minus(p1)) > 0

                // We'll roughly want to form a triangle between the start, control, and end points.
                // However we need to grow that triangle a bit to fit the actual stroke (which, unlike the mathematical
                // 1D curve, also has a width). For the start and end points, this is already taken care of by
                // `outlinePoints`.
                // For the control point, we'll need to find the point where the two (start and end) control-point-side
                // edges of the outline meet:
                val edgeLine1 = LinePD(if (controlOnRight) r1 else l1, c.minus(p1).normalize())
                val edgeLine2 = LinePD(if (controlOnRight) r2 else l2, p2.minus(c).normalize())
                val co = edgeLine1.intersect(edgeLine2) ?: (if (controlOnRight) r1 else l1)

                // Emit three triangles which form a 5-gon which covers this segment of bezier curve.
                // Note: The way the 5-gon has been split into triangles is quite important, otherwise extreme cases
                //       may render incorrectly.
                //       E.g. https://i.johni0702.de/pgLmHh6N.png (curve goes from bottom right to bottom left; yellow
                //       lines are the actual triangles emitted; red line and arrow is for the explanation below)
                //       If one were to instead use a solution which e.g. form a quad with l1, r1, r2, l2, the
                //       connection between r1 and r2 (the red line) would result in a triangle that would draw over the
                //       previous segment (where the red arrow is pointing).
                tri(l1, r1, co)
                if (controlOnRight) {
                    tri(l2, l1, co)
                } else {
                    tri(r1, r2, co)
                }
                tri(r2, l2, co)

                // For the very first/last segment, we need to draw an extra quad at the start/end, so the curve can
                // end in a nice half-circle instead of cutting off abruptly.
                if (i == 0) {
                    val l0 = l1.minus(edgeLine1.direction.times(edgeOffset))
                    val r0 = r1.minus(edgeLine1.direction.times(edgeOffset))
                    tri(l1, r1, r0)
                    tri(r0, l0, l1)
                } else if (i == segments.last) {
                    val l3 = l2.plus(edgeLine2.direction.times(edgeOffset))
                    val r3 = r2.plus(edgeLine2.direction.times(edgeOffset))
                    tri(l2, r2, r3)
                    tri(r3, l3, l2)
                }
            }
            return vertexCount
        }
    }

    companion object {
        @Language("GLSL")
        private val vertSource = """
            varying vec4 vColor;
            varying vec2 vPos;
            varying vec2 vP1;
            varying vec2 vP2;
            varying vec2 vC;
            
            void main() {
                gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex;
                vColor = gl_Color;
                vPos = gl_Vertex.xy;
                vC = gl_MultiTexCoord0.st;
                vP1 = vec2(gl_MultiTexCoord1.st);
                vP2 = vec2(gl_MultiTexCoord2.st);
            }
        """.trimIndent()

        @Language("GLSL")
        private val fragSource = """
            varying vec2 vPos;
            varying vec2 vP1;
            varying vec2 vP2;
            varying vec2 vC;
            varying vec4 vColor;
            
            uniform float uThickness;
            
            float sdBezier( in vec2 pos, in vec2 A, in vec2 B, in vec2 C );
            
            void main() {
                float dist = abs(sdBezier(vPos, vP1, vC, vP2));
                dist -= uThickness / 2.0;
                gl_FragColor = vColor * vec4(1.0, 1.0, 1.0, clamp(1.0 - dist, 0.0, 1.0));
            }
            
            // sdBezier and helpers from https://www.shadertoy.com/view/MlKcDD with dead code, comments, and `outQ` removed
            // also added a small epsilon to the computation of `kk` to prevent degeneracy for straight lines, as suggested by the comments
            // The MIT License
            // Copyright © 2018 Inigo Quilez
            // Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions: The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
            float dot2( vec2 v ) { return dot(v,v); }
            float cro( vec2 a, vec2 b ) { return a.x*b.y-a.y*b.x; }
            float cos_acos_3( float x ) { x=sqrt(0.5+0.5*x); return x*(x*(x*(x*-0.008972+0.039071)-0.107074)+0.576975)+0.5; } // https://www.shadertoy.com/view/WltSD7
            float sdBezier( in vec2 pos, in vec2 A, in vec2 B, in vec2 C )
            {    
                vec2 a = B - A;
                vec2 b = A - 2.0*B + C;
                vec2 c = a * 2.0;
                vec2 d = A - pos;

                float kk = 1.0/(dot(b,b)+(1e-3));
                float kx = kk * dot(a,b);
                float ky = kk * (2.0*dot(a,a)+dot(d,b))/3.0;
                float kz = kk * dot(d,a);      

                float res = 0.0;
                float sgn = 0.0;

                float p  = ky - kx*kx;
                float q  = kx*(2.0*kx*kx - 3.0*ky) + kz;
                float p3 = p*p*p;
                float q2 = q*q;
                float h  = q2 + 4.0*p3;

                if( h>=0.0 ) 
                {
                    h = sqrt(h);
                    
                    h = (q<0.0) ? h : -h;
                    float x = (h-q)/2.0;
                    float v = sign(x)*pow(abs(x),1.0/3.0);
                    float t = v - p/v;

                    t -= (t*(t*t+3.0*p)+q)/(3.0*t*t+3.0*p);
                    
                    t = clamp( t-kx, 0.0, 1.0 );
                    vec2  w = d+(c+b*t)*t;
                    res = dot2(w);
                	sgn = cro(c+2.0*b*t,w);
                }
                else 
                {
                    float z = sqrt(-p);
                    float m = cos_acos_3( q/(p*z*2.0) );
                    float n = sqrt(1.0-m*m);
                    n *= sqrt(3.0);
                    vec3  t = clamp( vec3(m+m,-n-m,n-m)*z-kx, 0.0, 1.0 );
                    vec2  qx=d+(c+b*t.x)*t.x; float dx=dot2(qx), sx=cro(a+b*t.x,qx);
                    vec2  qy=d+(c+b*t.y)*t.y; float dy=dot2(qy), sy=cro(a+b*t.y,qy);
                    if( dx<dy ) {res=dx;sgn=sx;} else {res=dy;sgn=sy;}
                }
                
                return sqrt( res )*sign(sgn);
            }
        """.trimIndent()

        private val PIPELINE = platform.newPenToolRenderPipelineBuilder(
            "essential:screenshot/pen",
            UGraphics.DrawMode.TRIANGLES,
            vertSource,
            fragSource,
        ).apply {
            blendState = BlendState.ALPHA
        }.build()
    }
}