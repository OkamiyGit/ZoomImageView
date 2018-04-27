package com.wxkj.ycw.touchview;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;

/**
 * Created by Okamiy on 2018/4/19.
 * Email: 542839122@qq.com
 * 自定义ImageView
 * OnGlobalLayoutListener  捕获图片加载完成的监听
 */

public class ZoomImageView extends AppCompatImageView implements ViewTreeObserver.OnGlobalLayoutListener, ScaleGestureDetector.OnScaleGestureListener, View.OnTouchListener {

    private boolean mOnce = false;//初始化的操作只需要一次
    /**
     * 初始化时得到的值(缩放值应该在mInitScale和mMaxScale之间)
     */
    private float mInitScale;
    /**
     * 双击放大时的到达的值
     */
    private float mMidScale;
    /**
     * 放大的极限值
     */
    private float mMaxScale;
    /**
     * 缩放。平移
     */
    private Matrix mScaleMatrix;
    //多点触控类
    private ScaleGestureDetector mScaleGestureDetector;
    private Context mContext;

    /**
     * 捕获用户多指触控时缩放的比例，知道用户是放大还是缩小
     */

    //----------------------------自由移动--------------

    /**
     * 记录上一次多点触控的数量
     * 比如2个手指和4个手指。中心点的坐标还是差距很大的，所以在手指个数发生变化时
     * 需要监测，以防发生跳跃式变化
     */
    private int mLastPointerCount;

    //记录上一次多点中心点的坐标
    private float mLastX;
    private float mLastY;
    //判断是不是move事件的标准
    private int mTouchSlop;

    private boolean isCanDrag;
    private boolean isCheckLeftAndRight;//移动时检测左右边界
    private boolean isCheckTopAndBottom;//移动时检测上下边界

    //-----------------双击放大与缩小
    private GestureDetector mGestureDetector;
    private boolean isAutoScale;//标识当前是不是在双击，正在双击就不需要再次进行放大操作

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /**
     * 初始化
     */
    private void init(Context context) {
        this.mContext = context;
        mScaleMatrix = new Matrix();
        //无论用户设置什么缩放都会将其覆盖
        setScaleType(ScaleType.MATRIX);

        mScaleGestureDetector = new ScaleGestureDetector(mContext, this);
        setOnTouchListener(this);

        mTouchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();

        mGestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isAutoScale) {//当前正在缩放
                    return true;
                }

                //当前的缩放比例小于我们的mMidScale时，双击就放大到我们的mMidScale，其他所有情况都缩小到mInitScale
                float x = e.getX();//当前点击的x
                float y = e.getY();

                //以用户点击的点进行缩放
                if (getScale() < mMidScale) {
                    //                    mScaleMatrix.postScale(mMidScale / getScale(), mMidScale / getScale(), x, y);
                    //                    setImageMatrix(mScaleMatrix);
                    postDelayed(new AutoScaleRunnable(mMidScale, x, y), 16);
                    isAutoScale = true;
                } else {
                    //                    mScaleMatrix.postScale(mInitScale / getScale(), mInitScale / getScale(), x, y);
                    //                    setImageMatrix(mScaleMatrix);
                    postDelayed(new AutoScaleRunnable(mInitScale, x, y), 16);
                    isAutoScale = true;
                }
                return true;
            }
        });
    }

    /**
     * 进行缓慢的缩放
     * 传递一个目标缩放值和缩放中心点，实现一点点缩放
     */
    private class AutoScaleRunnable implements Runnable {

        private float mTargetScale;//缩放目标值
        private float x;//缩放中心点x
        private float y;

        private final float BIGGER = 1.07f;//放大梯度
        private final float SMALL = 0.93f;

        private float temScale;//临时变量

        public AutoScaleRunnable(float targetScale, float x, float y) {
            mTargetScale = targetScale;
            this.x = x;
            this.y = y;

            if (getScale() < mTargetScale) {//表示用户想放大
                temScale = BIGGER;
            }
            if (getScale() > mTargetScale) {
                temScale = SMALL;
            }
        }

        @Override
        public void run() {
            //进行缩放
            mScaleMatrix.postScale(temScale, temScale, x, y);
            checkBorderAndCenterWhenScale();
            setImageMatrix(mScaleMatrix);

            float currentScale = getScale();
            //满足条件就去执行run方法
            if ((temScale > 1.0f && currentScale < mTargetScale) || (temScale < 1.0f && currentScale > mTargetScale)) {
                postDelayed(this, 16);
            } else {//设置为我们的目标值
                float scale = mTargetScale / currentScale;
                mScaleMatrix.postScale(scale, scale, x, y);
                checkBorderAndCenterWhenScale();
                setImageMatrix(mScaleMatrix);
                isAutoScale = false;
            }
        }
    }

    /**
     * 获取当前图片的缩放比例
     */
    public float getScale() {
        float[] values = new float[9];
        //Matrix 里面是有9个值得一维数组，x和y的缩放是一样的
        mScaleMatrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    /**
     * 获取放大、缩小后图片的四点坐标、宽高
     */
    private RectF getMatrixRectF() {
        //拿到当前的Matrix,赋值给矩形就可以拿到四点坐标
        Matrix matrix = mScaleMatrix;
        RectF rectF = new RectF();
        Drawable d = getDrawable();
        if (d != null) {
            rectF.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            matrix.mapRect(rectF);
        }
        return rectF;
    }

    /**
     * 在缩放的时候进行边界控制以及位置控制,防止出现白边
     */
    private void checkBorderAndCenterWhenScale() {
        RectF rect = getMatrixRectF();
        float deltaX = 0;//左右差值
        float deltaY = 0;//上下差值

        //控件宽高
        int width = getWidth();
        int hight = getHeight();

        //宽高正好等于屏幕的宽高就不需要做偏移操作
        //rect.width()放大缩小以后的宽度
        //水平方向的控制
        if (rect.width() >= width) {
            if (rect.left > 0) {//图片和屏幕左边有空隙，要向左移动是负值
                deltaX = -rect.left;
            }
            if (rect.right < width) {
                deltaX = width - rect.right;
            }
        }

        //竖直方向的控制
        if (rect.height() >= hight) {

            if (rect.top > 0) {
                deltaY = -rect.top;
            }
            if (rect.bottom < hight) {
                deltaY = hight - rect.bottom;
            }
        }

        //如果宽度或者高度小于控件的宽或者高，则让其居中
        if (rect.width() < width) {
            deltaX = width / 2f - rect.right + rect.width() / 2f;
        }

        if (rect.height() < hight) {
            deltaY = hight / 2f - rect.bottom + rect.height() / 2f;
        }

        mScaleMatrix.postTranslate(deltaX, deltaY);
    }

    /**
     * 当移动时进行边界检查
     */
    private void checkBorderWhenTranslate() {
        //拿到缩放以后的图片
        RectF rectF = getMatrixRectF();

        float deltaX = 0;//左右差值
        float deltaY = 0;//上下差值

        //控件宽高
        int width = getWidth();
        int hight = getHeight();

        if (rectF.top > 0 && isCheckTopAndBottom) {
            deltaY = -rectF.top;
        }
        if (rectF.bottom < hight && isCheckTopAndBottom) {
            deltaY = hight - rectF.bottom;
        }
        if (rectF.left > 0 && isCheckLeftAndRight) {
            deltaX = -rectF.left;
        }
        if (rectF.right < width && isCheckLeftAndRight) {
            deltaX = width - rectF.right;
        }

        mScaleMatrix.postTranslate(deltaX, deltaY);
    }

    /**
     * 全局布局完成之后会调用这个方法
     * 获取Imageview加载完成的图片及宽高
     */
    @Override
    public void onGlobalLayout() {
        //根据屏幕大小和图片的大小完成缩放：适配手机
        if (!mOnce) {
            //得到控件的宽和高（一般好似屏幕的宽和高，但是有时也会小点，因为屏幕可能包含toobar等）
            int width = getWidth();
            int height = getHeight();

            //得到图片以及宽和高
            Drawable d = getDrawable();
            //控件没有设置图片直接返回，否则获取宽和高
            if (d == null) {
                return;
            }
            int dw = d.getIntrinsicWidth();
            int dh = d.getIntrinsicHeight();

            //进行缩放
            float scale = 1.0f;//默认缩放值
            //如果图片的宽度大于控件宽度，但是高度小于控件高度，我们将其缩小
            if (dw > width && dh < height) {
                //宽度进行缩小，让宽度变为屏幕的大小：乘以1.0防止做了整除出错
                scale = width * 1.0f / dw;
            }

            //如果图片的高度大于控件高度，但是宽度小于控件宽度，我们将其缩小
            if (dh > height && dw < width) {
                scale = height * 1.0f / dh;
            }

            //如果图片的宽度大于控件宽度，但是高度小于控件高度，我们将其缩小
            if ((dw > width && dh > height) || (dw < width && dh < height)) {
                scale = Math.min(width * 1.0f / dw, height * 1.0f / dh);
            }

            //得到初始化时缩放的比例
            mInitScale = scale;
            mMaxScale = mInitScale * 4;
            mMidScale = mInitScale * 2;

            //将图片移动到当前控件的中心
            int dx = getWidth() / 2 - dw / 2;
            int dy = getHeight() / 2 - dh / 2;

            //将图片计算的缩放和移动应用到图片上
            //平移
            mScaleMatrix.postTranslate(dx, dy);
            //缩放:以控件的中心进行缩放
            mScaleMatrix.postScale(mInitScale, mInitScale, width / 2, height / 2);
            setImageMatrix(mScaleMatrix);

            mOnce = true;
        }
    }

    /**
     * 当View离开Window时解除注册
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeGlobalOnLayoutListener(this);
    }

    /**
     * 当View加载到Window注册监听OnGlobalLayoutListener
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    /**
     * 缩放中
     * 缩放区间：mInitScale--mMaxScale
     *
     * @param detector 可以拿到缩放比例
     * @return
     */
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        //当前缩放的比例是否在我们的限度之内
        float scale = getScale();
        //获取当前缩放的值
        float scaleFactor = detector.getScaleFactor();

        if (getDrawable() == null) {
            return true;
        }

        //缩放范围的控制
        //没有达到最大值就允许放大:当前缩放小于最大比例且当前缩放值大于1是想放大允许放大
        //当前缩放比例大于最小缩放比例且当前缩放值小于1是想缩小允许
        if ((scale < mMaxScale && scaleFactor > 1.0f) || (scale > mInitScale && scaleFactor < 1.0f)) {

            //乘积小于最小值我们需要重置，设置最小值
            if (scale * scaleFactor < mInitScale) {
                scaleFactor = mInitScale / scale;
            }

            //乘积大于最大值我们需要重置，设置最大值
            if (scale * scaleFactor > mMaxScale) {
                scaleFactor = mMaxScale / scale;
            }

            //以多点触控的中心点缩放
            mScaleMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            //缩放时不断的进行检测中心和边界
            checkBorderAndCenterWhenScale();

            setImageMatrix(mScaleMatrix);
        }
        return false;//保证事件继续
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    //处理触摸事件
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        //双击的时候不允许放大缩小
        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }

        //将触摸事件传递给ScaleGestureDetector处理
        mScaleGestureDetector.onTouchEvent(event);

        //存储多点触控的中心点
        float x = 0;
        float y = 0;

        //拿到多点触控的数量,进行遍历从而得出中心点的位置
        int pointerCount = event.getPointerCount();
        for (int i = 0; i < pointerCount; i++) {
            x += event.getX(i);
            y += event.getY(i);
        }
        x /= pointerCount;
        y /= pointerCount;

        //手指数量发生改变，重新记录中心点坐标
        if (mLastPointerCount != pointerCount) {
            isCanDrag = false;
            mLastX = x;
            mLastY = y;
        }
        mLastPointerCount = pointerCount;

        //多点触控不用理会down，第一个手指就是down
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                //偏移量=当前 - 上一次
                float dx = x - mLastX;
                float dy = y - mLastY;

                //因为手指发生变化时已经设置为fasle了，需要在此时去判断是不是move行为
                if (!isCanDrag) {
                    isCanDrag = isMoveAction(dx, dy);
                }

                //可以移动
                if (isCanDrag) {
                    //拿到缩放以后图片的宽和高
                    RectF rectF = getMatrixRectF();
                    if (getDrawable() != null) {
                        //可以移动时每次都需要检测边界
                        isCheckLeftAndRight = isCheckTopAndBottom = true;
                        //图片的宽度小于控件的宽度就不允许横向移动
                        if (rectF.width() < getWidth()) {
                            isCheckLeftAndRight = false;
                            dx = 0;
                        }
                        //图片的高度小于控件的高度就不允许横向移动
                        if (rectF.height() < getHeight()) {
                            isCheckTopAndBottom = false;
                            dy = 0;
                        }

                        mScaleMatrix.postTranslate(dx, dy);
                        //移动过程中需要进行边界控制
                        checkBorderWhenTranslate();
                        setImageMatrix(mScaleMatrix);
                    }
                }
                //移动过程中不断记录上一次的x和y
                mLastX = x;
                mLastY = y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mLastPointerCount = 0;
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * 判断动作是不是move
     * 用距离判断
     */
    private boolean isMoveAction(float dx, float dy) {
        return Math.sqrt(dx * dx + dy * dy) > mTouchSlop;
    }
}
