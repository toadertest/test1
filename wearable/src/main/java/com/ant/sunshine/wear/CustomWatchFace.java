package com.ant.sunshine.wear;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.text.format.Time;

import com.ant.core.Utility;

public class CustomWatchFace {

    public static final String BASE_TIME_FORMAT = "%02d:%02d";
    private static final String TIME_FORMAT_WITHOUT_SECONDS = BASE_TIME_FORMAT + " %s";
    private static final String TIME_FORMAT_WITH_SECONDS = BASE_TIME_FORMAT + ":%02d" + " %s";
    private static final String HIGH_FORMAT_TEXT = "%s : %s";
    private static final String LOW_FORMAT_TEXT = "%s : %s";

    private static final String DATE_FORMAT = "%02d/%02d/%d";

    private static final int DATE_AND_TIME_DEFAULT_COLOUR = Color.BLACK;
    private static final int BACKGROUND_DEFAULT_COLOUR = Color.BLACK;
    public static final int DEFAULT_VALUE_LOW = 10;
    public static final int DEFAULT_VALUE_HIGH = 25;
    public static final String LOW_TEXT = "L";
    public static final float DEFAULT_YOFFSET = 20.0f;
    private double lowValue = DEFAULT_VALUE_LOW;
    private double highValue = DEFAULT_VALUE_HIGH;
    public static final String HIGH_TEXT = "H";

    private final Paint timePaint;
    private final Paint datePaint;
    private final Paint backgroundPaint;
    private final Paint lowTempPaint;
    private final Paint highTempPaint;
    private final Paint weatherPaint;
    private final Time time;

    private boolean shouldShowSeconds = true;
    private int backgroundColour = BACKGROUND_DEFAULT_COLOUR;
    private int dateAndTimeColour = DATE_AND_TIME_DEFAULT_COLOUR;
    private int mBackgroundId;
    private int mWeatherId;

    private Bitmap weatherBgBitmap;
    private Context context;

    public static CustomWatchFace newInstance(Context context) {

        Paint timePaint = initPaint(DATE_AND_TIME_DEFAULT_COLOUR, context.getResources().getDimension(R.dimen.time_size), null);

        Paint datePaint = initPaint(DATE_AND_TIME_DEFAULT_COLOUR, context.getResources().getDimension(R.dimen.date_size), null);

        Paint lowTempPaint = initPaint(Color.BLUE, context.getResources().getDimension(R.dimen.date_size), Paint.Align.LEFT);

        Paint highTempPaint = initPaint(Color.BLUE, context.getResources().getDimension(R.dimen.date_size), Paint.Align.LEFT);

        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(BACKGROUND_DEFAULT_COLOUR);

        Paint weatherPaint = new Paint();
        weatherPaint.setFilterBitmap(true);

        return new CustomWatchFace(timePaint,
                datePaint,
                backgroundPaint,
                lowTempPaint,
                highTempPaint,
                weatherPaint,
                new Time(),
                context);
    }

    @NonNull
    private static Paint initPaint(int dateAndTimeDefaultColour, float dimension, Paint.Align align) {
        Paint datePaint = new Paint();
        datePaint.setColor(dateAndTimeDefaultColour);
        datePaint.setTextSize(dimension);
        datePaint.setAntiAlias(true);
        if (align != null) {
            datePaint.setTextAlign(align);
        }
        return datePaint;
    }

    CustomWatchFace(Paint timePaint,
                    Paint datePaint,
                    Paint backgroundPaint,
                    Paint lowTempPaint,
                    Paint highTempPaint,
                    Paint weatherPaint,
                    Time time,
                    Context context) {
        mBackgroundId = R.drawable.background_hot;
        this.context = context;
        weatherBgBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(context.getResources(), mBackgroundId), 320, 320, false);
        this.timePaint = timePaint;
        this.datePaint = datePaint;
        this.lowTempPaint = lowTempPaint;
        this.highTempPaint = highTempPaint;
        this.backgroundPaint = backgroundPaint;
        this.weatherPaint = weatherPaint;
        this.time = time;
    }

    public void draw(Canvas canvas, Rect bounds) {
        time.setToNow();
        weatherBgBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(context.getResources(), mBackgroundId), bounds.width(), bounds.height(), false);
        canvas.drawBitmap(weatherBgBitmap, 0, 0, backgroundPaint);
        float defaultYOffset = DEFAULT_YOFFSET;

        String timeText = String.format(shouldShowSeconds ? TIME_FORMAT_WITH_SECONDS : TIME_FORMAT_WITHOUT_SECONDS, time.hour % 12, time.minute, time.second, (time.hour < 12) ? "am" : "pm");
        float timeXOffset = computeXOffset(timeText, timePaint, bounds, 0f);
        float timeYOffset = computeTimeYOffset(timeText, timePaint, bounds);
        canvas.drawText(timeText, timeXOffset, timeYOffset, timePaint);

        String dateText = String.format(DATE_FORMAT, time.monthDay, (time.month + 1), time.year);
        float dateXOffset = computeXOffset(dateText, datePaint, bounds, 0f);
        float dateYOffset = computeDateYOffset(dateText, datePaint);
        canvas.drawText(dateText, dateXOffset, timeYOffset + dateYOffset, datePaint);
        //draw the low temperature
        String formatLowTemp = Utility.formatTemperature(context, lowValue);
        String lowTempText = String.format(LOW_FORMAT_TEXT, LOW_TEXT, formatLowTemp);
        float lowTempTextLength = lowTempPaint.measureText(lowTempText);
        float lowTimeXOffset = computeXOffset(lowTempText, lowTempPaint, bounds, (lowTempTextLength / 2.0f));
        float lowTimeYOffset = computeYOffset(lowTempText, lowTempPaint, 0f);
        canvas.drawText(lowTempText, lowTimeXOffset, defaultYOffset + lowTimeYOffset + timeYOffset + dateYOffset, lowTempPaint);
        //draw the high temperature
        String formatHighTemp = Utility.formatTemperature(context, lowValue);
        String highTempText = String.format(HIGH_FORMAT_TEXT, HIGH_TEXT, formatHighTemp);
        float highTempTextLength = lowTempPaint.measureText(highTempText);
        float highTimeXOffset = computeXOffset(highTempText, highTempPaint, bounds, (highTempTextLength / (2.0f)));
        float highTimeYOffset = computeYOffset(highTempText, lowTempPaint, 0.0f);
        canvas.drawText(highTempText, highTimeXOffset, defaultYOffset + lowTimeYOffset + highTimeYOffset + timeYOffset + dateYOffset, lowTempPaint);
        if (mWeatherId != -1) {
            int iconId = Utility.getIconResourceForWeatherCondition(mWeatherId);
            if (iconId != -1) {
                Bitmap bitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(context.getResources(), iconId), 60, 60, false);
                if (bitmap != null) {
                    float xLength = (float) bitmap.getWidth();
                    float xOffset = bounds.width() - (1.5f) * xLength;
                    canvas.drawBitmap(bitmap, xOffset, defaultYOffset + timeYOffset + dateYOffset, weatherPaint);
                }
            }
        }

        //draw the bitmap.
    }

    private float computeXOffset(String text, Paint paint, Rect watchBounds, float extra) {
        float centerX = watchBounds.exactCenterX();
        float timeLength = paint.measureText(text);
        return centerX - (timeLength / 2.0f) - extra;
    }

    private float computeTimeYOffset(String timeText, Paint timePaint, Rect watchBounds) {
        float centerY = watchBounds.exactCenterY();
        Rect textBounds = new Rect();
        timePaint.getTextBounds(timeText, 0, timeText.length(), textBounds);
        int textHeight = textBounds.height();
        return centerY - (textHeight / 2.0f);
    }

    private float computeDateYOffset(String dateText, Paint datePaint) {
        return computeYOffset(dateText, datePaint, 10.0f);
    }

    private float computeYOffset(String dateText, Paint datePaint, float extra) {
        Rect textBounds = new Rect();
        datePaint.getTextBounds(dateText, 0, dateText.length(), textBounds);
        return textBounds.height() + extra;
    }

    public void setAntiAlias(boolean antiAlias) {
        timePaint.setAntiAlias(antiAlias);
        datePaint.setAntiAlias(antiAlias);
    }

    public void updateTimeZoneWith(String timeZone) {
        time.clear(timeZone);
        time.setToNow();
    }

    public void setShowSeconds(boolean showSeconds) {
        shouldShowSeconds = showSeconds;
    }

    public void updateLowValue(double value) {
        this.lowValue = value;
    }

    public void updateHighValue(double value) {
        this.highValue = value;
    }

    public void restoreBackgroundColour() {
        backgroundPaint.setColor(backgroundColour);
    }

    public void updateBackgroundColourToDefault() {
        backgroundPaint.setColor(BACKGROUND_DEFAULT_COLOUR);
    }

    public void updateDateAndTimeColourToDefault() {
        timePaint.setColor(DATE_AND_TIME_DEFAULT_COLOUR);
        datePaint.setColor(DATE_AND_TIME_DEFAULT_COLOUR);
    }

    public void restoreDateAndTimeColour() {
        timePaint.setColor(dateAndTimeColour);
        datePaint.setColor(dateAndTimeColour);
    }

    public void updateWeatherDrawable(int drawableId) {
        this.mWeatherId = drawableId;
    }

    public void updateAmbient(boolean force) {
        if (force) {
            updateBackgroundColourToDefault();
            updateDateAndTimeColourToDefault();
        } else {
            restoreBackgroundColour();
            restoreDateAndTimeColour();
        }
        lowTempPaint.setAntiAlias(force);
        highTempPaint.setAntiAlias(force);
    }
}
