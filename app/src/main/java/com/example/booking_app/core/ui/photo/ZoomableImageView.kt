package com.example.booking_app.core.ui.photo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * Ảnh phóng to 1×–4× bằng hai ngón, kéo khi đang phóng, chạm hai lần để phóng/thu (spec 030 FR-004, research R8).
 * Khi đang phóng, view giữ thao tác vuốt (`requestDisallowInterceptTouchEvent`) để `ViewPager2` không chuyển ảnh.
 * Phép tính nằm ở [ZoomMath]. `contentDescription` do nơi dùng đặt.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var transform = ZoomMath.Transform.IDENTITY
    private val baseMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val contentRect = RectF()

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                apply(ZoomMath.zoomAround(transform, detector.scaleFactor, detector.focusX, detector.focusY))
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (transform.scale <= ZoomMath.MIN_SCALE) return false
                apply(transform.copy(tx = transform.tx - distanceX, ty = transform.ty - distanceY))
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val target = ZoomMath.nextDoubleTapScale(transform.scale)
                apply(ZoomMath.zoomAround(transform, target / transform.scale, e.x, e.y))
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean = performClick()
        },
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    val currentScale: Float get() = transform.scale

    fun resetZoom() {
        apply(ZoomMath.Transform.IDENTITY)
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        transform = ZoomMath.Transform.IDENTITY
        updateBaseMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBaseMatrix()
    }

    @SuppressLint("ClickableViewAccessibility") // performClick gọi trong onSingleTapConfirmed
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        val holdGesture = scaleDetector.isInProgress || event.pointerCount > 1 ||
            ZoomMath.canPanHorizontally(transform, width.toFloat(), contentRect.width())
        parent?.requestDisallowInterceptTouchEvent(holdGesture)
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun apply(next: ZoomMath.Transform) {
        transform = ZoomMath.clampTranslation(
            next,
            width.toFloat(),
            height.toFloat(),
            contentRect.width(),
            contentRect.height(),
        )
        drawMatrix.set(baseMatrix)
        drawMatrix.postScale(transform.scale, transform.scale)
        drawMatrix.postTranslate(transform.tx, transform.ty)
        imageMatrix = drawMatrix
    }

    /** Ma trận đặt ảnh vừa khung (fit center) ở 1×. */
    private fun updateBaseMatrix() {
        val d = drawable ?: return
        if (width == 0 || height == 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        val src = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        val dst = RectF(0f, 0f, width.toFloat(), height.toFloat())
        baseMatrix.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER)
        baseMatrix.mapRect(contentRect, src)
        apply(transform)
    }
}
