package com.cadre.ocr.Core.ExtraViews;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

//import com.cadre.ocr.R;

import com.cadre.eneo.R;

import java.util.ArrayList;

/**
 * Created by Fadi on 5/11/2014.
 */
public class FocusBoxView extends View {

    private static final int MIN_FOCUS_BOX_WIDTH = 50;
    private static final int MIN_FOCUS_BOX_HEIGHT = 20;

    private final Paint paint;
    private final int maskColor;
    private final int textRectColor;
    private final String TAG = getClass().getSimpleName();
    private RoundRectShape roundRect;
    private RoundRectShape textBoxRounded;
    private Rect textBox;
    private Rect frame;
    private int previewWidth;
    private int previewHeight;
    private float canvasToPreviewRatioW;
    private float canvasToPreviewRatioH;
    private boolean ratioSet;
    private boolean previewSizeSet;
    private ArrayList<Rect> rectangles = new ArrayList<Rect>();

    public FocusBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.focus_box_mask);
        textRectColor = resources.getColor(R.color.textRect);

        // set up window raduis
        float windowOuterRadii[] = {0, 0, 0, 0, 0, 0, 0, 0};
        float windowInnerRadii[] = {8, 8, 8, 8, 8, 8, 8, 8};
        roundRect = new RoundRectShape(windowOuterRadii, new RectF(5, 5, 5, 5), windowInnerRadii);

        //set up text box
        float textBoxOuterRadii[] = {10, 10, 10, 10, 10, 10, 10, 10};
        float textBoxInnerRadii[] = {5, 5, 5, 5, 5, 5, 5, 5};
        textBoxRounded = new RoundRectShape(textBoxOuterRadii, null, textBoxInnerRadii);

        frame = getBoxRect();
        ratioSet = false;
        previewSizeSet = false;

    }

    private Rect box;

    private static Point ScrRes;

    private Rect getBoxRect() {

        if (box == null) {

            ScrRes = FocusBoxUtils.getScreenResolution(getContext());

            // Box takes up 6/7 of screen width
            int width = ScrRes.x * 6 / 7;
            // Box takes up 1/9 of screen height
            int height = ScrRes.y / 9;

            width = width == 0
                    ? MIN_FOCUS_BOX_WIDTH
                    : width < MIN_FOCUS_BOX_WIDTH ? MIN_FOCUS_BOX_WIDTH : width;

            height = height == 0
                    ? MIN_FOCUS_BOX_HEIGHT
                    : height < MIN_FOCUS_BOX_HEIGHT ? MIN_FOCUS_BOX_HEIGHT : height;

            // Set box equidistant on both sides
            int left = (ScrRes.x - width) / 2;

            // Use the same margin as width for the top
            int top = left;

            box = new Rect(left, top, left + width, top + height);
        }

        return box;
    }

    public Rect getBox() {
        return box;
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (!ratioSet && previewSizeSet) {

            int canvasWidth = (canvas.getWidth() < canvas.getHeight()) ? canvas.getWidth() : canvas.getHeight();
            int canvasHeight = (canvas.getHeight() > canvas.getWidth()) ? canvas.getHeight() : canvas.getWidth();
            canvasToPreviewRatioW = (float) canvasWidth / (float) previewWidth;
            canvasToPreviewRatioH = (float) canvasHeight / (float) previewHeight;

            Log.i(TAG, "Preview dimensions = " + previewWidth + " x " + previewHeight);
            Log.e(TAG, "Canvas dimensions = " + canvasWidth + " x " + canvasHeight);
            Log.i(TAG, "canvasToPreviewRatioW = " + canvasToPreviewRatioW);
            Log.i(TAG, "canvasToPreviewRatioH = " + canvasToPreviewRatioH);
            ratioSet = true;
        } else if (!previewSizeSet) {
            canvasToPreviewRatioH = canvasToPreviewRatioW = 1.0f;
        }

        Log.e(TAG, "canvasToPreviewRatioW = " + canvasToPreviewRatioW);
        Log.e(TAG, "canvasToPreviewRatioH = " + canvasToPreviewRatioH);

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the bounding boxes around the window
        paint.setColor(maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        roundRect.resize(frame.width() + 1, frame.height() + 1);
        canvas.save();
        canvas.translate(frame.left, frame.top);
        roundRect.draw(canvas, paint);
        canvas.restore();

        // Draw box around text if one exists
        /*paint.setColor(textRectColor);
        if (textBox != null) {
            Paint paint1 = new Paint();
            paint1.setStyle(Paint.Style.STROKE);
            paint1.setColor(Color.WHITE);
            paint1.setStrokeWidth(10);
            textBox.left = (int)((float)textBox.left * canvasToPreviewRatioW);
            textBox.top = (int)((float)textBox.top * canvasToPreviewRatioH);
            textBox.right = (int)((float)textBox.right * canvasToPreviewRatioW);
            textBox.bottom = (int)((float)textBox.bottom * canvasToPreviewRatioH);

//            canvas.drawRect(textBox.left,textBox.top,textBox.right,textBox.bottom,paint1);

            Log.i(TAG, "Drawing a textBox at (" + textBox.left +", " + textBox.top + ") from frame edge");
            textBoxRounded.resize(textBox.width(),textBox.height());
            canvas.save();
            canvas.translate(frame.left + textBox.left, frame.top + textBox.top);
            textBoxRounded.draw(canvas, paint1);
            canvas.restore();

            for (Rect rect : rectangles) {
//                canvas.drawRect(rect, paint);
            }
        }*/

        paint.setColor(textRectColor);
        if (rectangles != null) {
            int p = 1;
            for (Rect rect : rectangles) {
//                canvas.drawRect(rect, paint);
                Paint paint1 = new Paint();
                paint1.setStyle(Paint.Style.STROKE);
                paint1.setColor(Color.WHITE);
                paint1.setStrokeWidth(10);

                Log.e("rect width", rect.width() + "");

                Log.e("rect height", rect.height() + "");

                rect.left = (int) ((float) rect.left * canvasToPreviewRatioW);
                rect.top = (int) ((float) rect.top * canvasToPreviewRatioH);
                rect.right = (int) ((float) rect.right * canvasToPreviewRatioW);
                rect.bottom = (int) ((float) rect.bottom * canvasToPreviewRatioH);

                /*rect.left = (int) ((float) rect.left);
                rect.top = (int) ((float) rect.top);
                rect.right = (int) ((float) rect.right);
                rect.bottom = (int) ((float) rect.bottom);*/

//                  canvas.drawRect(frame.left + rect.left, frame.top + rect.top, frame.right , frame.bottom, paint1);
//                canvas.drawRect(canvas.getWidth() - (canvas.getWidth() - 150), canvas.getHeight() - fin, canvas.getWidth() - 150, height + top, paint);

//TODO template for translation below
//                box = new Rect(left, top, left + width, top + height);

                Log.i(TAG, "Drawing a textBox at (" + rect.left + ", " + rect.top + ") from frame edge");

                textBoxRounded.resize(rect.width(), rect.height());

                canvas.save();
                Log.e("frame left is ", frame.left + " and right is " + frame.right);
                canvas.translate(frame.left+rect.left, frame.top+ (rect.top* canvasToPreviewRatioW));
                textBoxRounded.draw(canvas, paint1);
                canvas.restore();

               /* int x = p - 1;
                if (x != 0) {
//                    + rect.top+rectangles.get(x).top
                    canvas.translate(frame.left + rect.left + rectangles.get(x).left, frame.top);
                    textBoxRounded.draw(canvas, paint1);
                    canvas.restore();
                }*/
                p++;
            }
        }
    }

    public void setTextBox(Rect rect) {
        textBox = rect;
    }


    public void setArrayTextBox(ArrayList<Rect> rect) {
        rectangles = rect;
    }

    public void setPreviewSize(int w, int h) {
        previewWidth = w;
        previewHeight = h;
        previewSizeSet = true;
    }

}
