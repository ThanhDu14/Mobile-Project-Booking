package com.example.booking_app.core.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.google.android.material.color.MaterialColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bóng người nhỏ lặp động tác smash cầu lông cho màn chờ: nhún → bật nhảy → vung vợt → cầu cắm xuống.
 * Vẽ trên hệ toạ độ ảo 100x100, scale theo kích thước view.
 */
class SmashLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val figureColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val accentColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)

    private val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 4.2f
        color = figureColor
    }
    private val racketPaint = Paint(limbPaint).apply { strokeWidth = 1.6f }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = figureColor }
    private val shuttlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.4f
        color = accentColor
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }

    private val oval = RectF()
    private val feather = android.graphics.Path()

    /** Tiến độ vòng lặp 0..1. */
    private var t = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = LOOP_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    /** Vị trí đầu vợt lúc chạm cầu, tính từ chính tư thế để cầu luôn trúng vợt. */
    private val contact: PointF by lazy { racketHead(CONTACT_T) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width, height) / 100f
        canvas.save()
        canvas.translate((width - 100f * scale) / 2f, (height - 100f * scale) / 2f)
        canvas.scale(scale, scale)

        val jump = jump(t)
        val hip = hip(t)

        // Bóng dưới chân, co lại khi bật nhảy.
        val shadowW = 13f * (1f - jump / JUMP_MAX * 0.45f)
        shadowPaint.alpha = (40 * (1f - jump / JUMP_MAX * 0.5f)).toInt()
        oval.set(hip.x - shadowW, GROUND + 1f, hip.x + shadowW, GROUND + 4f)
        canvas.drawOval(oval, shadowPaint)

        // Chân.
        val air = jump / JUMP_MAX
        val footY = minOf(GROUND, hip.y + 29f)
        drawLeg(canvas, hip, PointF(hip.x + 5f + 2f * air, footY))
        drawLeg(canvas, hip, PointF(hip.x - 6f - 5f * air, footY))

        // Thân + đầu.
        val neck = neck(t)
        canvas.drawLine(hip.x, hip.y, neck.x, neck.y, limbPaint)
        canvas.drawCircle(neck.x + lean(t) * 0.3f, neck.y - 8f, 5.5f, fillPaint)

        // Tay trái (chỉ hướng cầu).
        val shoulder = shoulder(t)
        val offAngle = rad(keyframe(t, OFF_ARM_T, OFF_ARM_DEG))
        canvas.drawLine(
            shoulder.x, shoulder.y,
            shoulder.x + ARM * cos(offAngle), shoulder.y + ARM * sin(offAngle),
            limbPaint,
        )

        // Tay cầm vợt.
        val a = rad(racketAngle(t))
        val hand = PointF(shoulder.x + ARM * cos(a), shoulder.y + ARM * sin(a))
        canvas.drawLine(shoulder.x, shoulder.y, hand.x, hand.y, limbPaint)
        val headC = racketHead(t)
        canvas.drawLine(
            hand.x, hand.y,
            hand.x + HANDLE * cos(a), hand.y + HANDLE * sin(a),
            racketPaint,
        )
        canvas.save()
        canvas.rotate(Math.toDegrees(a.toDouble()).toFloat(), headC.x, headC.y)
        oval.set(headC.x - 6f, headC.y - 4f, headC.x + 6f, headC.y + 4f)
        canvas.drawOval(oval, racketPaint)
        canvas.restore()

        drawShuttle(canvas)
        drawImpact(canvas)

        canvas.restore()
    }

    private fun drawLeg(canvas: Canvas, hip: PointF, foot: PointF) {
        val dx = foot.x - hip.x
        val dy = foot.y - hip.y
        val d = hypot(dx, dy)
        val knee = if (d >= 2 * LEG) {
            PointF(hip.x + dx / 2, hip.y + dy / 2)
        } else {
            // Gập gối về phía trước (+x).
            val h = sqrt(LEG * LEG - d * d / 4)
            PointF(hip.x + dx / 2 + h * dy / d, hip.y + dy / 2 - h * dx / d)
        }
        canvas.drawLine(hip.x, hip.y, knee.x, knee.y, limbPaint)
        canvas.drawLine(knee.x, knee.y, foot.x, foot.y, limbPaint)
    }

    private fun drawShuttle(canvas: Canvas) {
        val start = PointF(contact.x + 42f, -6f)
        val end = PointF(104f, GROUND - 2f)
        val pos: PointF
        val dirX: Float
        val dirY: Float
        var alpha = 255
        when {
            t in SHUTTLE_IN_T..CONTACT_T -> {
                val p = (t - SHUTTLE_IN_T) / (CONTACT_T - SHUTTLE_IN_T)
                pos = lerp(start, contact, p)
                dirX = contact.x - start.x
                dirY = contact.y - start.y
            }
            t in CONTACT_T..SHUTTLE_OUT_T -> {
                val p = (t - CONTACT_T) / (SHUTTLE_OUT_T - CONTACT_T)
                pos = lerp(contact, end, p * p * 0.4f + p * 0.6f)
                dirX = end.x - contact.x
                dirY = end.y - contact.y
                alpha = (255 * (1f - p * p)).toInt()
                // Vệt tốc độ sau cú smash.
                val tail = lerp(contact, end, maxOf(0f, p - 0.35f))
                trailPaint.alpha = alpha / 3
                canvas.drawLine(tail.x, tail.y, pos.x, pos.y, trailPaint)
            }
            else -> return
        }
        val len = hypot(dirX, dirY)
        val ux = dirX / len
        val uy = dirY / len
        // Lông cầu xoè về phía ngược hướng bay.
        val bx = pos.x - ux * 6f
        val by = pos.y - uy * 6f
        feather.reset()
        feather.moveTo(pos.x, pos.y)
        feather.lineTo(bx - uy * 2.6f, by + ux * 2.6f)
        feather.lineTo(bx + uy * 2.6f, by - ux * 2.6f)
        feather.close()
        shuttlePaint.alpha = alpha / 2
        canvas.drawPath(feather, shuttlePaint)
        shuttlePaint.alpha = alpha
        canvas.drawCircle(pos.x, pos.y, 1.8f, shuttlePaint)
    }

    private fun drawImpact(canvas: Canvas) {
        if (t !in CONTACT_T..CONTACT_T + 0.1f) return
        val p = (t - CONTACT_T) / 0.1f
        trailPaint.alpha = (200 * (1f - p)).toInt()
        val r0 = 4f + 4f * p
        val r1 = r0 + 3f
        for (i in 0 until 6) {
            val ang = (i * 60f + 15f) * PI.toFloat() / 180f
            canvas.drawLine(
                contact.x + r0 * cos(ang), contact.y + r0 * sin(ang),
                contact.x + r1 * cos(ang), contact.y + r1 * sin(ang),
                trailPaint,
            )
        }
    }

    // --- Tư thế theo thời gian ---

    private fun jump(t: Float): Float =
        if (t in JUMP_START..JUMP_END) {
            JUMP_MAX * sin(PI.toFloat() * (t - JUMP_START) / (JUMP_END - JUMP_START))
        } else 0f

    private fun lean(t: Float) = keyframe(t, LEAN_T, LEAN_V)

    private fun hip(t: Float) =
        PointF(BODY_X, GROUND - 29f + 7f * keyframe(t, CROUCH_T, CROUCH_V) - jump(t))

    private fun neck(t: Float): PointF {
        val hip = hip(t)
        return PointF(hip.x + lean(t), hip.y - TORSO)
    }

    private fun shoulder(t: Float): PointF {
        val n = neck(t)
        return PointF(n.x, n.y + 2f)
    }

    private fun racketAngle(t: Float) = keyframe(t, RACKET_T, RACKET_DEG)

    private fun racketHead(t: Float): PointF {
        val s = shoulder(t)
        val a = rad(racketAngle(t))
        val r = ARM + HANDLE + 6f
        return PointF(s.x + r * cos(a), s.y + r * sin(a))
    }

    private fun keyframe(t: Float, times: FloatArray, values: FloatArray): Float {
        if (t <= times.first()) return values.first()
        for (i in 1 until times.size) {
            if (t <= times[i]) {
                val p = (t - times[i - 1]) / (times[i] - times[i - 1])
                val eased = p * p * (3 - 2 * p)
                return values[i - 1] + (values[i] - values[i - 1]) * eased
            }
        }
        return values.last()
    }

    private fun lerp(a: PointF, b: PointF, p: Float) = PointF(a.x + (b.x - a.x) * p, a.y + (b.y - a.y) * p)

    private fun rad(deg: Float) = deg * PI.toFloat() / 180f

    private companion object {
        const val LOOP_MS = 1500L
        const val GROUND = 92f
        const val BODY_X = 42f
        const val TORSO = 20f
        const val LEG = 15f
        const val ARM = 13f
        const val HANDLE = 4f
        const val JUMP_MAX = 10f
        const val JUMP_START = 0.15f
        const val JUMP_END = 0.75f
        const val CONTACT_T = 0.52f
        const val SHUTTLE_IN_T = 0.12f
        const val SHUTTLE_OUT_T = 0.72f

        // Góc theo toạ độ màn hình: 0° = phải, 90° = xuống, 270° = lên.
        val RACKET_T = floatArrayOf(0f, 0.15f, 0.42f, CONTACT_T, 0.62f, 1f)
        val RACKET_DEG = floatArrayOf(60f, 70f, 200f, 285f, 400f, 420f)
        val OFF_ARM_T = floatArrayOf(0f, 0.15f, 0.42f, 0.55f, 0.68f, 1f)
        val OFF_ARM_DEG = floatArrayOf(110f, 110f, -60f, -60f, 130f, 110f)
        val LEAN_T = floatArrayOf(0f, 0.15f, 0.42f, 0.56f, 0.8f, 1f)
        val LEAN_V = floatArrayOf(0f, 1f, -3f, 5f, 2f, 0f)
        val CROUCH_T = floatArrayOf(0f, 0.15f, 0.2f, 0.75f, 0.82f, 1f)
        val CROUCH_V = floatArrayOf(0f, 1f, 0f, 0f, 0.8f, 0f)
    }
}
