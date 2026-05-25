package com.example.gravadordeacoes.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Build;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.example.gravadordeacoes.R;

public class FloatingButtonManager {

    public interface Listener {
        void onStartStopRecording();
        void onDebugRequested();
        void onSnapshotRequested();
        void onInspectTouch(float x, float y);
    }

    private static final String TAG = "GravadorDeAcoes";
    private final Context context;
    private final WindowManager windowManager;
    private final Listener listener;

    private View floatingButtonView;
    private View recordButton;
    private View inspectButton;

    private View overlayView;
    private WindowManager.LayoutParams overlayParams;
    private boolean isOverlayAdded = false;

    public FloatingButtonManager(Context context, Listener listener) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.listener = listener;
        setupFloatingButton();
        setupOverlayView();
    }

    private void setupFloatingButton() {
        floatingButtonView = LayoutInflater.from(context).inflate(R.layout.floating_button_layout2, null);

        recordButton = floatingButtonView.findViewById(R.id.floating_button);
        inspectButton = floatingButtonView.findViewById(R.id.btn_inspect);

        int layout_params_type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layout_params_type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        try {
            if (floatingButtonView.getWindowToken() == null) {
                windowManager.addView(floatingButtonView, params);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao adicionar view flutuante", e);
        }

        inspectButton.setOnClickListener(v -> listener.onSnapshotRequested());

        setupTouchListener(params);
    }

    private void setupOverlayView() {
        overlayView = new View(context);
        overlayView.setBackgroundColor(Color.TRANSPARENT);

        int layout_params_type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layout_params_type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                Log.d(TAG, "Toque interceptado no Overlay: X=" + event.getRawX() + " Y=" + event.getRawY());
                listener.onInspectTouch(event.getRawX(), event.getRawY());
                return true;
            }
            return false;
        });
    }

    private void setupTouchListener(final WindowManager.LayoutParams params) {
        final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                listener.onStartStopRecording();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                listener.onDebugRequested();
            }
        });

        recordButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (gestureDetector.onTouchEvent(event)) return true;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingButtonView, params);
                        return true;
                }
                return false;
            }
        });
    }

    public void setRecordingState(boolean isRecording) {
        if (recordButton != null) {
            int drawableId = isRecording
                    ? R.drawable.shape_floating_button_recording
                    : R.drawable.shape_floating_button;
            recordButton.setBackground(ContextCompat.getDrawable(context, drawableId));
        }
    }

    public void setInspectModeState(boolean isActive) {
        if (inspectButton != null) {
            if (isActive) {
                inspectButton.getBackground().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_ATOP);
                if (inspectButton instanceof ImageView) {
                    ((ImageView) inspectButton).setColorFilter(Color.BLACK);
                }

                if (!isOverlayAdded) {
                    try {
                        windowManager.addView(overlayView, overlayParams);
                        isOverlayAdded = true;
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao adicionar overlay", e);
                    }
                }
            } else {
                inspectButton.getBackground().clearColorFilter();
                if (inspectButton instanceof ImageView) {
                    ((ImageView) inspectButton).clearColorFilter();
                }

                if (isOverlayAdded) {
                    try {
                        windowManager.removeView(overlayView);
                        isOverlayAdded = false;
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao remover overlay", e);
                    }
                }
            }
        }
    }

    public void destroy() {
        try {
            if (floatingButtonView != null) windowManager.removeView(floatingButtonView);
            if (isOverlayAdded && overlayView != null) windowManager.removeView(overlayView);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao destruir views", e);
        }
    }
}