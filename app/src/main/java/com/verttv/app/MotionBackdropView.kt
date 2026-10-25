package com.verttv.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/** Local poster artwork with a slow pan. Never fetches trailers or consumes playback bandwidth. */
class MotionBackdropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val artwork = BitmapFactory.decodeResource(resources, R.drawable.cinema_wall)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shade = Paint()
    private var phase = 0f
    private val motion = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 36000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isFocusable = false }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && ValueAnimator.areAnimatorsEnabled()) {
            if (motion.isPaused) motion.resume() else if (!motion.isStarted) motion.start()
        } else if (motion.isStarted) motion.pause()
    }
    override fun onDetachedFromWindow() { motion.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = maxOf(width.toFloat() / artwork.width, height.toFloat() / artwork.height) * 1.16f
        val w = artwork.width * scale; val h = artwork.height * scale
        val x = -(w - width) * (0.15f + 0.7f * phase)
        val y = -(h - height) * (0.7f - 0.4f * phase)
        paint.shader = null
        canvas.drawBitmap(artwork, null, RectF(x, y, x + w, y + h), paint)
        shade.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
            intArrayOf(0xFA09080D.toInt(), 0xDE09080D.toInt(), 0x5509080D), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
        shade.shader = LinearGradient(0f, height * 0.6f, 0f, height.toFloat(), 0x0009080D, 0xE609080D.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
    }
}
