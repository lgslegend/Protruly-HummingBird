/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
// add by wxue 20170427 start
import android.hardware.fingerprint.FingerprintManager;
// add by wxue 20170427 end
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.text.TextUtils;
import android.media.AudioSystem;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.RenderNodeAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;

import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
import com.android.settingslib.animation.AppearAnimationCreator;
import com.android.settingslib.animation.AppearAnimationUtils;
import com.android.settingslib.animation.DisappearAnimationUtils;
import java.util.List;

public class KeyguardPatternView extends LinearLayout implements KeyguardSecurityView,
        AppearAnimationCreator<LockPatternView.CellState>,
        EmergencyButton.EmergencyButtonCallback {

    private static final String TAG = "SecurityPatternView";
    private static final boolean DEBUG = KeyguardConstants.DEBUG;

    // how long before we clear the wrong pattern
    private static final int PATTERN_CLEAR_TIMEOUT_MS = 2000;

    // how long we stay awake after each key beyond MIN_PATTERN_BEFORE_POKE_WAKELOCK
    private static final int UNLOCK_PATTERN_WAKE_INTERVAL_MS = 7000;

    // how many cells the user has to cross before we poke the wakelock
    private static final int MIN_PATTERN_BEFORE_POKE_WAKELOCK = 2;

    private final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private final AppearAnimationUtils mAppearAnimationUtils;
    private final DisappearAnimationUtils mDisappearAnimationUtils;

    private CountDownTimer mCountdownTimer = null;
    private LockPatternUtils mLockPatternUtils;
    private AsyncTask<?, ?, ?> mPendingLockCheck;
    private LockPatternView mLockPatternView;
    private KeyguardSecurityCallback mCallback;

    /**
     * Keeps track of the last time we poked the wake lock during dispatching of the touch event.
     * Initialized to something guaranteed to make us poke the wakelock when the user starts
     * drawing the pattern.
     * @see #dispatchTouchEvent(android.view.MotionEvent)
     */
    private long mLastPokeTime = -UNLOCK_PATTERN_WAKE_INTERVAL_MS;

    /**
     * Useful for clearing out the wrong pattern after a delay
     */
    private Runnable mCancelPatternRunnable = new Runnable() {
        @Override
        public void run() {
            mLockPatternView.clearPattern();
        }
    };
    private Rect mTempRect = new Rect();
    private KeyguardMessageArea mSecurityMessageDisplay;
    // add by wxue 20170323 start
    private View mUnlockFailedContainer;
    private KeyguardMessageArea mUnlockFailedMessageDisplay;
    private ViewGroup mFrameContainer;
    // add by wxue 20170323 end 
    private View mEcaView;
    private ViewGroup mContainer;
    private int mDisappearYTranslation;

    enum FooterMode {
        Normal,
        ForgotLockPattern,
        VerifyUnlocked
    }

    public KeyguardPatternView(Context context) {
        this(context, null);
    }

    public KeyguardPatternView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        mAppearAnimationUtils = new AppearAnimationUtils(context,
                AppearAnimationUtils.DEFAULT_APPEAR_DURATION, 1.5f /* translationScale */,
                2.0f /* delayScale */, AnimationUtils.loadInterpolator(
                        mContext, android.R.interpolator.linear_out_slow_in));
        mDisappearAnimationUtils = new DisappearAnimationUtils(context,
                125, 1.2f /* translationScale */,
                0.6f /* delayScale */, AnimationUtils.loadInterpolator(
                        mContext, android.R.interpolator.fast_out_linear_in));
        mDisappearYTranslation = getResources().getDimensionPixelSize(
                R.dimen.disappear_y_translation);
    }
    
    @Override
    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        mCallback = callback;
    }
    
    @Override
    public void setLockPatternUtils(LockPatternUtils utils) {
        mLockPatternUtils = utils;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLockPatternUtils = mLockPatternUtils == null
                ? new LockPatternUtils(mContext) : mLockPatternUtils;

        mLockPatternView = (LockPatternView) findViewById(R.id.lockPatternView);
        mLockPatternView.setSaveEnabled(false);
        mLockPatternView.setOnPatternListener(new UnlockPatternListener());

        // stealth mode will be the same for the life of this screen
        mLockPatternView.setInStealthMode(!mLockPatternUtils.isVisiblePatternEnabled(
                KeyguardUpdateMonitor.getCurrentUser()));

        // vibrate mode will be the same for the life of this screen
        mLockPatternView.setTactileFeedbackEnabled(mLockPatternUtils.isTactileFeedbackEnabled());

        mSecurityMessageDisplay =
                (KeyguardMessageArea) KeyguardMessageArea.findSecurityMessageDisplay(this);
        // add by wxue 20170323 start
        mFrameContainer = (ViewGroup) findViewById(R.id.frame_container);
        mUnlockFailedContainer = findViewById(R.id.unlock_failed_container);
        mUnlockFailedMessageDisplay = (KeyguardMessageArea) KeyguardMessageArea.findUnlockFailedMessageDisplay(this);
        // add by wxue 20170323 end
        mEcaView = findViewById(R.id.keyguard_selector_fade_container);
        mContainer = (ViewGroup) findViewById(R.id.container);

        EmergencyButton button = (EmergencyButton) findViewById(R.id.emergency_call_button);
        if (button != null) {
            button.setCallback(this);
        }
        // add by wxue 20170427 start
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateCallback);
        // add by wxue 20170427 end
    }

    @Override
    public void onEmergencyButtonClickedWhenInCall() {
        mCallback.reset();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = super.onTouchEvent(ev);
        // as long as the user is entering a pattern (i.e sending a touch event that was handled
        // by this screen), keep poking the wake lock so that the screen will stay on.
        final long elapsed = SystemClock.elapsedRealtime() - mLastPokeTime;
        if (result && (elapsed > (UNLOCK_PATTERN_WAKE_INTERVAL_MS - 100))) {
            mLastPokeTime = SystemClock.elapsedRealtime();
        }
        mTempRect.set(0, 0, 0, 0);
        offsetRectIntoDescendantCoords(mLockPatternView, mTempRect);
        ev.offsetLocation(mTempRect.left, mTempRect.top);
        result = mLockPatternView.dispatchTouchEvent(ev) || result;
        ev.offsetLocation(-mTempRect.left, -mTempRect.top);
        return result;
    }

    @Override
    public void reset() {
        // reset lock pattern
        mLockPatternView.enableInput();
        mLockPatternView.setEnabled(true);
        mLockPatternView.clearPattern();

        // if the user is currently locked out, enforce it.
        long deadline = mLockPatternUtils.getLockoutAttemptDeadline(
                KeyguardUpdateMonitor.getCurrentUser());
        if (deadline != 0) {
            handleAttemptLockout(deadline);
        } else {
            displayDefaultSecurityMessage();
        }
    }

    private void displayDefaultSecurityMessage() {
        if (mKeyguardUpdateMonitor.getMaxBiometricUnlockAttemptsReached()) {
            if (mLockPatternUtils.usingVoiceWeak(mKeyguardUpdateMonitor.getCurrentUser())) {
                mSecurityMessageDisplay.setMessage(R.string.voiceunlock_multiple_failures, true);
                /// M: [ALPS01748966] supress voice unlock view
                mKeyguardUpdateMonitor.setAlternateUnlockEnabled(false);
            }
        } else {
        	// add by wxue 20170327 start
            mLockPatternView.setVisibility(View.VISIBLE);
            mUnlockFailedMessageDisplay.setMessage("", false);
            mUnlockFailedContainer.setVisibility(View.GONE);
            // add by wxue 20170327 end
            // modify by wxue 20170407 start
            //mSecurityMessageDisplay.setMessage(R.string.kg_pattern_instructions, false);
            mSecurityMessageDisplay.setMessage(R.string.kg_pattern_instructions, true);
            // modify by wxue 20170407 end
        }
    }

    @Override
    public void showUsabilityHint() {
    }

    /** TODO: hook this up */
    public void cleanUp() {
        if (DEBUG) Log.v(TAG, "Cleanup() called on " + this);
        mLockPatternUtils = null;
        mLockPatternView.setOnPatternListener(null);
    }

    private class UnlockPatternListener implements LockPatternView.OnPatternListener {

        @Override
        public void onPatternStart() {
            mLockPatternView.removeCallbacks(mCancelPatternRunnable);
            mSecurityMessageDisplay.setMessage("", false);
        }

        @Override
        public void onPatternCleared() {
        }

        @Override
        public void onPatternCellAdded(List<LockPatternView.Cell> pattern) {
            mCallback.userActivity();
        }

        @Override
        public void onPatternDetected(final List<LockPatternView.Cell> pattern) {
            mLockPatternView.disableInput();
            if (mPendingLockCheck != null) {
                mPendingLockCheck.cancel(false);
            }

            if (pattern.size() < LockPatternUtils.MIN_PATTERN_REGISTER_FAIL) {
                mLockPatternView.enableInput();
                onPatternChecked(false, 0, false /* not valid - too short */);
                return;
            }

            mPendingLockCheck = LockPatternChecker.checkPattern(
                    mLockPatternUtils,
                    pattern,
                    KeyguardUpdateMonitor.getCurrentUser(),
                    new LockPatternChecker.OnCheckCallback() {
                        @Override
                        public void onChecked(boolean matched, int timeoutMs) {
                            mLockPatternView.enableInput();
                            mPendingLockCheck = null;
                            onPatternChecked(matched, timeoutMs, true);
                        }
                    });
            if (pattern.size() > MIN_PATTERN_BEFORE_POKE_WAKELOCK) {
                mCallback.userActivity();
            }
        }

        private void onPatternChecked(boolean matched, int timeoutMs, boolean isValidPattern) {
            if (matched) {
                mCallback.reportUnlockAttempt(true, 0);
                mLockPatternView.setDisplayMode(LockPatternView.DisplayMode.Correct);
                mCallback.dismiss(true);
            } else {
                mLockPatternView.setDisplayMode(LockPatternView.DisplayMode.Wrong);
                if (isValidPattern) {
                    mCallback.reportUnlockAttempt(false, timeoutMs);
                    if (timeoutMs > 0) {
                        long deadline = mLockPatternUtils.setLockoutAttemptDeadline(
                                KeyguardUpdateMonitor.getCurrentUser(), timeoutMs);
                        handleAttemptLockout(deadline);
                        //HB. Comments : TODO , Engerineer : wxue , Date : 2017年6月23日 ,begin
                        mKeyguardUpdateMonitor.reportSecurityModeLocked(timeoutMs);
                        //HB. end
                    }
                }
                if (timeoutMs == 0) {
                    mSecurityMessageDisplay.setMessage(R.string.kg_wrong_pattern, true);
                    mLockPatternView.postDelayed(mCancelPatternRunnable, PATTERN_CLEAR_TIMEOUT_MS);
                }
            }
        }
    }

    private void handleAttemptLockout(long elapsedRealtimeDeadline) {
        mLockPatternView.clearPattern();
        mLockPatternView.setEnabled(false);
        // modify by wxue 20170327 start
        mLockPatternView.setVisibility(View.INVISIBLE);
        mUnlockFailedContainer.setVisibility(View.VISIBLE);
        // modify by wxue 20170327 end
        final long elapsedRealtime = SystemClock.elapsedRealtime();
        mCountdownTimer = new CountDownTimer(elapsedRealtimeDeadline - elapsedRealtime, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                final int secondsRemaining = (int) (millisUntilFinished / 1000);
                // modify by wxue 20170323 start
                /*mSecurityMessageDisplay.setMessage(
                        R.string.kg_too_many_failed_attempts_countdown, true, secondsRemaining);*/
                mSecurityMessageDisplay.setMessage("",false);
                mUnlockFailedMessageDisplay.setMessage(R.string.kg_too_many_failed_attempts_countdown, true, secondsRemaining);
             // modify by wxue 20170323 end
            }

            @Override
            public void onFinish() {
                mLockPatternView.setEnabled(true);
                displayDefaultSecurityMessage();
                //HB. Comments : report security mode locked, Engerineer : wxue , Date : 2017年6月23日 ,begin
                mKeyguardUpdateMonitor.reportSecurityModeLocked(0);
                //HB. end
            }

        }.start();
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public void onPause() {
        if (mCountdownTimer != null) {
            mCountdownTimer.cancel();
            mCountdownTimer = null;
        }
        if (mPendingLockCheck != null) {
            mPendingLockCheck.cancel(false);
            mPendingLockCheck = null;
        }
    }

    @Override
    public void onResume(int reason) {
        reset();
       ///M: add for voice unlock
        ///   display prompt message when voice unlock is disabled because of
        ///   media is playing in background.
        final boolean mediaPlaying = AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC, 0);
        if (mLockPatternUtils.usingVoiceWeak() && mediaPlaying) {
            mSecurityMessageDisplay.setMessage(R.string.voice_unlock_media_playing, true);
        }
    }

    @Override
    public KeyguardSecurityCallback getCallback() {
        return mCallback;
    }

    @Override
    public void showPromptReason(int reason) {
        switch (reason) {
            case PROMPT_REASON_RESTART:
                mSecurityMessageDisplay.setMessage(R.string.kg_prompt_reason_restart_pattern,
                        true /* important */);
                break;
            default:
        }
    }

    @Override
    public void startAppearAnimation() {
        enableClipping(false);
        setAlpha(1f);
        setTranslationY(mAppearAnimationUtils.getStartTranslation());
        AppearAnimationUtils.startTranslationYAnimation(this, 0 /* delay */, 500 /* duration */,
                0, mAppearAnimationUtils.getInterpolator());
        mAppearAnimationUtils.startAnimation2d(
                mLockPatternView.getCellStates(),
                new Runnable() {
                    @Override
                    public void run() {
                        enableClipping(true);
                    }
                },
                this);
        if (!TextUtils.isEmpty(mSecurityMessageDisplay.getText())) {
            mAppearAnimationUtils.createAnimation(mSecurityMessageDisplay, 0,
                    AppearAnimationUtils.DEFAULT_APPEAR_DURATION,
                    mAppearAnimationUtils.getStartTranslation(),
                    true /* appearing */,
                    mAppearAnimationUtils.getInterpolator(),
                    null /* finishRunnable */);
        }
    }

    @Override
    public boolean startDisappearAnimation(final Runnable finishRunnable) {
        mLockPatternView.clearPattern();
        enableClipping(false);
        setTranslationY(0);
        AppearAnimationUtils.startTranslationYAnimation(this, 0 /* delay */, 300 /* duration */,
                -mDisappearAnimationUtils.getStartTranslation(),
                mDisappearAnimationUtils.getInterpolator());
        mDisappearAnimationUtils.startAnimation2d(mLockPatternView.getCellStates(),
                new Runnable() {
                    @Override
                    public void run() {
                        enableClipping(true);
                        if (finishRunnable != null) {
                            finishRunnable.run();
                        }
                    }
                }, KeyguardPatternView.this);
        if (!TextUtils.isEmpty(mSecurityMessageDisplay.getText())) {
            mDisappearAnimationUtils.createAnimation(mSecurityMessageDisplay, 0,
                    200,
                    - mDisappearAnimationUtils.getStartTranslation() * 3,
                    false /* appearing */,
                    mDisappearAnimationUtils.getInterpolator(),
                    null /* finishRunnable */);
        }
        return true;
    }

    private void enableClipping(boolean enable) {
        setClipChildren(enable);
        mContainer.setClipToPadding(enable);
        mContainer.setClipChildren(enable);
        // add by wxue 20170503 start
    	mFrameContainer.setClipToPadding(enable);
    	mFrameContainer.setClipChildren(enable);
    	// add by wxue 20170503 end
    }

    @Override
    public void createAnimation(final LockPatternView.CellState animatedCell, long delay,
            long duration, float translationY, final boolean appearing,
            Interpolator interpolator,
            final Runnable finishListener) {
        mLockPatternView.startCellStateAnimation(animatedCell,
                1f, appearing ? 1f : 0f, /* alpha */
                appearing ? translationY : 0f, appearing ? 0f : translationY, /* translation */
                appearing ? 0f : 1f, 1f /* scale */,
                delay, duration, interpolator, finishListener);
        if (finishListener != null) {
            // Also animate the Emergency call
            mAppearAnimationUtils.createAnimation(mEcaView, delay, duration, translationY,
                    appearing, interpolator, null);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
    
    // add by wxue 20170427 start
    private KeyguardUpdateMonitorCallback mUpdateCallback = new KeyguardUpdateMonitorCallback() {
        public void onFingerprintHelp(int msgId, String helpString) {
        	if(!KeyguardUpdateMonitor.getInstance(mContext).isUnlockingWithFingerprintAllowed()){
        		return;
        	}
        	if(msgId == -1){
        		mSecurityMessageDisplay.setFingerprintAuthFailedMessage(helpString, true);
        	}
        };
        
        public void onFingerprintError(int msgId, String errString) {
        	if(msgId == FingerprintManager.FINGERPRINT_ERROR_LOCKOUT){
        		mSecurityMessageDisplay.setFingerprintAuthFailedMessage(getResources().getString(R.string.kg_pattern_instructions), false);
        	}
        };
    };
    // add by wxue 20170427 end
}
