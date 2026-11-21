package com.xxq.xlai;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;
import android.graphics.Color;
// import android.util.Log; // If you had a Log.d statement, remove it or move it after super()

public class GradientTextview extends AppCompatTextView {

    // Constructor 1
    public GradientTextview(Context context) {
        super(context); // THIS MUST BE THE FIRST STATEMENT
        // Any other initialization code can go here
        // For example: init(); if you had a common initialization method
    }

    // Constructor 2
    public GradientTextview(Context context, AttributeSet attrs) {
        super(context, attrs); // THIS MUST BE THE FIRST STATEMENT
        // Any other initialization code can go here
    }

    // Constructor 3
    public GradientTextview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); // THIS MUST BE THE FIRST STATEMENT
        // Any other initialization code can go here
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (changed) {
            applyGradient();
        }
    }

    private void applyGradient() {
        int viewWidth = getMeasuredWidth();

        if (viewWidth == 0) {
            return;
        }

        int[] colors = new int[]{
                Color.BLACK,
                Color.parseColor("#1DA1F2"), // Your Blue color
                Color.parseColor("#FF7782")  // Your Pink color
        };

        // Define the positions for each color
        // Example for "more pink, less blue":
        // Black starts at 0%
        // Blue is reached by 30% of the width (adjust this value)
        // Pink is reached by 100% of the width
        float[] positions = new float[]{0.0f, 0.4f, 0.6f}; // More pink, less blue (blue transition is faster)

        // Experiment with these positions:
        // {0.0f, 0.2f, 1.0f} : Even less blue, more pink
        // {0.0f, 0.4f, 1.0f} : Slightly more blue than 0.3f, but still pink dominant

        Shader textShader = new LinearGradient(
                0, 0, viewWidth, 0, // Gradient from left (0) to right (viewWidth)
                colors,
                positions, // Pass your defined positions array here
                Shader.TileMode.CLAMP
        );

        getPaint().setShader(textShader);
        invalidate();
    }
}
