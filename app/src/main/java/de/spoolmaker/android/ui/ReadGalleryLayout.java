/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import de.spoolmaker.android.R;

/**
 * Lightweight, dependency-free three-page gallery for the read screen.
 *
 * <p>The project currently does not use AndroidX, so this view implements the
 * requested horizontal swipe behavior without adding ViewPager2 or changing
 * the Gradle setup.</p>
 */
public final class ReadGalleryLayout extends LinearLayout {
    private static final int PAGE_RESULT = 0;
    private static final int PAGE_DETAILS = 1;
    private static final int PAGE_RAW = 2;

    private final int touchSlop;
    private final int minSwipeDistance;

    private View pageResult;
    private View pageDetails;
    private View pageRaw;
    private TextView dotResult;
    private TextView dotDetails;
    private TextView dotRaw;

    private float downX;
    private float downY;
    private int currentPage = PAGE_RESULT;
    private boolean animating;

    public ReadGalleryLayout(Context context) {
        this(context, null);
    }

    public ReadGalleryLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReadGalleryLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        minSwipeDistance = dp(48);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        pageResult = findViewById(R.id.readGalleryResultPage);
        pageDetails = findViewById(R.id.readGalleryDetailsPage);
        pageRaw = findViewById(R.id.readGalleryRawPage);
        dotResult = findViewById(R.id.readGalleryDotResult);
        dotDetails = findViewById(R.id.readGalleryDotDetails);
        dotRaw = findViewById(R.id.readGalleryDotRaw);

        dotResult.setOnClickListener(view -> showPage(PAGE_RESULT, true));
        dotDetails.setOnClickListener(view -> showPage(PAGE_DETAILS, true));
        dotRaw.setOnClickListener(view -> showPage(PAGE_RAW, true));

        Button copySummary = findViewById(R.id.buttonCopySummary);
        copySummary.setOnClickListener(view -> copySummaryToClipboard());

        // MainActivity keeps textRawDump hidden and toggles its visibility for the
        // old UI. Keep that source TextView for compatibility and mirror its text
        // into the permanently visible raw-data gallery page.
        TextView rawSource = findViewById(R.id.textRawDump);
        TextView rawVisible = findViewById(R.id.textRawDumpVisible);
        rawVisible.setText(rawSource.getText());
        rawSource.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                rawVisible.setText(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        showPage(PAGE_RESULT, false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                return Math.abs(dx) > touchSlop
                        && Math.abs(dx) > Math.abs(dy) * 1.2f;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                return true;
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) >= minSwipeDistance && Math.abs(dx) > Math.abs(dy)) {
                    if (dx < 0 && currentPage < PAGE_RAW) {
                        showPage(currentPage + 1, true);
                    } else if (dx > 0 && currentPage > PAGE_RESULT) {
                        showPage(currentPage - 1, true);
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    private void showPage(int newPage, boolean animate) {
        if (newPage < PAGE_RESULT || newPage > PAGE_RAW) {
            return;
        }
        if (newPage == currentPage) {
            updateDots();
            return;
        }
        if (animating) {
            return;
        }

        View oldPage = pageFor(currentPage);
        View nextPage = pageFor(newPage);
        int oldIndex = currentPage;
        currentPage = newPage;
        updateDots();

        if (!animate || getWidth() <= 0) {
            oldPage.setVisibility(GONE);
            oldPage.setTranslationX(0f);
            nextPage.setTranslationX(0f);
            nextPage.setVisibility(VISIBLE);
            return;
        }

        animating = true;
        int direction = newPage > oldIndex ? 1 : -1;
        float distance = getWidth();

        oldPage.animate().cancel();
        nextPage.animate().cancel();
        nextPage.setTranslationX(direction * distance);
        nextPage.setVisibility(VISIBLE);

        oldPage.animate()
                .translationX(-direction * distance)
                .setDuration(180L)
                .withEndAction(() -> {
                    oldPage.setVisibility(GONE);
                    oldPage.setTranslationX(0f);
                    animating = false;
                })
                .start();

        nextPage.animate()
                .translationX(0f)
                .setDuration(180L)
                .start();
    }

    private View pageFor(int page) {
        switch (page) {
            case PAGE_DETAILS:
                return pageDetails;
            case PAGE_RAW:
                return pageRaw;
            case PAGE_RESULT:
            default:
                return pageResult;
        }
    }

    private void updateDots() {
        updateDot(dotResult, currentPage == PAGE_RESULT);
        updateDot(dotDetails, currentPage == PAGE_DETAILS);
        updateDot(dotRaw, currentPage == PAGE_RAW);
    }

    private void updateDot(TextView dot, boolean selected) {
        int color = getResources().getColor(
                selected ? R.color.primary : R.color.text_secondary,
                getContext().getTheme());
        dot.setTextColor(color);
        dot.setAlpha(selected ? 1f : 0.45f);
        dot.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        dot.setSelected(selected);
    }

    private void copySummaryToClipboard() {
        int[] fieldIds = new int[]{
                R.id.textUid,
                R.id.textGuid,
                R.id.textMaterialResult,
                R.id.textWeightResult,
                R.id.textTimestamp,
                R.id.textBatch,
                R.id.textStation,
                R.id.textCrc
        };

        StringBuilder out = new StringBuilder(512);
        for (int fieldId : fieldIds) {
            TextView field = findViewById(fieldId);
            CharSequence value = field.getText();
            if (value != null && value.length() > 0) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(value);
            }
        }

        if (out.length() == 0) {
            Toast.makeText(getContext(), R.string.copy_no_data, Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(getContext(), R.string.copy_clipboard_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        clipboard.setPrimaryClip(ClipData.newPlainText("Spool Maker Ergebnis", out.toString()));
        Toast.makeText(getContext(), R.string.copy_result_done, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
