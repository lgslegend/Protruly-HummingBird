package com.android.mms.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.mms.R;

/**
 * Created by Danxx on 2016/7/29.
 * 最简单的方式实现圆角图片
 */
public class ZzzCircleImageView extends View {
    /**
     * 默认圆角大小
     */
    private static final int DEFUALT_RADIUS = 20;
    /**
     * 源图片
     */
    private Bitmap mSrc;
    //占位图片
    private Drawable mHolderDrawable;
    /**
     * 圆角大小，默认为20
     */
    private int mRadius = DEFUALT_RADIUS;
    /**
     * 控件的宽度
     */
    private int mWidth;
    /**
     * 控件的高度
     */
    private int mHeight;

    private int mSetWidth;
    private int mSetHeight;

    private Context mContext;

    private int mType = -1;
    public static final int IMAGE_TYPE = 0;
    public static final int VIDEO_TYPE = 1;
    public static final int AUDIO_TYPE = 2;
    public static final int VCARD_TYPE = 3;

    private int mDefaultWidth;
    private int mImageWidth;


    public ZzzCircleImageView(Context context) {
        super(context);
        init(context ,null ,0);
    }

    public ZzzCircleImageView(Context context ,Bitmap bitmap) {
        super(context);
        Log.d("danxx" ,"create SampleCircleImageView");
        this.mSrc = bitmap;
        init(context ,null ,0);
    }

    public ZzzCircleImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context ,attrs ,0);
    }

    public ZzzCircleImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context ,attrs ,defStyleAttr);
    }

    private void init(Context context ,AttributeSet attrs ,int defStyleAttr){
        mContext = context;
        if(attrs != null){
            /**Load the styled attributes and set their properties**/
            TypedArray typedArray = context.obtainStyledAttributes(attrs , R.styleable.ZzzCircleImageView ,defStyleAttr ,0);
            //mSrc = BitmapFactory.decodeResource(context.getResources() ,typedArray.getResourceId(R.styleable.CircleImageView_src ,android.R.drawable.ic_dialog_dialer));
            mSrc = BitmapFactory.decodeResource(context.getResources() ,typedArray.getResourceId(R.styleable.ZzzCircleImageView_src ,0));
            //mHolderDrawable = context.getResources().getDrawable(R.drawable.attachment_image_placeholder_background);
            mHolderDrawable = typedArray.getDrawable(R.styleable.ZzzCircleImageView_holderDrawable);
            mType = typedArray.getInt( R.styleable.ZzzCircleImageView_type, 0 );
            if(mHolderDrawable == null) {
                mHolderDrawable = context.getResources().getDrawable(R.drawable.zzz_mms_attachment_view_background);
            }
            mRadius = (int) typedArray.getDimension(R.styleable.ZzzCircleImageView_radius ,dp2px(DEFUALT_RADIUS));
            typedArray.recycle();
        }
        mDefaultWidth = (int)mContext.getResources().getDimension(R.dimen.mms_default_attachment_size);
        mImageWidth = (int)mContext.getResources().getDimension(R.dimen.mms_image_attachment_size);
        if(mType == IMAGE_TYPE) {
            setSize( mImageWidth, mImageWidth );
        }
    }

    public void setType(int type) {
        this.mType = type;
        int tmpWidth = mSetWidth;
        if(mType == IMAGE_TYPE) {
            setSize( mImageWidth, mImageWidth );
        } else {
            setSize( mDefaultWidth, mImageWidth );
        }
        if(tmpWidth != mSetWidth) {
            requestLayout();
        }
    }

    public int getType() {
        return this.mType;
    }
    /**
     * 测量控件大小
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.d("danxx" ,"onMeasure");
        if (mSetWidth != 0 && mSetHeight != 0) {
            mWidth = mSetWidth;
            mHeight = mSetHeight;
            setMeasuredDimension(mWidth ,mHeight);
            return;
        }
        /*if (mSrc == null) {
            if (mSetWidth != 0 && mSetHeight != 0) {
                mWidth = mSetWidth;
                mHeight = mSetHeight;
                setMeasuredDimension(mWidth ,mHeight);
                return;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }*/
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        /**
         * 一个MeasureSpec封装了父布局传递给子布局的布局要求，每个MeasureSpec代表了一组宽度和高度的要求。
         * 三种测量模式解释：
         *  UNSPECIFIED：父布局没有给子布局任何限制，子布局可以任意大小。
         *  EXACTLY：父布局决定子布局的确切大小。不论子布局多大，它都必须限制在这个界限里。match_parent
         *  AT_MOST：此时子控件尺寸只要不超过父控件允许的最大尺寸,子布局可以根据自己的大小选择任意大小。wrap_content
         *
         * 简单的映射关系：
         *  wrap_content -> MeasureSpec.AT_MOST
         *  match_parent -> MeasureSpec.EXACTLY
         *  具体值 -> MeasureSpec.EXACTLY
         */

        /**获取宽高的测量模式**/
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        /**获取宽高的尺寸**/
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);
        /**
         * 测量宽度
         */
        //if(widthSpecMode == MeasureSpec.EXACTLY){  //宽为具体值或者是填满父控件就直接赋值 match_parent , accurate
            mWidth = widthSpecSize;
        /*}else{
            *//**图片显示时原始大小**//*
            int srcWidth = mSrc.getWidth() + getPaddingLeft() + getPaddingRight();
            if(widthSpecMode == MeasureSpec.AT_MOST){ //wrap_content,子控件不能超过父控件,此时我们取传递过来的大小和图片本身大小的小者
                mWidth = Math.min(widthSpecSize , srcWidth);
            }else{
                //没有要求，可以随便大小
                mWidth = srcWidth;
            }
        }*/

        /**
         * 测量高度，逻辑跟测量宽度是一样的
         */
        //if(heightSpecMode == MeasureSpec.EXACTLY){  //match_parent , accurate
            mHeight = heightSpecSize;
        /*}else{
            *//**图片显示时原始大小**//*
            int srcHeigth = mSrc.getHeight() + getPaddingTop() + getPaddingBottom();
            if(heightSpecMode == MeasureSpec.AT_MOST){ //wrap_content
                mHeight = Math.min(heightSpecSize , srcHeigth);
            }else{
                //没有要求，可以随便大小
                mHeight = srcHeigth;
            }
        }
        if (mType == VIDEO_TYEP) {
            float scale = mVideoWidth / mWidth;
            mWidth = (int)mVideoWidth;
            mHeight = (int)(mHeight * scale);
        }*/
        setMeasuredDimension(mWidth ,mHeight);
    }

    /**
     * 绘制控件
     * @param canvas
     */
    @Override
    protected void onDraw(Canvas canvas) {
        Log.d("danxx" ,"onDraw");
//        super.onDraw(canvas);
        if (mSrc == null) {
            super.onDraw(canvas);
            return;
        }
        canvas.drawBitmap(createRoundConerImage(mSrc) ,0 ,0 ,null);
    }

    /**
     * 设置圆角大小
     * @param radius
     */
    public void setRadius(int radius){
        this.mRadius = radius;
    }

    //设置图片的大小
    public void setSize(int width, int height) {
        mSetWidth = width;
        mSetHeight = height;
        setBackground(mHolderDrawable);
    }

    /**
     * 设置图片
     * @param bitmap
     */
    public void setSrc(Bitmap bitmap){
        /*if (null == bitmap) {
            return;
        }*/
        this.mSrc = bitmap;
        //requestLayout();
        invalidate();
    }

    public void setImageBitmap(Bitmap bitmap){
        /*if (null == bitmap) {
            return;
        }*/
        this.mSrc = bitmap;
        //requestLayout();
        invalidate();
    }

    public void setImageDrawable(Drawable drawable){
        /*if (null == bitmap) {
            return;
        }*/
        this.mSrc = drawableToBitmap(drawable);
        //requestLayout();
        invalidate();
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if(drawable == null) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                        : Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        //canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }
    /**
     * 根据给定的图片和已经测量出来的宽高来绘制圆角图形
     * 原理：
     * 基本原理就是先画一个圆角的图形出来，然后在圆角图形上画我们的源图片，
     * 圆角图形跟我们的源图片堆叠时我们取交集并显示上层的图形
     * 原理就是这样，很简单。
     */
    private Bitmap createRoundConerImage(Bitmap source){
        final Paint paint = new Paint();
        /**开启抗锯齿**/
        paint.setAntiAlias(true);
        /****/
        Bitmap target  = Bitmap.createBitmap(mWidth,mHeight, Bitmap.Config.ARGB_8888);
        /**
         * Construct a canvas with the specified bitmap to draw into. The bitmapmust be mutable
         * 以bitmap对象创建一个画布，则将内容都绘制在bitmap上，bitmap不得为null;
         */
        Canvas canvas = new Canvas(target);
        /**新建一个矩形绘制区域,并给出左上角和右下角的坐标**/
        RectF rect = new RectF(0 , 0 ,mWidth ,mHeight);
        /**
         * 把图片缩放成我们想要的大小,video是缩放，但是iamge取其中间。不进行缩放
         */
        if (mType == VIDEO_TYPE) {
            source = Bitmap.createScaledBitmap(source,mWidth,mHeight,false);
        } else {
            int dwidth = source.getWidth();
            int dheight = source.getHeight();
            float scale;
            /*if (dwidth * mHeight > mWidth * dheight) {
                scale = (float) mHeight / (float) dheight; 
                dx = (mWidth - dwidth * scale) * 0.5f;
            } else {
                scale = (float) mWidth / (float) dwidth;
                dy = (mHeight - dheight * scale) * 0.5f;
            }*/
            float scaleX = (float)mWidth / (float)dwidth;
            float scaleY = (float)mHeight / (float)dheight;
            scale = Math.max( scaleX, scaleY );
            int scaleWidth = (int)(dwidth * scale);
            int scaleHeight = (int)(dheight * scale);
            source = Bitmap.createScaledBitmap(source,scaleWidth,scaleHeight,false);
            int x = 0;
            int y = 0;
            if (scaleX > scaleY) {
                x = 0;
                y = (scaleHeight - mHeight) / 2;
            } else {
                y = 0;
                x = (scaleWidth - mWidth) / 2;
            }
            source = Bitmap.createBitmap( source, x, y, mWidth, mHeight );
        }
        /**在绘制矩形区域绘制用画笔绘制一个圆角矩形**/
        canvas.drawRoundRect(rect ,mRadius ,mRadius ,paint);
        /**
         * 我简单理解为设置画笔在绘制时图形堆叠时候的显示模式
         * SRC_IN:取两层绘制交集。显示上层。
         */
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(source ,0 ,0 ,paint);
        /****/
        return target;
    }

    protected int sp2px(float spValue) {
        final float fontScale = mContext.getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

    protected int dp2px(float dp) {
        final float scale = mContext.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

}
