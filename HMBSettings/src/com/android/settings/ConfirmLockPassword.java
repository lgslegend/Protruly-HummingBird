/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.TextViewInputDisabler;
import com.android.settings.widget.DigitalPasswordInputView;
import com.android.settings.widget.keyboard.PinPasswordTextView;
import com.android.settingslib.animation.AppearAnimationUtils;
import com.android.settingslib.animation.DisappearAnimationUtils;

import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import java.util.ArrayList;

public class ConfirmLockPassword extends ConfirmDeviceCredentialBaseActivity {
    final static String TAG = "ConfirmLockPassword";
    public static class InternalActivity extends ConfirmLockPassword {
    }

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, ConfirmLockPasswordFragment.class.getName());
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (ConfirmLockPasswordFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Fragment fragment = getFragmentManager().findFragmentById(R.id.main_content);
        if (fragment != null && fragment instanceof ConfirmLockPasswordFragment) {
            ((ConfirmLockPasswordFragment)fragment).onWindowFocusChanged(hasFocus);
        }
    }

    public static class ConfirmLockPasswordFragment extends ConfirmDeviceCredentialBaseFragment
            implements OnClickListener, OnEditorActionListener, TextWatcher {
        private static final String KEY_NUM_WRONG_CONFIRM_ATTEMPTS
                = "confirm_lock_password_fragment.key_num_wrong_confirm_attempts";
        private static final long ERROR_MESSAGE_TIMEOUT = 3000;
        private TextView mPasswordEntry;
        private TextViewInputDisabler mPasswordEntryInputDisabler;
        private LockPatternUtils mLockPatternUtils;
        private AsyncTask<?, ?, ?> mPendingLockCheck;
        private TextView mHeaderTextView;
        private TextView mDetailsTextView;
        private TextView mErrorTextView;
        private Handler mHandler = new Handler();
        private int mNumWrongConfirmAttempts;
        private CountDownTimer mCountdownTimer;
        private boolean mIsAlpha;
        private InputMethodManager mImm;
        private boolean mUsingFingerprint = false;
        private AppearAnimationUtils mAppearAnimationUtils;
        private DisappearAnimationUtils mDisappearAnimationUtils;
        private boolean mBlockImm;
        private int mEffectiveUserId;

        private DigitalPasswordInputView mDigitalPasswordInputView;

        // required constructor for fragments
        public ConfirmLockPasswordFragment() {

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mLockPatternUtils = new LockPatternUtils(getActivity());
            mEffectiveUserId = Utils.getEffectiveUserId(getActivity());

            if (savedInstanceState != null) {
                mNumWrongConfirmAttempts = savedInstanceState.getInt(
                        KEY_NUM_WRONG_CONFIRM_ATTEMPTS, 0);
            }

            final int storedQuality = mLockPatternUtils.getKeyguardStoredPasswordQuality(
                    mEffectiveUserId);
            mIsAlpha = DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC == storedQuality
                    || DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC == storedQuality
                    || DevicePolicyManager.PASSWORD_QUALITY_COMPLEX == storedQuality;
            if (!mIsAlpha && LocalSettings.Enabler.ENABLE_CUSTOM_NUM_KEYBOARD) {
                getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            CharSequence detailsMessage = "";

            View view = inflater.inflate(R.layout.confirm_lock_password, null);

            mPasswordEntry = (TextView) view.findViewById(R.id.password_entry);
            mPasswordEntry.setOnEditorActionListener(this);
            mPasswordEntryInputDisabler = new TextViewInputDisabler(mPasswordEntry);

            mHeaderTextView = (TextView) view.findViewById(R.id.headerText);
            mDetailsTextView = (TextView) view.findViewById(R.id.detailsText);
            mErrorTextView = (TextView) view.findViewById(R.id.errorText);

            mImm = (InputMethodManager) getActivity().getSystemService(
                    Context.INPUT_METHOD_SERVICE);

            Intent intent = getActivity().getIntent();
            if (intent != null) {
                CharSequence headerMessage = intent.getCharSequenceExtra(
                        ConfirmDeviceCredentialBaseFragment.HEADER_TEXT);
                detailsMessage = intent.getCharSequenceExtra(
                        ConfirmDeviceCredentialBaseFragment.DETAILS_TEXT);
                if (TextUtils.isEmpty(headerMessage)) {
                    headerMessage = getString(getDefaultHeader());
                }
                if (TextUtils.isEmpty(detailsMessage)) {
                    detailsMessage = getString(getDefaultDetails());
                }
                mHeaderTextView.setText(headerMessage);
                mDetailsTextView.setText(detailsMessage);
            }
            int currentType = mPasswordEntry.getInputType();
            mPasswordEntry.setInputType(mIsAlpha ? currentType
                    : (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));
            mAppearAnimationUtils = new AppearAnimationUtils(getContext(),
                    220, 2f /* translationScale */, 1f /* delayScale*/,
                    AnimationUtils.loadInterpolator(getContext(),
                            android.R.interpolator.linear_out_slow_in));
            mDisappearAnimationUtils = new DisappearAnimationUtils(getContext(),
                    110, 1f /* translationScale */,
                    0.5f /* delayScale */, AnimationUtils.loadInterpolator(
                            getContext(), android.R.interpolator.fast_out_linear_in));
            setAccessibilityTitle(mHeaderTextView.getText());

            mDigitalPasswordInputView = (DigitalPasswordInputView) view.findViewById(R.id.digital_password_input);
            mDigitalPasswordInputView.setEditText(mPasswordEntry);
            if (!mIsAlpha) {
                mPasswordEntry.setBackground(null);
                mPasswordEntry.setHeight(0);
                mDigitalPasswordInputView.setVisibility(View.VISIBLE);
            }

            mPasswordEntry.addTextChangedListener(this);
            view = initCustomKeyboardLayout(inflater, container, view, detailsMessage);

            return view;
        }

        private int getDefaultHeader() {
            return mIsAlpha ? R.string.lockpassword_confirm_your_password_header
                    : R.string.lockpassword_confirm_your_pin_header;
        }

        private int getDefaultDetails() {
            return mIsAlpha ? R.string.lockpassword_confirm_your_password_generic
                    : R.string.lockpassword_confirm_your_pin_generic;
        }

        private int getErrorMessage() {
            return mIsAlpha ? R.string.lockpassword_invalid_password
                    : R.string.lockpassword_invalid_pin;
        }

        @Override
        public void prepareEnterAnimation() {
            super.prepareEnterAnimation();
            mHeaderTextView.setAlpha(0f);
            mDetailsTextView.setAlpha(0f);
            mCancelButton.setAlpha(0f);
            mPasswordEntry.setAlpha(0f);
            mFingerprintIcon.setAlpha(0f);
            mBlockImm = true;
        }

        private View[] getActiveViews() {
            ArrayList<View> result = new ArrayList<>();
            result.add(mHeaderTextView);
            result.add(mDetailsTextView);
            if (mCancelButton.getVisibility() == View.VISIBLE) {
                result.add(mCancelButton);
            }
            result.add(mPasswordEntry);
            if (mFingerprintIcon.getVisibility() == View.VISIBLE) {
                result.add(mFingerprintIcon);
            }
            return result.toArray(new View[] {});
        }

        @Override
        public void startEnterAnimation() {
            super.startEnterAnimation();
            mAppearAnimationUtils.startAnimation(getActiveViews(), new Runnable() {
                @Override
                public void run() {
                    mBlockImm = false;
                    resetState();
                }
            });
        }

        @Override
        public void onPause() {
            super.onPause();
            if (mCountdownTimer != null) {
                mCountdownTimer.cancel();
                mCountdownTimer = null;
                mErrorTextView.setText("");
            }
            if (mPendingLockCheck != null) {
                mPendingLockCheck.cancel(false);
                mPendingLockCheck = null;
            }
        }

        @Override
        protected int getMetricsCategory() {
            return MetricsLogger.CONFIRM_LOCK_PASSWORD;
        }

        @Override
        public void onResume() {
            super.onResume();
            long deadline = mLockPatternUtils.getLockoutAttemptDeadline(mEffectiveUserId);
            if (deadline != 0) {
                handleAttemptLockout(deadline);
            } else {
                resetState();
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(KEY_NUM_WRONG_CONFIRM_ATTEMPTS, mNumWrongConfirmAttempts);
        }

        @Override
        protected void authenticationSucceeded() {
            startDisappearAnimation(new Intent());
        }

        @Override
        public void onFingerprintIconVisibilityChanged(boolean visible) {
            mUsingFingerprint = visible;
        }

        private void resetState() {
            if (mBlockImm) return;
            mPasswordEntry.setEnabled(true);
            mPasswordEntryInputDisabler.setInputEnabled(true);
            if (shouldAutoShowSoftKeyboard()) {
                mImm.showSoftInput(mPasswordEntry, InputMethodManager.SHOW_IMPLICIT);
            }
        }

        private boolean shouldAutoShowSoftKeyboard() {
            return mPasswordEntry.isEnabled() && !mUsingFingerprint;
        }

        public void onWindowFocusChanged(boolean hasFocus) {
            if (!hasFocus || mBlockImm) {
                return;
            }
            // Post to let window focus logic to finish to allow soft input show/hide properly.
            mPasswordEntry.post(new Runnable() {
                @Override
                public void run() {
                    if (shouldAutoShowSoftKeyboard()) {
                        resetState();
                        return;
                    }

                    mImm.hideSoftInputFromWindow(mPasswordEntry.getWindowToken(),
                            InputMethodManager.HIDE_IMPLICIT_ONLY);
                }
            });
        }

        private void handleNext() {
            /// M: ALPS01849630 {@
            if (getActivity() == null) {
                Log.e(TAG, "error,getActivity() is null");
                return;
            }
            final String pin = mPasswordEntry.getText().toString();
            if (TextUtils.isEmpty(pin)) {
                return;
            }

            /// @}
            mPasswordEntryInputDisabler.setInputEnabled(false);
            if (mPendingLockCheck != null) {
                mPendingLockCheck.cancel(false);
            }

            final boolean verifyChallenge = getActivity().getIntent().getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, false);
            Intent intent = new Intent();
            if (verifyChallenge)  {
                if (isInternalActivity()) {
                    startVerifyPassword(pin, intent);
                    return;
                }
            } else {
                startCheckPassword(pin, intent);
                return;
            }

            onPasswordChecked(false, intent, 0, mEffectiveUserId);
        }

        private boolean isInternalActivity() {
            return getActivity() instanceof ConfirmLockPassword.InternalActivity;
        }

        private void startVerifyPassword(final String pin, final Intent intent) {
            long challenge = getActivity().getIntent().getLongExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, 0);
            final int localEffectiveUserId = mEffectiveUserId;
            mPendingLockCheck = LockPatternChecker.verifyPassword(
                    mLockPatternUtils,
                    pin,
                    challenge,
                    localEffectiveUserId,
                    new LockPatternChecker.OnVerifyCallback() {
                        @Override
                        public void onVerified(byte[] token, int timeoutMs) {
                            mPendingLockCheck = null;
                            boolean matched = false;
                            if (token != null) {
                                matched = true;
                                intent.putExtra(
                                        ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN,
                                        token);
                            }
                            onPasswordChecked(matched, intent, timeoutMs, localEffectiveUserId);
                        }
                    });
        }

        private void startCheckPassword(final String pin, final Intent intent) {
            final int localEffectiveUserId = mEffectiveUserId;
            mPendingLockCheck = LockPatternChecker.checkPassword(
                    mLockPatternUtils,
                    pin,
                    localEffectiveUserId,
                    new LockPatternChecker.OnCheckCallback() {
                        @Override
                        public void onChecked(boolean matched, int timeoutMs) {
                            mPendingLockCheck = null;
                            if (matched && isInternalActivity()) {
                                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_TYPE,
                                                mIsAlpha ? StorageManager.CRYPT_TYPE_PASSWORD
                                                         : StorageManager.CRYPT_TYPE_PIN);
                                intent.putExtra(
                                        ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, pin);
                            }
                            onPasswordChecked(matched, intent, timeoutMs, localEffectiveUserId);
                        }
                    });
        }

        private void startDisappearAnimation(final Intent intent) {
            if (getActivity().getThemeResId() == R.style.Theme_ConfirmDeviceCredentialsDark) {
                mDisappearAnimationUtils.startAnimation(getActiveViews(), new Runnable() {
                    @Override
                    public void run() {
                        getActivity().setResult(RESULT_OK, intent);
                        getActivity().finish();
                        getActivity().overridePendingTransition(
                                R.anim.confirm_credential_close_enter,
                                R.anim.confirm_credential_close_exit);
                    }
                });
            } else {
                getActivity().setResult(RESULT_OK, intent);
                getActivity().finish();
            }
        }

        private void onPasswordChecked(boolean matched, Intent intent, int timeoutMs,
                int effectiveUserId) {
            mPasswordEntryInputDisabler.setInputEnabled(true);
            if (matched) {
                startDisappearAnimation(intent);
            } else {
                if (timeoutMs > 0) {
                    long deadline = mLockPatternUtils.setLockoutAttemptDeadline(
                            effectiveUserId, timeoutMs);
                    handleAttemptLockout(deadline);
                } else {
                    showError(getErrorMessage());
                }
            }
        }

        private void handleAttemptLockout(long elapsedRealtimeDeadline) {
            long elapsedRealtime = SystemClock.elapsedRealtime();
            mPasswordEntry.setEnabled(false);
            mCountdownTimer = new CountDownTimer(
                    elapsedRealtimeDeadline - elapsedRealtime,
                    LockPatternUtils.FAILED_ATTEMPT_COUNTDOWN_INTERVAL_MS) {

                @Override
                public void onTick(long millisUntilFinished) {
                    ///M: CR ALPS01808515. Avoid fragment did not attach to activity.
                    if (!isAdded()) return;
                    final int secondsCountdown = (int) (millisUntilFinished / 1000);
                    showError(getString(
                            R.string.lockpattern_too_many_failed_confirmation_attempts,
                            secondsCountdown), 0);
                }

                @Override
                public void onFinish() {
                    resetState();
                    mErrorTextView.setText("");
                    mNumWrongConfirmAttempts = 0;
                }
            }.start();
        }

        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.next_button:
                    handleNext();
                    break;

                case R.id.cancel_button:
                    getActivity().setResult(RESULT_CANCELED);
                    getActivity().finish();
                    break;
            }
        }

        private void showError(int msg) {
            showError(msg, ERROR_MESSAGE_TIMEOUT);
        }

        private final Runnable mResetErrorRunnable = new Runnable() {
            public void run() {
                mErrorTextView.setText("");
            }
        };

        private void showError(CharSequence msg, long timeout) {
            mErrorTextView.setText(msg);
            mPasswordEntry.setText(null);
            mHandler.removeCallbacks(mResetErrorRunnable);
            if (timeout != 0) {
                mHandler.postDelayed(mResetErrorRunnable, timeout);
            }
        }

        private void showError(int msg, long timeout) {
            showError(getText(msg), timeout);
        }

        // {@link OnEditorActionListener} methods.
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            // Check if this was the result of hitting the enter or "done" key
            if (actionId == EditorInfo.IME_NULL
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_NEXT) {
                handleNext();
                return true;
            }
            return false;
        }

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            int maxLength = mIsAlpha ?
                    LocalSettings.PASSWORD_MAX_LENGTH : LocalSettings.DIGITAL_PASSWORD_MAX_LENGTH;
            int length = mPasswordEntry.length();
            if(length > maxLength) {
                Editable editable = (Editable) mPasswordEntry.getText();
                String str = editable.toString();
                String newStr = str.substring(0, maxLength);
                mPasswordEntry.setText(newStr);
                editable = (Editable) mPasswordEntry.getText();
                Selection.setSelection(editable, maxLength);
            }

            if (!mIsAlpha) {
                length = mPasswordEntry.length();
                mDigitalPasswordInputView.updateInputCount(
                        length <= LocalSettings.DIGITAL_PASSWORD_MAX_LENGTH ?
                                length : LocalSettings.DIGITAL_PASSWORD_MAX_LENGTH);
                if (length == LocalSettings.DIGITAL_PASSWORD_MAX_LENGTH) {
                    handleNext();
                }
            }
        }

        @Override
        public void afterTextChanged(Editable editable) {

        }

        private View initCustomKeyboardLayout(LayoutInflater inflater, ViewGroup container,
                                              View view, CharSequence detailsMessage) {
            if (!mIsAlpha && LocalSettings.Enabler.ENABLE_CUSTOM_NUM_KEYBOARD) {
                View tempView = view;
                view = inflater.inflate(R.layout.hb_keyguard_pin_view, container, false);
                view.findViewById(R.id.comfirm_button).setVisibility(View.INVISIBLE);
                ViewGroup compatContainer = (ViewGroup)view.findViewById(R.id.layout_compat_container);
                compatContainer.addView(tempView);

                mHeaderTextView = (TextView)view.findViewById(R.id.headerText);
                mHeaderTextView.setText(detailsMessage);
                mErrorTextView = (TextView) view.findViewById(R.id.errText);
                mErrorTextView.setVisibility(View.VISIBLE);

                mPasswordEntry = (PinPasswordTextView) view.findViewById(R.id.pinEntry);
                mPasswordEntry.setOnEditorActionListener(this);
                ((PinPasswordTextView)mPasswordEntry).setTextWatcher(this);
            }
            return view;
        }
    }
}
