package com.leodroidcoder

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.*
import android.support.annotation.ColorInt
import android.support.annotation.FloatRange
import android.support.annotation.Px
import android.support.v4.content.ContextCompat
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View


class SmileRatingBar @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    private val TAG = "SwipeRatingView"
    private val _boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val _smilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val _smileSidePaint: Paint
    private val _bitmapPaint: Paint
    private val _ratingTextPaint: TextPaint
    private val _maxRatingTextPaint: TextPaint
    private val _smilePath: Path = Path()
    private val _oval = RectF()
    private val _leftDimpleArch = RectF()
    private val _rightDimpleArch = RectF()
    private val _smileBounds = RectF()
    private var _boardColor = 0xFFFFFFF
    private var _smileColor = 0xFF000FF
    private var _shadowColor = 0xF000000
    //    private var _shadowColor =
    private val _textBounds = Rect()
    private var _radius = 0f
    private var _lastY = 0f
    // minimum rating value
    private var _minRating = 0f
    private var _maxRating = 5f
    // rating step
    private var _ratingStep = 0.5f
    private var _exactRating = 5f
    private var _animStep = 0.1f
    private val _handler = Handler(Looper.getMainLooper())
    private var _isMoving = false
    private var layoutWidth: Int = 0
    private var layoutHeight: Int = 0
    private var dimpleRectSide: Float = 0f
    // current rating text attributes
    private var _ratingTextColor = Color.GRAY
    // max rating text size
    private var _maxRatingTextColor = Color.GRAY
    private var _dp = 1f
    private var _rightIconDrawable: Drawable? = null
    private var _rightIconBitmap: Bitmap? = null
    private var _shadowWidth = 0f
    private var _fontName: String? = null
    private var _smileStrokeWidth = 0f
    // how much extra is allowed to pull
    private var _stretchPercent = 0.35f

    init {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.SmileRatingBar, 0, 0)
        try {
            _minRating = a.getFloat(R.styleable.SmileRatingBar_minRating, _minRating)
            _maxRating = a.getFloat(R.styleable.SmileRatingBar_maxRating, _maxRating)
            _ratingStep = a.getFloat(R.styleable.SmileRatingBar_ratingStep, _ratingStep)
            _exactRating = a.getFloat(R.styleable.SmileRatingBar_defaultRating, _exactRating)
            _boardColor = a.getInt(R.styleable.SmileRatingBar_boardColor, _boardColor)
            _smileColor = a.getInt(R.styleable.SmileRatingBar_smileColor, _smileColor)
            _ratingTextColor = a.getInt(R.styleable.SmileRatingBar_currentRatingColor, _ratingTextColor)
            _maxRatingTextColor = a.getInt(R.styleable.SmileRatingBar_maxRatingColor, _maxRatingTextColor)
            _shadowColor = a.getInt(R.styleable.SmileRatingBar_shadowColor, _shadowColor)
            _rightIconDrawable = ContextCompat.getDrawable(context, a.getResourceId(R.styleable.SmileRatingBar_hintImage, 0))
            _shadowWidth = a.getDimension(R.styleable.SmileRatingBar_shadowWidth, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BG_SHADOW_DEFAULT_DP, context.resources.displayMetrics))
            _smileStrokeWidth = a.getDimension(R.styleable.SmileRatingBar_smileStrokeWidth, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, SMILE_STROKE_WIDTH_DEFAULT_DP, context.resources.displayMetrics))
            _fontName = a.getString(R.styleable.SmileRatingBar_typeface)
        } finally {
            a.recycle()
        }

        if (_minRating < 0) {
            throw IllegalArgumentException("Invalid min rating value ($_minRating). It cannot be below then zero")
        } else if (_maxRating <= _minRating) {
            throw IllegalArgumentException("Max rating (which = $_maxRating) must be bigger than min rating (which = $_minRating)")
        } else if ((_maxRating - _minRating) < _ratingStep * 2) {
            throw IllegalArgumentException("Invalid rating step (which = $_ratingStep) for the specified max rating (which = $_maxRating) and min rating (which = $_minRating)")
        } else if (_exactRating < _minRating || _exactRating > _maxRating) {
            throw IllegalArgumentException("Invalid rating value ($_exactRating). It must be within the range: min rating=$_minRating and max rating =$_maxRating)")
        }

        // initialize background _boardPaint
        _boardPaint.color = _boardColor
        _boardPaint.setShadowLayer(_shadowWidth, 0f, 0f, _shadowColor)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(LAYER_TYPE_SOFTWARE, _boardPaint)
        }
        // smile paint
        _smilePaint.color = _smileColor
        _smilePaint.style = Paint.Style.STROKE
        _smilePaint.strokeWidth = _smileStrokeWidth
        _smilePaint.strokeCap = Paint.Cap.ROUND
        _smileSidePaint = Paint(_smilePaint)
        _smileSidePaint.strokeWidth = _smilePaint.strokeWidth / 2.3f

        // bitmap paint
        _bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        _dp = context.resources.displayMetrics.density

        // initialize current rating text paint
        _ratingTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        _ratingTextPaint.style = Paint.Style.FILL
        _ratingTextPaint.color = _ratingTextColor
        _ratingTextPaint.textAlign = Paint.Align.LEFT
        _ratingTextPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, RATING_TEXT_SIZE_SP, context.resources.displayMetrics)
        // initialize max rating text paint
        _maxRatingTextPaint = TextPaint(_ratingTextPaint)
        _maxRatingTextPaint.color = _maxRatingTextColor
        _ratingTextPaint.textAlign = Paint.Align.RIGHT
        _maxRatingTextPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, RATING_MAX_TEXT_SIZE_SP, context.resources.displayMetrics)

        if (!TextUtils.isEmpty(_fontName)) {
            // apply typeface to the text paints
            try {
                val typeface = Typeface.createFromAsset(context.assets, "fonts/$_fontName")
                _ratingTextPaint.typeface = typeface
                _maxRatingTextPaint.typeface = typeface
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("Invalid font name: $_fontName. The font should be put to the assets/fonts/ folder. Font attribute should be specified as a file name, for instance  `app:typeface=\"Roboto-Regular.ttf\"")
            }
        }
        // set rects for the left and right smile dimples
        dimpleRectSide = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DIMPLES_SIZE_DP, context.resources.displayMetrics)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        layoutWidth = MeasureSpec.getSize(widthMeasureSpec)
        layoutHeight = layoutWidth / 3 * 2
        this.setMeasuredDimension(layoutWidth, layoutHeight)

        _radius = layoutWidth / 3.6f // bg circle radius
        // calculate relational center coordinate, according to which smile will be drawn
        val yCenter = layoutHeight / 1.85f
        // set circle dimensions
        _oval.set(layoutWidth / 2 - _radius, layoutHeight / 2 - _radius, layoutWidth / 2 + _radius, layoutHeight / 2 + _radius)
        // set magnetic animation step in pixels
        _animStep = _radius / 1500
        // setup smile bounds
        _smileBounds.set(layoutWidth / 2 - _radius * 0.65f, yCenter - _radius * 0.7f, layoutWidth / 2 + _radius * 0.65f, yCenter + _radius * 0.7f)

        _leftDimpleArch.set(_smileBounds.left - dimpleRectSide, yCenter - dimpleRectSide, _smileBounds.left + dimpleRectSide, yCenter + dimpleRectSide)
        _rightDimpleArch.set(_smileBounds.right - dimpleRectSide, yCenter - dimpleRectSide, _smileBounds.right + dimpleRectSide, yCenter + dimpleRectSide)

        if (null != _rightIconDrawable) {
            val iconWidth = Math.round(_radius / 4)
            _rightIconBitmap = convertToBitmap(_rightIconDrawable!!, iconWidth, getHeightInProportion(iconWidth, _rightIconDrawable!!))
        }
    }

    /**
     * Convert [Drawable] into [Bitmap]

     * @param drawable     source drawable image
     * *
     * @param widthPixels  image width (due to canvas)
     * *
     * @param heightPixels image height (due to canvas)
     * *
     * @return [Bitmap]
     */
    private fun convertToBitmap(drawable: Drawable, widthPixels: Int, heightPixels: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPixels, heightPixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, widthPixels, heightPixels)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getHeightInProportion(width: Int, drawable: Drawable): Int {
        return (drawable.intrinsicHeight.toFloat() * width.toFloat() / drawable.intrinsicWidth.toFloat()).toInt()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas?.drawOval(_oval, _boardPaint)
        drawSmileCurve(canvas)
        drawRatingText(canvas)
        drawRightImage(canvas)
    }

    private fun drawSmileCurve(canvas: Canvas?) {
        _smilePath.reset()
        _smilePath.moveTo(_smileBounds.left, _smileBounds.centerY())
        val y2 = _smileBounds.top + (_smileBounds.bottom - _smileBounds.top) * _exactRating / _maxRating
        _smilePath.quadTo(_smileBounds.centerX(), y2, _smileBounds.right, _smileBounds.centerY())
        canvas?.drawPath(_smilePath, _smilePaint)
        // draw left and right dimples
        canvas?.drawArc(_leftDimpleArch, getLeftAngleStartByRating(_exactRating, _maxRating.toInt()), 90f, false, _smileSidePaint)
        canvas?.drawArc(_rightDimpleArch, getRightAngleStartByRating(_exactRating, _maxRating.toInt()), 90f, false, _smileSidePaint)
    }

    private fun drawRightImage(canvas: Canvas?) {
        if (null == _rightIconBitmap) {
            return
        }
        canvas?.drawBitmap(_rightIconBitmap, layoutWidth * 0.5f + _radius + (layoutWidth / 2 - _radius) / 2 - _rightIconBitmap!!.width / 2, layoutHeight / 2f - _rightIconBitmap!!.height / 2, _bitmapPaint)
    }

    private fun drawRatingText(canvas: Canvas?) {
        // draw max text
        val maxRatingFormatted = "/" + _maxRating
        // measure text bounds
        _maxRatingTextPaint.getTextBounds(maxRatingFormatted, 0, maxRatingFormatted.length, _textBounds)
        canvas?.drawText(maxRatingFormatted, layoutWidth / 2 - _radius - BG_SHADOW_DEFAULT_DP * _dp * 1.5f, layoutHeight / 2f - _textBounds.top, _maxRatingTextPaint)
        // draw current rating
        val currRatingFormatted = getFormattedRating()
        // measure text bounds
        _ratingTextPaint.getTextBounds(currRatingFormatted, 0, currRatingFormatted.length, _textBounds)
        canvas?.drawText(currRatingFormatted, layoutWidth / 2 - _radius - BG_SHADOW_DEFAULT_DP * _dp * 1.5f - 2 * _dp, layoutHeight / 2f - _textBounds.top, _ratingTextPaint)
    }

    /**
     * Converts current rating to user-readable format.
     * Makes rounding as well as filters values below [_minRating] and above [_maxRating]
     * (which are caused by pull effect which allows to pull smile out of scale)_
     */
    private fun getFormattedRating(): String {
        return "%.1f".format(getRoundedRating())
    }

    /**
     * Get rounded rating value.
     * Method returns rounded value of [_exactRating], which is multiple to [_ratingStep]
     *
     * @return the value which has to be shown to user or processed by the application
     */
    private fun getRoundedRating(): Float {
        return when {
            _exactRating < _minRating -> _minRating
            _exactRating > _maxRating -> _maxRating
            else -> (_exactRating / _ratingStep).toInt() * _ratingStep
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {

        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isTouchInSwipeableArea(event)) {
                    _isMoving = true
                    _lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                _isMoving = false
                _exactRating = getRoundedRating()
                moveToNearestPosition()
                _lastY = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                if (_isMoving && isTouchInSwipeableArea(event)) {
                    updateRating(event.y)
                    invalidateSmile()
                } else if (_isMoving) {
                    moveToNearestPosition()
                }
            }
        }
        return true
    }

    private fun getLeftAngleStartByRating(currentRating: Float, maxRating: Int): Float {
        return currentRating / maxRating * 90f + 90
    }

    private fun getRightAngleStartByRating(currentRating: Float, maxRating: Int): Float {
        return currentRating / maxRating * -90f + 360
    }

    /**
     * Check if touch is within swipable circle

     * @param event [MotionEvent]
     * *
     * @return true if touch is inside area
     */
    private fun isTouchInSwipeableArea(event: MotionEvent?): Boolean {
        return event != null && _oval.contains(event.x, event.y)
    }

    private fun updateRating(newY: Float) {
        _exactRating += (newY - _lastY) * _maxRating * SENSIBILITY / (_smileBounds.height())
        if (_exactRating > _maxRating + _maxRating * _stretchPercent) {
            _isMoving = false
            moveToNearestPosition()
            return
        } else if (_exactRating < _minRating - _maxRating * _stretchPercent) {
            _isMoving = false
            moveToNearestPosition()
            return
        }
        _lastY = newY
        invalidateSmile()
    }

    private fun moveToNearestPosition() {
        if (_exactRating > _maxRating) {
            // user over pulled, swipe back to to the max rating value
            _exactRating -= _animStep
            if (_exactRating > _maxRating) {
                _handler.postDelayed(autoSwipe, ANIM_FPS)
            } else {
                _exactRating = _maxRating
            }
        } else if (_exactRating < _minRating) {
            // user pulled rating below zero, swipe it back to zero
            _exactRating += _animStep
            if (_exactRating < _minRating) {
                _handler.postDelayed(autoSwipe, ANIM_FPS)
            } else {
                _exactRating = _minRating
            }
        } else {
            // calculate the value to the nearest rating step
            val diff: Float = _exactRating % _ratingStep
            if (diff != 0f) {
                val res = (_exactRating / _ratingStep).toInt()
                if (diff >= ratingStep / 2f) {
                    // round up
                    _exactRating += _animStep
                } else {
                    //round down
                    _exactRating -= _animStep
                }
                val resNew = (_exactRating / _ratingStep).toInt()
                if (resNew == res) {
                    // continue moving and invalidate
                    _handler.postDelayed(autoSwipe, ANIM_FPS)
                } else {
                    // reached the step
                    _exactRating = resNew * _ratingStep
                }
            }
        }
    }

    /**
     * Handle magnetic effect
     */
    private val autoSwipe = Runnable {
        invalidateSmile()
        moveToNearestPosition()
    }

    /**
     * Invalidates smile area
     */
    private fun invalidateSmile() {
        invalidate((_smileBounds.left - dimpleRectSide).toInt(), _smileBounds.top.toInt(), (_smileBounds.right - dimpleRectSide).toInt(), _smileBounds.bottom.toInt())
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putFloat(INSTANCE_STATE_CURRENT_RATING, _exactRating)
        bundle.putFloat(INSTANCE_STATE_MIN_RATING, _minRating)
        bundle.putFloat(INSTANCE_STATE_MAX_RATING, _maxRating)
        bundle.putFloat(INSTANCE_STATE_RATING_STEP, _ratingStep)
        bundle.putFloat(INSTANCE_STATE_SHADOW_WIDTH, _shadowWidth)
        bundle.putFloat(INSTANCE_STATE_SMILE_STROKE_WIDTH, _smileStrokeWidth)
        bundle.putInt(INSTANCE_STATE_BOARD_COLOR, _boardColor)
        bundle.putInt(INSTANCE_STATE_SMILE_COLOR, _smileColor)
        bundle.putInt(INSTANCE_STATE_RATING_TEXT_COLOR, _ratingTextColor)
        bundle.putInt(INSTANCE_STATE_MAX_RATING_TEXT_COLOR, _maxRatingTextColor)
        bundle.putInt(INSTANCE_STATE_SHADOW_COLOR, _shadowColor)
        bundle.putString(INSTANCE_STATE_TYPEFACE_NAME, _fontName)
        bundle.putParcelable(INSTANCE_RIGHT_ICON_BITMAP, _rightIconBitmap)
        bundle.putParcelable(INSTANCE_STATE_SUPER, super.onSaveInstanceState())
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            _exactRating = state.getFloat(INSTANCE_STATE_CURRENT_RATING)
            _minRating = state.getFloat(INSTANCE_STATE_MIN_RATING)
            _maxRating = state.getFloat(INSTANCE_STATE_MAX_RATING)
            _ratingStep = state.getFloat(INSTANCE_STATE_RATING_STEP)
            _shadowWidth = state.getFloat(INSTANCE_STATE_SHADOW_WIDTH)
            _smileStrokeWidth = state.getFloat(INSTANCE_STATE_SMILE_STROKE_WIDTH)
            _boardColor = state.getInt(INSTANCE_STATE_BOARD_COLOR)
            _smileColor = state.getInt(INSTANCE_STATE_SMILE_COLOR)
            _ratingTextColor = state.getInt(INSTANCE_STATE_RATING_TEXT_COLOR)
            _maxRatingTextColor = state.getInt(INSTANCE_STATE_MAX_RATING_TEXT_COLOR)
            _shadowColor = state.getInt(INSTANCE_STATE_SHADOW_COLOR)
            _fontName = state.getString(INSTANCE_STATE_SHADOW_COLOR)
            _rightIconBitmap = state.getParcelable(INSTANCE_RIGHT_ICON_BITMAP)
            return super.onRestoreInstanceState(state.getParcelable(INSTANCE_STATE_SUPER))
        }
        super.onRestoreInstanceState(state)
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (isTouchInSwipeableArea(event)) {
            // handle touch events when the view is placed inside a ScrollView
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(event)
    }

    var rating: Float
        @FloatRange(from = 0.0) get() = getRoundedRating()
        @FloatRange(from = 0.0) set(value) {
            if (value < _minRating || value > _maxRating) {
                throw IllegalArgumentException("Invalid rating value ($value). It must be within the range: min rating=$_minRating and max rating =$_maxRating)")
            }
            _exactRating = value
            invalidate()
        }

    var minRating: Float
        @FloatRange(from = 0.0) get() = _minRating
        @FloatRange(from = 0.0) set(value) {
            if (value < 0) {
                throw IllegalArgumentException("Invalid min rating value ($value). It cannot be below then zero")
            } else if (value >= _maxRating) {
                throw IllegalArgumentException("Min rating value ($value) must be smaller than max rating ($_maxRating)")
            }
            _minRating = value
            invalidate()
        }

    var maxRating: Float
        @FloatRange(from = 0.0) get() = _maxRating
        @FloatRange(from = 0.0) set(value) {
            if (value <= _minRating) {
                throw IllegalArgumentException("Max rating value ($value) must be bigger than min rating ($_minRating)")
            }
            _maxRating = value
            invalidate()
        }

    var ratingStep: Float
        @FloatRange(from = 0.0) get() = _ratingStep
        @FloatRange(from = 0.0) set(value) {
            if ((_maxRating - _minRating) < _ratingStep * 2) {
                throw IllegalArgumentException("Invalid rating step (which = $_ratingStep) for the specified max rating (which = $_maxRating) and min rating (which = $_minRating)")
            }
            _ratingStep = value
            invalidate()
        }


    var boardColor: Int
        @ColorInt get() = _boardColor
        @ColorInt set(value) {
            _boardColor = value
            _boardPaint.color = _boardColor
            invalidate()
        }

    var smileColor: Int
        @ColorInt get() = _smileColor
        @ColorInt set(value) {
            _smileColor = value
            _smilePaint.color = _smileColor
            invalidate()
        }

    var ratingTextColor: Int
        @ColorInt get() = _ratingTextColor
        @ColorInt set(value) {
            _ratingTextColor = value
            _ratingTextPaint.color = _ratingTextColor
            invalidate()
        }

    var maxRatingTextColor: Int
        @ColorInt get() = _maxRatingTextColor
        @ColorInt set(value) {
            _maxRatingTextColor = value
            _maxRatingTextPaint.color = _maxRatingTextColor
            invalidate()
        }

    var shadowColor: Int
        @ColorInt get() = _shadowColor
        @ColorInt set(value) {
            _shadowColor = value
            _boardPaint.setShadowLayer(_shadowWidth, 0f, 0f, _shadowColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                setLayerType(LAYER_TYPE_SOFTWARE, _boardPaint)
            }
            invalidate()
        }

    var shadowWidth: Float
        @Px get() = _shadowWidth
        @Px set(value) {
            if (value < 0) {
                throw IllegalArgumentException("Invalid shadow width ($value)")
            }
            _shadowWidth = value
            _boardPaint.setShadowLayer(_shadowWidth, 0f, 0f, _shadowColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                setLayerType(LAYER_TYPE_SOFTWARE, _boardPaint)
            }
            invalidate()
        }

    var hintDrawable: Drawable?
        get() = _rightIconDrawable
        set(value) {
            _rightIconDrawable = value
            if (null != _rightIconDrawable) {
                val iconWidth = Math.round(_radius / 4)
                _rightIconBitmap = convertToBitmap(_rightIconDrawable!!, iconWidth, getHeightInProportion(iconWidth, _rightIconDrawable!!))
            }
            invalidate()
        }

    var smileStrokeWidth: Float
        @Px get() = _smileStrokeWidth
        @Px set(value) {
            if (value < 0) {
                throw IllegalArgumentException("Invalid smile stroke width ($value)")
            }
            _smileStrokeWidth = value
            _smilePaint.strokeWidth = _smileStrokeWidth
            _smileSidePaint.strokeWidth = _smilePaint.strokeWidth / 2.3f
            invalidate()
        }

    var fontName: String?
        get() = _fontName
        set(value) {
            // apply typeface to the text paints
            _fontName = value
            try {
                val typeface = Typeface.createFromAsset(context.assets, "fonts/$_fontName")
                _ratingTextPaint.typeface = typeface
                _maxRatingTextPaint.typeface = typeface
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("Invalid font name: $_fontName. The font should be put to the assets/fonts/ folder. Font attribute should be specified as a file name, for instance  `app:typeface=\"Roboto-Regular.ttf\"")
            }
            invalidate()
        }

    companion object {
        // animation frames per second
        private const val ANIM_FPS = 11L
        // text sizes in SP
        private const val RATING_MAX_TEXT_SIZE_SP = 14f
        private const val RATING_TEXT_SIZE_SP = 24f
        // smile stroke width in dp
        private const val SMILE_STROKE_WIDTH_DEFAULT_DP = 7f
        // dimples size in dp
        private const val DIMPLES_SIZE_DP = 14f
        // background shadow size
        private const val BG_SHADOW_DEFAULT_DP = 20f
        // scroll to change rating sensibility
        private const val SENSIBILITY = 1.1f
        // instance state keys
        private const val INSTANCE_STATE_SUPER = "INSTANCE_STATE_SUPER"
        private const val INSTANCE_STATE_CURRENT_RATING = "INSTANCE_STATE_CURRENT_RATING"
        private const val INSTANCE_STATE_MIN_RATING = "INSTANCE_STATE_MIN_RATING"
        private const val INSTANCE_STATE_MAX_RATING = "INSTANCE_STATE_MAX_RATING"
        private const val INSTANCE_STATE_RATING_STEP = "INSTANCE_STATE_RATING_STEP"
        private const val INSTANCE_STATE_BOARD_COLOR = "INSTANCE_STATE_BOARD_COLOR"
        private const val INSTANCE_STATE_SMILE_COLOR = "INSTANCE_STATE_SMILE_COLOR"
        private const val INSTANCE_STATE_RATING_TEXT_COLOR = "INSTANCE_STATE_RATING_TEXT_COLOR"
        private const val INSTANCE_STATE_MAX_RATING_TEXT_COLOR = "INSTANCE_STATE_MAX_RATING_TEXT_COLOR"
        private const val INSTANCE_STATE_SHADOW_COLOR = "INSTANCE_STATE_SHADOW_COLOR"
        private const val INSTANCE_RIGHT_ICON_BITMAP = "INSTANCE_RIGHT_ICON_BITMAP"
        private const val INSTANCE_STATE_SHADOW_WIDTH = "INSTANCE_STATE_SHADOW_WIDTH"
        private const val INSTANCE_STATE_SMILE_STROKE_WIDTH = "INSTANCE_STATE_SMILE_STROKE_WIDTH"
        private const val INSTANCE_STATE_TYPEFACE_NAME = "INSTANCE_STATE_TYPEFACE_NAME"
    }
}
