package com.haozhang.lib

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import com.v2ray.ang.R

/**
 * @author HaoZhang
 */
class SlantedTextView : View {

    private val mPaint = Paint()
    private val mTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var mSlantedLength = 40f
    private var mTextSize = 16f
    private var mSlantedBackgroundColor = Color.TRANSPARENT
    private var mTextColor = Color.WHITE
    private var mSlantedText = ""
    
    var mode = MODE_LEFT
        private set

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(attrs)
    }

    fun init(attrs: AttributeSet?) {
        if (attrs != null) {
            val array = context.obtainStyledAttributes(attrs, R.styleable.SlantedTextView)

            mTextSize = array.getDimension(R.styleable.SlantedTextView_slantedTextSize, mTextSize)
            mTextColor = array.getColor(R.styleable.SlantedTextView_slantedTextColor, mTextColor)
            mSlantedLength = array.getDimension(R.styleable.SlantedTextView_slantedLength, mSlantedLength)
            mSlantedBackgroundColor = array.getColor(R.styleable.SlantedTextView_slantedBackgroundColor, mSlantedBackgroundColor)

            if (array.hasValue(R.styleable.SlantedTextView_slantedText)) {
                mSlantedText = array.getString(R.styleable.SlantedTextView_slantedText) ?: ""
            }

            if (array.hasValue(R.styleable.SlantedTextView_slantedMode)) {
                mode = array.getInt(R.styleable.SlantedTextView_slantedMode, 0)
            }
            array.recycle()
        }

        mPaint.apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            isAntiAlias = true
            color = mSlantedBackgroundColor
        }

        mTextPaint.apply {
            isAntiAlias = true
            textSize = mTextSize
            color = mTextColor
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        drawText(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        var path = Path()
        val w = width.toFloat()
        val h = height.toFloat()

        if (w != h) throw IllegalStateException("SlantedTextView's width must equal to height")

        path = when (mode) {
            MODE_LEFT -> getModeLeftPath(path, w, h)
            MODE_RIGHT -> getModeRightPath(path, w, h)
            MODE_LEFT_BOTTOM -> getModeLeftBottomPath(path, w, h)
            MODE_RIGHT_BOTTOM -> getModeRightBottomPath(path, w, h)
            MODE_LEFT_TRIANGLE -> getModeLeftTrianglePath(path, w, h)
            MODE_RIGHT_TRIANGLE -> getModeRightTrianglePath(path, w, h)
            MODE_LEFT_BOTTOM_TRIANGLE -> getModeLeftBottomTrianglePath(path, w, h)
            MODE_RIGHT_BOTTOM_TRIANGLE -> getModeRightBottomTrianglePath(path, w, h)
            else -> path
        }
        path.close()
        canvas.drawPath(path, mPaint)
        canvas.save()
    }

    private fun getModeLeftPath(path: Path, w: Float, h: Float): Path {
        path.moveTo(w, 0f)
        path.lineTo(0f, h)
        path.lineTo(0f, h - mSlantedLength)
        path.lineTo(w - mSlantedLength, 0f)
        return path
    }

    private fun getModeRightPath(path: Path, w: Float, h: Float): Path {
        path.lineTo(w, h)
        path.lineTo(w, h - mSlantedLength)
        path.lineTo(mSlantedLength, 0f)
        return path
    }

    private fun getModeLeftBottomPath(path: Path, w: Float, h: Float): Path {
        path.lineTo(w, h)
        path.lineTo(w - mSlantedLength, h)
        path.lineTo(0f, mSlantedLength)
        return path
    }

    private fun getModeRightBottomPath(path: Path, w: Float, h: Float): Path {
        path.moveTo(0f, h)
        path.lineTo(mSlantedLength, h)
        path.lineTo(w, mSlantedLength)
        path.lineTo(w, 0f)
        return path
    }

    private fun getModeLeftTrianglePath(path: Path, w: Float, h: Float): Path {
        path.lineTo(0f, h)
        path.lineTo(w, 0f)
        return path
    }

    private fun getModeRightTrianglePath(path: Path, w: Float, h: Float): Path {
        path.lineTo(w, 0f)
        path.lineTo(w, h)
        return path
    }

    private fun getModeLeftBottomTrianglePath(path: Path, w: Float, h: Float): Path {
        path.lineTo(w, h)
        path.lineTo(0f, h)
        return path
    }

    private fun getModeRightBottomTrianglePath(path: Path, w: Float, h: Float): Path {
        path.moveTo(0f, h)
        path.lineTo(w, h)
        path.lineTo(w, 0f)
        return path
    }

    private fun drawText(canvas: Canvas) {
        val w = (width - mSlantedLength / 2).toInt()
        val h = (height - mSlantedLength / 2).toInt()
        val xy = calculateXY(canvas, w, h)
        
        val toX = xy[0]
        val toY = xy[1]
        val centerX = xy[2]
        val centerY = xy[3]
        val angle = xy[4]

        canvas.rotate(angle, centerX, centerY)
        canvas.drawText(mSlantedText, toX, toY, mTextPaint)
    }

    private fun calculateXY(canvas: Canvas, w: Int, h: Int): FloatArray {
        val xy = FloatArray(5)
        val offset = (mSlantedLength / 2).toInt()
        
        when (mode) {
            MODE_LEFT_TRIANGLE, MODE_LEFT -> {
                val rect = Rect(0, 0, w, h)
                val rectF = RectF(rect)
                rectF.right = mTextPaint.measureText(mSlantedText, 0, mSlantedText.length)
                rectF.bottom = mTextPaint.descent() - mTextPaint.ascent()
                rectF.left += (rect.width() - rectF.right) / 2.0f
                rectF.top += (rect.height() - rectF.bottom) / 2.0f
                xy[0] = rectF.left
                xy[1] = rectF.top - mTextPaint.ascent()
                xy[2] = w / 2f
                xy[3] = h / 2f
                xy[4] = -ROTATE_ANGLE.toFloat()
            }
            MODE_RIGHT_TRIANGLE, MODE_RIGHT -> {
                val rect = Rect(offset, 0, w + offset, h)
                val rectF = RectF(rect)
                rectF.right = mTextPaint.measureText(mSlantedText, 0, mSlantedText.length)
                rectF.bottom = mTextPaint.descent() - mTextPaint.ascent()
                rectF.left += (rect.width() - rectF.right) / 2.0f
                rectF.top += (rect.height() - rectF.bottom) / 2.0f
                xy[0] = rectF.left
                xy[1] = rectF.top - mTextPaint.ascent()
                xy[2] = w / 2f + offset
                xy[3] = h / 2f
                xy[4] = ROTATE_ANGLE.toFloat()
            }
            MODE_LEFT_BOTTOM_TRIANGLE, MODE_LEFT_BOTTOM -> {
                val rect = Rect(0, offset, w, h + offset)
                val rectF = RectF(rect)
                rectF.right = mTextPaint.measureText(mSlantedText, 0, mSlantedText.length)
                rectF.bottom = mTextPaint.descent() - mTextPaint.ascent()
                rectF.left += (rect.width() - rectF.right) / 2.0f
                rectF.top += (rect.height() - rectF.bottom) / 2.0f
                xy[0] = rectF.left
                xy[1] = rectF.top - mTextPaint.ascent()
                xy[2] = w / 2f
                xy[3] = h / 2f + offset
                xy[4] = ROTATE_ANGLE.toFloat()
            }
            MODE_RIGHT_BOTTOM_TRIANGLE, MODE_RIGHT_BOTTOM -> {
                val rect = Rect(offset, offset, w + offset, h + offset)
                val rectF = RectF(rect)
                rectF.right = mTextPaint.measureText(mSlantedText, 0, mSlantedText.length)
                rectF.bottom = mTextPaint.descent() - mTextPaint.ascent()
                rectF.left += (rect.width() - rectF.right) / 2.0f
                rectF.top += (rect.height() - rectF.bottom) / 2.0f
                xy[0] = rectF.left
                xy[1] = rectF.top - mTextPaint.ascent()
                xy[2] = w / 2f + offset
                xy[3] = h / 2f + offset
                xy[4] = -ROTATE_ANGLE.toFloat()
            }
        }
        return xy
    }

    fun setText(str: String): SlantedTextView {
        mSlantedText = str
        postInvalidate()
        return this
    }

    fun setText(res: Int): SlantedTextView {
        val str = resources.getString(res)
        if (!TextUtils.isEmpty(str)) {
            setText(str)
        }
        return this
    }

    fun getText(): String {
        return mSlantedText
    }

    fun setSlantedBackgroundColor(color: Int): SlantedTextView {
        mSlantedBackgroundColor = color
        mPaint.color = mSlantedBackgroundColor
        postInvalidate()
        return this
    }

    fun setTextColor(color: Int): SlantedTextView {
        mTextColor = color
        mTextPaint.color = mTextColor
        postInvalidate()
        return this
    }

    /**
     * @param mode :
     * SlantedTextView.MODE_LEFT : top left
     * SlantedTextView.MODE_RIGHT : top right
     * @return this
     */
    fun setMode(mode: Int): SlantedTextView {
        require(!(mode > MODE_RIGHT_BOTTOM_TRIANGLE || mode < 0)) { 
            "$mode is illegal argument, please use right value" 
        }
        this.mode = mode
        postInvalidate()
        return this
    }

    fun setTextSize(size: Int): SlantedTextView {
        mTextSize = size.toFloat()
        mTextPaint.textSize = mTextSize
        postInvalidate()
        return this
    }

    /**
     * set slanted space length
     *
     * @param length
     * @return this
     */
    fun setSlantedLength(length: Int): SlantedTextView {
        mSlantedLength = length.toFloat()
        postInvalidate()
        return this
    }

    companion object {
        const val MODE_LEFT = 0
        const val MODE_RIGHT = 1
        const val MODE_LEFT_BOTTOM = 2
        const val MODE_RIGHT_BOTTOM = 3
        const val MODE_LEFT_TRIANGLE = 4
        const val MODE_RIGHT_TRIANGLE = 5
        const val MODE_LEFT_BOTTOM_TRIANGLE = 6
        const val MODE_RIGHT_BOTTOM_TRIANGLE = 7

        const val ROTATE_ANGLE = 45
    }
}
