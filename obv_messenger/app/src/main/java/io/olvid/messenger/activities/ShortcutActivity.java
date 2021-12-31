/*
 *  Olvid for Android
 *  Copyright © 2019-2021 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.messenger.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.olvid.engine.Logger;
import io.olvid.engine.engine.types.JsonIdentityDetails;
import io.olvid.messenger.App;
import io.olvid.messenger.AppSingleton;
import io.olvid.messenger.R;
import io.olvid.messenger.customClasses.EmptyRecyclerView;
import io.olvid.messenger.customClasses.InitialView;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.DiscussionDao;
import io.olvid.messenger.databases.entity.Contact;
import io.olvid.messenger.databases.entity.Discussion;
import io.olvid.messenger.databases.entity.Group;
import io.olvid.messenger.databases.entity.OwnedIdentity;
import io.olvid.messenger.discussion.DiscussionActivity;
import io.olvid.messenger.fragments.FilteredDiscussionListFragment;
import io.olvid.messenger.fragments.dialog.OwnedIdentitySelectionDialogFragment;
import io.olvid.messenger.main.MainActivity;
import io.olvid.messenger.services.UnifiedForegroundService;
import io.olvid.messenger.settings.SettingsActivity;
import io.olvid.messenger.viewModels.FilteredDiscussionListViewModel;


public class ShortcutActivity extends AppCompatActivity {
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean biometryAvailable = false;
    private boolean keyWiped = false;

    private EditText pinInput;
    private ImageButton fingerprintButton;
    private TextView biometryDisabledTextview;
    private UnlockEventBroadcastReceiver unlockEventBroadcastReceiver = null;
    private boolean openBiometricsOnNextWindowFocus = false;

    private InitialView currentIdentityInitialView;
    private TextView currentNameTextView;
    private TextView currentNameSecondLineTextView;
    private ImageView currentIdentityMutedImageView;
    private View separator;
    private OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter adapter;
    private PopupWindow popupWindow;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SettingsActivity.preventScreenCapture()) {
            Window window = getWindow();
            if (window != null) {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
            }
        }

        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);

            setContentView(R.layout.activity_lock_screen);

            unlockEventBroadcastReceiver = new UnlockEventBroadcastReceiver();
            LocalBroadcastManager.getInstance(this).registerReceiver(unlockEventBroadcastReceiver, new IntentFilter(UnifiedForegroundService.LockSubService.APP_UNLOCKED_BROADCAST_ACTION));

            pinInput = findViewById(R.id.pin_input);
            fingerprintButton = findViewById(R.id.fingerprint_icon);
            ImageView okButton = findViewById(R.id.button_ok);
            biometryDisabledTextview = findViewById(R.id.biometry_disabled_textview);

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    validatePIN();
                }
            };
            pinInput.addTextChangedListener(textWatcher);
            pinInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if (!validatePIN()) {
                        Animation shakeAnimation = AnimationUtils.loadAnimation(this, R.anim.shake);
                        pinInput.startAnimation(shakeAnimation);
                        pinInput.setSelection(0, pinInput.getText().length());
                    }
                    return true;
                }
                return true;
            });
            fingerprintButton.setOnClickListener(v -> openBiometricPrompt());
            okButton.setOnClickListener(v -> {
                if (!validatePIN()) {
                    Animation shakeAnimation = AnimationUtils.loadAnimation(this, R.anim.shake);
                    pinInput.startAnimation(shakeAnimation);
                    pinInput.setSelection(0, pinInput.getText().length());
                }
            });

            biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    if (biometryAvailable && !keyWiped && SettingsActivity.useBiometryToUnlock()) {
                        unlock();
                    }
                }
            });

            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.dialog_title_unlock_olvid))
                    .setNegativeButtonText(getString(R.string.button_label_cancel))
                    .setConfirmationRequired(false)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build();
        } else {
            Intent intent = getIntent();
            if (intent == null || intent.getAction() == null) {
                intentFail();
                return;
            }

            if (!Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction())) {
                intentFail();
                return;
            }

            getDelegate().setLocalNightMode(AppCompatDelegate.getDefaultNightMode());
            setContentView(R.layout.activity_shortcut);

            currentIdentityInitialView = findViewById(R.id.current_identity_initial_view);
            currentNameTextView = findViewById(R.id.current_identity_name_text_view);
            currentNameSecondLineTextView = findViewById(R.id.current_identity_name_second_line_text_view);
            currentIdentityMutedImageView = findViewById(R.id.current_identity_muted_marker_image_view);
            separator = findViewById(R.id.separator);

            final EditText contactNameFilter = findViewById(R.id.discussion_filter);
            findViewById(R.id.button_cancel).setOnClickListener(v -> finish());
            TextView switchProfileButton = findViewById(R.id.button_switch_profile);
            switchProfileButton.setOnClickListener(v -> openSwitchProfilePopup());
            switchProfileButton.setOnLongClickListener(v -> {
                new OpenHiddenProfileDialog(this);
                return true;
            });

            LiveData<List<DiscussionDao.DiscussionAndContactDisplayNames>> unfilteredDiscussions = Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> {
                bindOwnedIdentity(ownedIdentity);
                if (ownedIdentity == null) {
                    return null;
                } else {
                    return AppDatabase.getInstance().discussionDao().getAllWithContactNames(ownedIdentity.bytesOwnedIdentity, getString(R.string.text_contact_names_separator));
                }
            });

            adapter = new OwnedIdentitySelectionDialogFragment.OwnedIdentityListAdapter(getLayoutInflater(), bytesOwnedIdentity -> {
                if (popupWindow != null) {
                    popupWindow.dismiss();
                }
                AppSingleton.getInstance().selectIdentity(bytesOwnedIdentity, null);
            });
            Transformations.switchMap(AppSingleton.getCurrentIdentityLiveData(), (OwnedIdentity ownedIdentity) -> AppDatabase.getInstance().ownedIdentityDao().getAllNotHiddenExceptOne(ownedIdentity == null ? null : ownedIdentity.bytesOwnedIdentity)).observe(this, adapter);



            FilteredDiscussionListFragment filteredDiscussionListFragment = new FilteredDiscussionListFragment();
            filteredDiscussionListFragment.setUnfilteredDiscussions(unfilteredDiscussions);
            filteredDiscussionListFragment.setDiscussionFilterEditText(contactNameFilter);
            filteredDiscussionListFragment.setOnClickDelegate((View view, FilteredDiscussionListViewModel.SearchableDiscussion searchableDiscussion) -> App.runThread(() -> proceed(searchableDiscussion)));

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.filtered_discussion_list_placeholder, filteredDiscussionListFragment);
            transaction.commit();
        }
    }

    private void bindOwnedIdentity(OwnedIdentity ownedIdentity) {
        if (currentIdentityInitialView == null || currentNameTextView == null || currentNameSecondLineTextView == null || currentIdentityMutedImageView == null) {
            return;
        }

        if (ownedIdentity == null) {
            currentIdentityInitialView.setKeycloakCertified(false);
            currentIdentityInitialView.setInactive(false);
            currentIdentityInitialView.setInitial(new byte[0], " ");
            currentIdentityMutedImageView.setVisibility(View.GONE);
            return;
        }

        if (ownedIdentity.customDisplayName != null) {
            currentNameTextView.setText(ownedIdentity.customDisplayName);
            JsonIdentityDetails identityDetails = ownedIdentity.getIdentityDetails();
            currentNameSecondLineTextView.setVisibility(View.VISIBLE);
            if (identityDetails != null) {
                currentNameSecondLineTextView.setText(identityDetails.formatDisplayName(JsonIdentityDetails.FORMAT_STRING_FIRST_LAST_POSITION_COMPANY, SettingsActivity.getUppercaseLastName()));
            } else {
                currentNameSecondLineTextView.setText(ownedIdentity.displayName);
            }
        } else {
            JsonIdentityDetails identityDetails = ownedIdentity.getIdentityDetails();
            if (identityDetails != null) {
                currentNameTextView.setText(identityDetails.formatFirstAndLastName(SettingsActivity.getContactDisplayNameFormat(), SettingsActivity.getUppercaseLastName()));

                String posComp = identityDetails.formatPositionAndCompany(SettingsActivity.getContactDisplayNameFormat());
                if (posComp != null) {
                    currentNameSecondLineTextView.setVisibility(View.VISIBLE);
                    currentNameSecondLineTextView.setText(posComp);
                } else {
                    currentNameSecondLineTextView.setVisibility(View.GONE);
                }
            } else {
                currentNameTextView.setText(ownedIdentity.displayName);
                currentNameSecondLineTextView.setVisibility(View.GONE);
                currentNameSecondLineTextView.setText(null);
            }
        }
        currentIdentityInitialView.setInactive(!ownedIdentity.active);
        currentIdentityInitialView.setKeycloakCertified(ownedIdentity.keycloakManaged);
        if (ownedIdentity.photoUrl != null) {
            currentIdentityInitialView.setPhotoUrl(ownedIdentity.bytesOwnedIdentity, ownedIdentity.photoUrl);
        } else {
            currentIdentityInitialView.setInitial(ownedIdentity.bytesOwnedIdentity, App.getInitial(ownedIdentity.getCustomDisplayName()));
        }
        if (ownedIdentity.shouldMuteNotifications()) {
            currentIdentityMutedImageView.setVisibility(View.VISIBLE);
        } else {
            currentIdentityMutedImageView.setVisibility(View.GONE);
        }
    }

    private void openSwitchProfilePopup() {
        if (separator == null || adapter == null) {
            return;
        }
        View popupView = getLayoutInflater().inflate(R.layout.popup_switch_owned_identity, null);
        popupWindow = new PopupWindow(popupView, separator.getWidth(), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(12);
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.background_half_rounded_dialog));
        popupWindow.setOnDismissListener(() -> popupWindow = null);

        EmptyRecyclerView ownedIdentityListRecyclerView = popupView.findViewById(R.id.owned_identity_list_recycler_view);
        ownedIdentityListRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        ownedIdentityListRecyclerView.setAdapter(adapter);
        ownedIdentityListRecyclerView.setEmptyView(popupView.findViewById(R.id.empty_view));

        popupWindow.setAnimationStyle(R.style.FadeInAndOutPopupAnimation);
        popupWindow.showAsDropDown(separator);
    }

    private static class OpenHiddenProfileDialog extends io.olvid.messenger.customClasses.OpenHiddenProfileDialog {
        public OpenHiddenProfileDialog(@NonNull FragmentActivity activity) {
            super(activity);
        }

        @Override
        protected void onHiddenIdentityPasswordEntered(byte[] byteOwnedIdentity) {
            AppSingleton.getInstance().selectIdentity(byteOwnedIdentity, null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unlockEventBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(unlockEventBroadcastReceiver);
            unlockEventBroadcastReceiver = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            moveTaskToBack(true);
        } else {
            finish();
        }
    }

    private void configureInputForPINOrPassword() {
        if (pinInput != null && biometryDisabledTextview != null) {
            pinInput.setText(null);
            if (SettingsActivity.isPINAPassword()) {
                pinInput.setHint(R.string.hint_enter_password);
                pinInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                biometryDisabledTextview.setText(R.string.message_biometric_enrollment_detected_password);
            } else {
                pinInput.setHint(R.string.hint_enter_pin);
                pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                biometryDisabledTextview.setText(R.string.message_biometric_enrollment_detected_pin);
            }
        }
    }

    private boolean validatePIN() {
        String PIN = pinInput.getText().toString();
        if (SettingsActivity.verifyPIN(PIN)) {
            if (keyWiped) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    LockScreenActivity.generateFingerprintAdditionDetectionKey();
                }
            }
            unlock();
            return true;
        }
        return false;
    }

    private void unlock() {
        Intent unlockIntent = new Intent(this, UnifiedForegroundService.class);
        unlockIntent.setAction(UnifiedForegroundService.LockSubService.UNLOCK_APP_ACTION);
        unlockIntent.putExtra(UnifiedForegroundService.SUB_SERVICE_INTENT_EXTRA, UnifiedForegroundService.SUB_SERVICE_LOCK);
        startService(unlockIntent);
        // do not forward yet, wait for the unlock broadcast event to do this
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (UnifiedForegroundService.LockSubService.isApplicationLocked()) {
            configureInputForPINOrPassword();

            BiometricManager biometricManager = BiometricManager.from(this);
            biometryAvailable = BiometricManager.BIOMETRIC_SUCCESS == biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);

            if (biometryAvailable && SettingsActivity.useBiometryToUnlock()) {
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && LockScreenActivity.detectFingerprintAddition()) {
                    keyWiped = true;
                    fingerprintButton.setVisibility(View.GONE);
                    biometryDisabledTextview.setVisibility(View.VISIBLE);
                } else {
                    fingerprintButton.setVisibility(View.VISIBLE);
                    biometryDisabledTextview.setVisibility(View.GONE);
                    openBiometricsOnNextWindowFocus = true;
                }
            } else {
                fingerprintButton.setVisibility(View.GONE);
                biometryDisabledTextview.setVisibility(View.GONE);
            }
            pinInput.requestFocus();
        }
    }

    private void openBiometricPrompt() {
        biometricPrompt.authenticate(promptInfo);
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (openBiometricsOnNextWindowFocus) {
                openBiometricsOnNextWindowFocus = false;
                openBiometricPrompt();
            }
        }
    }

    class UnlockEventBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            recreate();
        }
    }


    private void intentFail() {
        App.toast(R.string.toast_message_shortcut_creation_failed, Toast.LENGTH_SHORT);
        finish();
    }

    private void proceed(final FilteredDiscussionListViewModel.SearchableDiscussion searchableDiscussion) {
        if (searchableDiscussion != null) {
            ShortcutInfoCompat.Builder builder;
            if (searchableDiscussion.isGroupDiscussion) {
                builder = getShortcutInfo(searchableDiscussion.discussionId, searchableDiscussion.byteIdentifier, null, searchableDiscussion.title);
            } else {
                builder = getShortcutInfo(searchableDiscussion.discussionId, null, searchableDiscussion.byteIdentifier, searchableDiscussion.title);
            }
            if (builder != null) {
                setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, builder.build()));
                runOnUiThread(this::finish);
                return;
            }
        }
        runOnUiThread(this::intentFail);
    }


    public static void updateShortcut(Discussion discussion) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutManager shortcutManager = (ShortcutManager) App.getContext().getSystemService(Context.SHORTCUT_SERVICE);
            if (shortcutManager != null) {
                ShortcutInfoCompat.Builder builder = getShortcutInfo(discussion.id, discussion.bytesGroupOwnerAndUid, discussion.bytesContactIdentity, discussion.title);
                if (builder != null) {
                    ShortcutInfo shortcutInfo = builder.build().toShortcutInfo();
                    shortcutManager.updateShortcuts(Collections.singletonList(shortcutInfo));
                }
            }
        }
    }

    public static ShortcutInfoCompat.Builder getShortcutInfo(long discussionId, byte[] bytesGroupOwnedAndUid, byte[] bytesContactIdentity, String title) {
        Discussion discussion = AppDatabase.getInstance().discussionDao().getById(discussionId);
        if (discussion == null) {
            return null;
        }

        Intent intent = new Intent(App.getContext(), MainActivity.class);
        intent.setAction(MainActivity.FORWARD_ACTION);
        intent.putExtra(MainActivity.FORWARD_TO_INTENT_EXTRA, DiscussionActivity.class.getName());
        intent.putExtra(DiscussionActivity.DISCUSSION_ID_INTENT_EXTRA, discussionId);
        intent.putExtra(MainActivity.HEX_STRING_BYTES_OWNED_IDENTITY_TO_SELECT_INTENT_EXTRA, Logger.toHexString(discussion.bytesOwnedIdentity));



        InitialView initialView = new InitialView(App.getContext());
        if (bytesGroupOwnedAndUid != null) {
            Group group = AppDatabase.getInstance().groupDao().get(discussion.bytesOwnedIdentity, bytesGroupOwnedAndUid);
            if (group != null && group.getCustomPhotoUrl() != null) {
                initialView.setPhotoUrl(bytesGroupOwnedAndUid, group.getCustomPhotoUrl());
            } else {
                initialView.setGroup(bytesGroupOwnedAndUid);
            }
        } else if (bytesContactIdentity != null) {
            Contact contact = AppDatabase.getInstance().contactDao().get(discussion.bytesOwnedIdentity, bytesContactIdentity);
            if (contact != null && contact.getCustomPhotoUrl() != null) {
                initialView.setPhotoUrl(bytesContactIdentity, contact.getCustomPhotoUrl());
            } else {
                initialView.setInitial(bytesContactIdentity, App.getInitial(title));
            }
        } else {
            if (discussion.photoUrl != null) {
                initialView.setLocked(true);
                initialView.setPhotoUrl(new byte[0], discussion.photoUrl);
            } else {
                initialView.setLocked(true);
                initialView.setInitial(new byte[0], "");
            }
        }
        Bitmap bitmap = initialView.getAdaptiveBitmap();

        return new ShortcutInfoCompat.Builder(App.getContext(), DiscussionActivity.SHORTCUT_PREFIX + discussionId)
                .setShortLabel(title)
                .setIcon(IconCompat.createWithAdaptiveBitmap(bitmap))
                .setIntent(intent);
    }


    // region Shortcut publication
    private static final String CATEGORY_SHARE_TARGET = "io.olvid.messenger.activities.ShareActivity.SHARE";

    private static Long[] publishedDiscussionIds;
    private static int MAX_SHORTCUTS = 5;

    public static void startPublishingShareTargets(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        if (!SettingsActivity.exposeRecentDiscussions()) {
            // if lock screen is on, publish an empty list
            ShortcutManagerCompat.removeAllDynamicShortcuts(context);
            return;
        }

        if (ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) > 0) {
            MAX_SHORTCUTS = Math.min(MAX_SHORTCUTS, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context));
        }
        publishedDiscussionIds = new Long[MAX_SHORTCUTS];

        new Handler(Looper.getMainLooper()).post(() ->
                AppDatabase.getInstance().discussionDao().getLatestDiscussionsInWhichYouWrote().observeForever(discussions -> {
                    boolean changed = false;
                    int position = 0;
                    for (Discussion discussion : discussions) {
                        if (position >= MAX_SHORTCUTS) {
                            break;
                        }
                        if (publishedDiscussionIds[position] == null || publishedDiscussionIds[position] != discussion.id) {
                            changed = true;
                            break;
                        }
                        position++;
                        if (position == MAX_SHORTCUTS) {
                            break;
                        }
                    }
                    if (!changed && position < MAX_SHORTCUTS && publishedDiscussionIds[position] != null) {
                        changed = true;
                    }
                    if (changed) {
                        App.runThread(() -> publishNewShareTargets(context, discussions));
                    }
                })
        );
    }

    private static void publishNewShareTargets(@NonNull Context context, List<Discussion> discussions) {
        ArrayList<ShortcutInfoCompat> shortcuts = new ArrayList<>();

        // Category that our sharing shortcuts will be assigned to
        Set<String> contactCategories = new HashSet<>();
        contactCategories.add(CATEGORY_SHARE_TARGET);

        int position = 0;
        for (Discussion discussion: discussions) {
            publishedDiscussionIds[position] = discussion.id;

            ShortcutInfoCompat.Builder builder = ShortcutActivity.getShortcutInfo(discussion.id, discussion.bytesGroupOwnerAndUid, discussion.bytesContactIdentity, discussion.title);
            if (builder != null) {
                builder.setLongLived(true)
                        .setCategories(contactCategories);
                shortcuts.add(builder.build());

                position++;
                if (position == MAX_SHORTCUTS) {
                    break;
                }
            }
        }
        for (; position < MAX_SHORTCUTS; position++) {
            publishedDiscussionIds[position] = null;
        }

        ShortcutManagerCompat.removeAllDynamicShortcuts(context);
        ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts);
    }
    // endregion
}