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
import android.animation.ObjectAnimator;
import android.content.Context;
import android.media.AudioSystem;
import android.util.AttributeSet;
import android.view.RenderNode;
import android.view.RenderNodeAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import com.android.settingslib.animation.AppearAnimationUtils;
import com.android.settingslib.animation.DisappearAnimationUtils;

/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardPINView extends KeyguardPinBasedInputView {

    private final AppearAnimationUtils mAppearAnimationUtils;
    private final DisappearAnimationUtils mDisappearAnimationUtils;
    private ViewGroup mContainer;
    // add by wxue 20170323 start
    private ViewGroup mFrameContainer;
    private View mUnlockFailedContainer;
    private SecurityMessageDisplay mUnlockFailedMessageDisplay;
    // add by wxue 20170323 end
    private ViewGroup mRow0;
    private ViewGroup mRow1;
    private ViewGroup mRow2;
    private ViewGroup mRow3;
    private View mDivider;
    private int mDisappearYTranslation;
    private View[][] mViews;

    public KeyguardPINView(Context context) {
        this(context, null);
    }

    public KeyguardPINView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAppearAnimationUtils = new AppearAnimationUtils(context);
        mDisappearAnimationUtils = new DisappearAnimationUtils(context,
                125, 0.6f /* translationScale */,
                0.45f /* delayScale */, AnimationUtils.loadInterpolator(
                        mContext, android.R.interpolator.fast_out_linear_in));
        mDisappearYTranslation = getResources().getDimensionPixelSize(
                R.dimen.disappear_y_translation);
    }

    protected void resetState() {
        super.resetState();

        if (KeyguardUpdateMonitor.getInstance(mContext).getMaxBiometricUnlockAttemptsReached()) {
            ///M: use different prompt message in face unlock or voice unlock
            if (mLockPatternUtils.usingVoiceWeak()) {
                mSecurityMessageDisplay.setMessage(R.string.voiceunlock_multiple_failures, true);
            }
        } else {
            /// M: [ALPS00581890] Indicate the user to input pin.
           // modify input hints by wxue 20170313 start
        	//mSecurityMessageDisplay.setMessage(R.string.kg_pin_instructions, true);
        	mSecurityMessageDisplay.setMessage(R.string.kg_password_instructions, true);
        	// modify input hints by wxue 20170313 end
        }
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.pinEntry;
    }

    // add by wxue 20170322 start
    @Override
    protected View getContainerView() {
    	return mContainer;
    }
    
    @Override
    protected SecurityMessageDisplay getUnlockFailedMsgDisplay() {
    	// TODO Auto-generated method stub
    	return mUnlockFailedMessageDisplay;
    }
    
    @Override
    protected View getUnlockFailedContainer() {
    	// TODO Auto-generated method stub
    	return mUnlockFailedContainer;
    }
    // add by wxue 20170322 end
    
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mContainer = (ViewGroup) findViewById(R.id.container);
        // add by wxue 20170323 start
        mFrameContainer = (ViewGroup) findViewById(R.id.frame_container);
        mUnlockFailedContainer = findViewById(R.id.unlock_failed_container);
        mUnlockFailedMessageDisplay = KeyguardMessageArea.findUnlockFailedMessageDisplay(this);
        // add by wxue 20170323 end
        mRow0 = (ViewGroup) findViewById(R.id.row0);
        mRow1 = (ViewGroup) findViewById(R.id.row1);
        mRow2 = (ViewGroup) findViewById(R.id.row2);
        mRow3 = (ViewGroup) findViewById(R.id.row3);
        mDivider = findViewById(R.id.divider);
        // modify by wxue 20170425 start
        /*mViews = new View[][]{
            new View[]{
                    mRow0, null, null
            },
            new View[]{
                    findViewById(R.id.key1), findViewById(R.id.key2),
                    findViewById(R.id.key3)
            },
            new View[]{
                    findViewById(R.id.key4), findViewById(R.id.key5),
                    findViewById(R.id.key6)
            },
            new View[]{
                    findViewById(R.id.key7), findViewById(R.id.key8),
                    findViewById(R.id.key9)
            },
            new View[]{
                    null, findViewById(R.id.key0), findViewById(R.id.key_enter)
            },
            new View[]{
                    null, mEcaView, null
            }};*/
        if(mPasswordEntry instanceof PinPasswordTextView){
        	mViews = new View[][]{
                new View[]{
                        mRow0, null, null
                },
                new View[]{
                        findViewById(R.id.key1), findViewById(R.id.key2),
                        findViewById(R.id.key3)
                },
                new View[]{
                        findViewById(R.id.key4), findViewById(R.id.key5),
                        findViewById(R.id.key6)
                },
                new View[]{
                        findViewById(R.id.key7), findViewById(R.id.key8),
                        findViewById(R.id.key9)
                },
                new View[]{
                        null, findViewById(R.id.key0), findViewById(R.id.delete_button)
                },
                new View[]{
                        null, mEcaView, null
                }};
        }else{
        	mViews = new View[][]{
                new View[]{
                        mRow0, null, null
                },
                new View[]{
                        findViewById(R.id.key1), findViewById(R.id.key2),
                        findViewById(R.id.key3)
                },
                new View[]{
                        findViewById(R.id.key4), findViewById(R.id.key5),
                        findViewById(R.id.key6)
                },
                new View[]{
                        findViewById(R.id.key7), findViewById(R.id.key8),
                        findViewById(R.id.key9)
                },
               new View[]{
                        null, findViewById(R.id.key0), findViewById(R.id.key_enter)
                },
                new View[]{
                        null, mEcaView, null
                }};
        }
        // modify by wxue 20170425 end
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public int getWrongPasswordStringId() {
    	// modify by wxue for input wrong hints 20170313 start
        //return R.string.kg_wrong_pin;
    	return R.string.kg_wrong_password;
    	// modify by wxue for input wrong hints 20170313 end
    }

    @Override
    public void startAppearAnimation() {
        enableClipping(false);
        setAlpha(1f);
        setTranslationY(mAppearAnimationUtils.getStartTranslation());
        AppearAnimationUtils.startTranslationYAnimation(this, 0 /* delay */, 500 /* duration */,
                0, mAppearAnimationUtils.getInterpolator());
        mAppearAnimationUtils.startAnimation2d(mViews,
                new Runnable() {
                    @Override
                    public void run() {
                        enableClipping(true);
                    }
                });
    }

    @Override
    public boolean startDisappearAnimation(final Runnable finishRunnable) {
        enableClipping(false);
        setTranslationY(0);
        AppearAnimationUtils.startTranslationYAnimation(this, 0 /* delay */, 280 /* duration */,
                mDisappearYTranslation, mDisappearAnimationUtils.getInterpolator());
        mDisappearAnimationUtils.startAnimation2d(mViews,
                new Runnable() {
                    @Override
                    public void run() {
                        enableClipping(true);
                        if (finishRunnable != null) {
                            finishRunnable.run();
                        }
                    }
                });
        return true;
    }

    private void enableClipping(boolean enable) {
    	// add by wxue 20170407 start
    	mFrameContainer.setClipToPadding(enable);
    	mFrameContainer.setClipChildren(enable);
    	// add by wxue 20170407 end
        mContainer.setClipToPadding(enable);
        mContainer.setClipChildren(enable);
        mRow1.setClipToPadding(enable);
        mRow2.setClipToPadding(enable);
        mRow3.setClipToPadding(enable);
        setClipChildren(enable);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * M: add for voice unlock
     *    display prompt message when voice unlock is disabled because of
     *    media is playing in background.
    **/
    @Override
    public void onResume(int reason) {
        super.onResume(reason);
        final boolean mediaPlaying = AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC, 0) ;
//                || AudioSystem.isStreamActive(AudioSystem.STREAM_FM, 0);
        if (mLockPatternUtils.usingVoiceWeak() && mediaPlaying) {
            mSecurityMessageDisplay.setMessage(R.string.voice_unlock_media_playing, true);
        }
    }
}