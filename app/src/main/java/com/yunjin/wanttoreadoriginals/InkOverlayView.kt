package com.yunjin.wanttoreadoriginals

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class InkOverlayView(context: Context) : View(context) {
    enum class Tool { PEN, HIGHLIGHTER, ERASER }
    data class Stroke(
        val tool: Tool,
        val color: Int,
        val width: Float,
        val alpha: Int,
        val points: MutableList<PointF> = mutableListOf()
    )

    var tool: Tool = Tool.PEN
    var color: Int = Color.BLACK
    var strokeWidthPx: Float = 6f
    var onTwoFingerDoubleTap: (() -> Unit)? = null

    private val strokes = mutableListOf<Stroke>()
    private var current: Stroke? = null
    private var lastTwoFingerTap = 0L

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.lastIndex)
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 2 && event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            val now = System.currentTimeMillis()
            if (now - lastTwoFingerTap < 330) onTwoFingerDoubleTap?.invoke()
            lastTwoFingerTap = now
            return true
        }
        if (event.pointerCount > 1) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val alpha = if (tool == Tool.HIGHLIGHTER) 70 else 255
                current = Stroke(tool, color, strokeWidthPx, alpha).also {
                    it.points.add(PointF(event.x, event.y)); strokes.add(it)
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (tool == Tool.ERASER) eraseNear(event.x, event.y)
                else current?.points?.add(PointF(event.x, event.y))
                invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current = null
                invalidate(); return true
            }
        }
        return true
    }

    private fun eraseNear(x: Float, y: Float) {
        val hit = strokeWidthPx * 2.2f
        strokes.removeAll { s -> s.points.any { p -> abs(p.x - x) < hit && abs(p.y - y) < hit } }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { drawStroke(canvas, it) }
    }

    private fun drawStroke(canvas: Canvas, s: Stroke) {
        if (s.points.size < 2) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = s.color
            alpha = s.alpha
            strokeWidth = s.width
        }
        val path = Path().apply { moveTo(s.points[0].x, s.points[0].y) }
        for (i in 1 until s.points.size) path.lineTo(s.points[i].x, s.points[i].y)
        canvas.drawPath(path, p)
    }

    fun toJson(): String {
        val arr = JSONArray()
        strokes.forEach { s ->
            arr.put(JSONObject().apply {
                put("tool", s.tool.name); put("color", s.color); put("width", s.width); put("alpha", s.alpha)
                put("points", JSONArray().also { pts -> s.points.forEach { pts.put(JSONArray().put(it.x).put(it.y)) } })
            })
        }
        return arr.toString()
    }

    fun loadJson(text: String) {
        strokes.clear()
        if (text.isBlank()) { invalidate(); return }
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val s = Stroke(
                Tool.valueOf(o.getString("tool")),
                o.getInt("color"),
                o.getDouble("width").toFloat(),
                o.getInt("alpha")
            )
            val pts = o.getJSONArray("points")
            for (j in 0 until pts.length()) {
                val p = pts.getJSONArray(j)
                s.points.add(PointF(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()))
            }
            strokes.add(s)
        }
        invalidate()
    }
}
