package com.pnuema.android.marqueeview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MarqueeView extends SurfaceView implements Runnable, SurfaceHolder.Callback {
    private SpannableStringBuilder textHolder;

    private DrawString drawStringOne;
    private DrawString drawStringTwo;
    private int rightBoundry;
    private int width;
    private int scrollWrapSpacer; //space added between the two strings scrolling when the previous ends and the next one start scrolling
    private boolean pastFirstDelay;
    private long startTime;
    private long animationStartDelay = 0; //ms before scrolling starts, controlled by attribute (animationStartDelay) in the xml
    private int textSize;
    private int textColor;
    private Paint backgroundPaint = new Paint(); // keep a separate paint object for the background so it doesnt have to be changed so much
    private boolean isRunning;

    public MarqueeView(Context context) {
        super(context);
        init(context, null);
    }

    public MarqueeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MarqueeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        width = w;
        scrollWrapSpacer = width / 6; //set the space between the two scrolling strings to a 1/6 of the width of the view
        rightBoundry = width + getLeft();
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.MarqueeView,
                    0, 0);

            try {
                animationStartDelay = a.getInt(R.styleable.MarqueeView_animationStartDelay, 0);
                textSize = a.getInt(R.styleable.MarqueeView_textSize, 14);
                textColor = a.getColor(R.styleable.MarqueeView_textColor, 0);
            } finally {
                a.recycle();
            }
        }

        drawStringOne = new DrawString(0, textSize, textColor);
        drawStringTwo = new DrawString(Integer.MIN_VALUE, textSize, textColor);

        backgroundPaint.setColor(Color.WHITE);
        getHolder().setFormat(PixelFormat.TRANSPARENT);
        getHolder().addCallback(this);
    }

    /**
     * Sets the text to be drawn and initializes the text on the {@link DrawString} objects if it hasnt already been done.
     * The reason it only updates the text the first time is that we can not update the text while its scrolling in view.  It is placed into a
     * temporary holder which will be placed in the {@link DrawString} when its reset to the end of the view to start scrolling.
     * @param text {@link SpannableStringBuilder} representing the formatted string to display.
     */
    public void setText(SpannableStringBuilder text) {
        if (text == null) {
            return;
        }

        textHolder = text;

        if (drawStringOne.getDrawText() == null) {
            drawStringOne.setDrawText(textHolder);
        }

        if (drawStringTwo.getDrawText() == null) {
            drawStringTwo.setDrawText(textHolder);
        }

        if (!isRunning && textHolder.length() > 0) {
            startDrawing();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startDrawing();
    }

    /**
     * Starts the drawing thread
     */
    private void startDrawing() {
        isRunning = true;
        new Thread(this, getClass().getSimpleName() + "-drawingThread").start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        //stop the drawing thread (didnt use thread.stop due to deprecation and safety per Android docs)
        isRunning = false;
    }

    /**
     * Main drawing looper
     */
    @Override
    public void run() {
        Canvas canvas;
        startTime = System.currentTimeMillis(); //record the start time for use in respecting the initial delay before scrolling
        long startFrame;
        boolean skipRelocation = false; //prevents a wait on the canvas from relocating multiple times before drawing the frame
        long frameDelay;
        while (isRunning) {
            if(!getHolder().getSurface().isValid())
                continue;

            startFrame = System.currentTimeMillis();
            canvas = null;

            if (drawStringOne.getDrawText().length() == 0 && drawStringTwo.getDrawText().length() == 0) {
                isRunning = false;
                break;
            }

            //check if the positions need to be reset to the end of the view, and update the texts X Coord
            if (!skipRelocation) {
                drawStringOne.checkResetCoord(drawStringTwo);
                drawStringTwo.checkResetCoord(drawStringOne);
            }

            try {
                //wait on the canvas to lock
                canvas = getHolder().lockCanvas();
                if (canvas == null) {
                    Thread.sleep(1);
                    skipRelocation = true;
                    continue;
                }

                skipRelocation = false;

                synchronized (this) {
                    doDraw(canvas);
                }
            } catch (InterruptedException ignored) {
                //noop as it threw an exception while trying to sleep
            } finally {
                if (canvas != null) {
                    getHolder().unlockCanvasAndPost(canvas);
                }
            }

            //limit to 60fps (~16.667ms = 60fps) otherwise it runs wild
            frameDelay = System.currentTimeMillis() - startFrame;
            if (frameDelay < 16) {
                try {
                    Thread.sleep(16 - frameDelay);
                } catch (InterruptedException ignored) {
                    //noop as it threw an exception while trying to sleep
                }
            }
        }
    }

    /**
     * Does the drawing operation by drawing the text at the XCoord specified on the DrawString class
     * @param canvas {@link Canvas} on which to draw the text
     */
    private void doDraw(Canvas canvas) {
        canvas.drawColor(backgroundPaint.getColor());
        final int save1 = canvas.save();
        canvas.translate(drawStringOne.getxCoord(), 0);
        drawStringOne.getLayout().draw(canvas);
        canvas.restoreToCount(save1);

        final int save2 = canvas.save();
        canvas.translate(drawStringTwo.getxCoord(), 0);
        drawStringTwo.getLayout().draw(canvas);
        canvas.restoreToCount(save2);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Canvas canvas = holder.lockCanvas();
        canvas.drawColor(Color.WHITE);
        holder.unlockCanvasAndPost(canvas);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Canvas canvas = holder.lockCanvas();
        canvas.drawColor(Color.WHITE);
        holder.unlockCanvasAndPost(canvas);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        holder.removeCallback(this);
    }

    private class DrawString {
        private float speed = 2f;
        private float xCoord;
        private SpannableStringBuilder drawText;
        private float textWidth;
        private TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
        private StaticLayout layout;

        DrawString(int initialXCoord, int textSize, int textColor) {
            setxCoord(initialXCoord);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(textColor);
            paint.setTextSize(textSize);

            layout = new StaticLayout("", getPaint(), (int)(getTextWidth() + 0.5f), Layout.Alignment.ALIGN_NORMAL,1,0,false);
        }

        float getxCoord() {
            return xCoord;
        }

        void setxCoord(float xCoord) {
            this.xCoord = xCoord;
        }

        SpannableStringBuilder getDrawText() {
            return drawText;
        }

        void setDrawText(SpannableStringBuilder drawText) {
            this.drawText = drawText;
            setTextWidth(measureTextSpannable(drawText, getPaint()));

            layout = new StaticLayout(drawText, getPaint(), (int)(getTextWidth() + 0.5f), Layout.Alignment.ALIGN_NORMAL,1,0,false);
        }

        private float getTextWidth() {
            return textWidth;
        }

        private void setTextWidth(float textWidth) {
            this.textWidth = textWidth;
        }

        public TextPaint getPaint() {
            return paint;
        }

        public void setPaint(TextPaint paint) {
            this.paint = paint;
        }

        public Layout getLayout() {
            return layout;
        }

        public void setLayout(StaticLayout layout) {
            this.layout = layout;
        }

        /**
         * Measures the drawn width of the text to display
         * @param text Text to measure
         * @param paint {@link Paint} object that will be used to draw the text
         * @return The width of the text to be drawn using the paint object provided.
         */
        private float measureTextSpannable(CharSequence text, TextPaint paint) {
            return Math.round(Layout.getDesiredWidth(text, paint) + 0.5f); //rounding to the nearest int
        }

        /**
         * Updates the X Coordinate if its past the initial delay
         */
        private void updateXCoord() {
            if (getxCoord() != Integer.MIN_VALUE && getTextWidth() >= width && (pastFirstDelay || System.currentTimeMillis() - startTime > animationStartDelay)) {
                setxCoord(getxCoord() - speed);
                pastFirstDelay = true;
            }
        }

        /**
         * Triggers the X Coordinate update and checks/resets the X Coord to the right boundry if necessary
         * @param other {@link DrawString} representing the other string object to draw along with this one
         */
        void checkResetCoord(DrawString other) {
            updateXCoord();
            if (getxCoord() + getTextWidth() < rightBoundry && other.getxCoord() < getxCoord()) {
                //text fully scrolled (swap texts and start new animation)
                other.setDrawText(textHolder);
                other.setxCoord(rightBoundry + scrollWrapSpacer);
            }
        }
    }
}
