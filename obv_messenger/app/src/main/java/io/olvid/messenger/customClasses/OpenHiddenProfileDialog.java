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

package io.olvid.messenger.customClasses;


import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;
import java.util.List;

import io.olvid.messenger.App;
import io.olvid.messenger.R;
import io.olvid.messenger.databases.AppDatabase;
import io.olvid.messenger.databases.dao.OwnedIdentityDao;
import io.olvid.messenger.settings.SettingsActivity;

public abstract class OpenHiddenProfileDialog {
    @NonNull
    private final AlertDialog dialog;
    @Nullable
    private List<OwnedIdentityDao.OwnedIdentityPasswordAndSalt> ownedIdentityPasswordAndSalts;

    public OpenHiddenProfileDialog(@NonNull FragmentActivity activity) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_view_open_hidden_profile, null);
        TextInputEditText passwordEditText = dialogView.findViewById(R.id.password_text_view);
        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null) {
                    selectIdentityFromPassword(s.toString());
                }
            }
        });


        AlertDialog.Builder builder = new SecureAlertDialogBuilder(activity, R.style.CustomAlertDialog)
                .setView(dialogView)
                .setNegativeButton(R.string.button_label_cancel, null);

        dialog = builder.create();
        dialog.setOnShowListener(d -> {
            passwordEditText.requestFocus();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        dialog.show();

        ownedIdentityPasswordAndSalts = null;

        App.runThread(() -> {
            ownedIdentityPasswordAndSalts = AppDatabase.getInstance().ownedIdentityDao().getHiddenIdentityPasswordsAndSalts();
            activity.runOnUiThread(() -> {
                if (passwordEditText.getText() != null) {
                    selectIdentityFromPassword(passwordEditText.getText().toString());
                }
            });
        });
    }

    private void selectIdentityFromPassword(String password) {
        if (ownedIdentityPasswordAndSalts == null || password == null || password.length() == 0) {
            return;
        }
        for (OwnedIdentityDao.OwnedIdentityPasswordAndSalt ownedIdentityPasswordAndSalt : ownedIdentityPasswordAndSalts) {
            try {
                byte[] hash = SettingsActivity.computePINHash(password, ownedIdentityPasswordAndSalt.unlock_salt);
                if (Arrays.equals(ownedIdentityPasswordAndSalt.unlock_password, hash)) {
                    dialog.dismiss();
                    onHiddenIdentityPasswordEntered(ownedIdentityPasswordAndSalt.bytes_owned_identity);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    protected abstract void onHiddenIdentityPasswordEntered(byte[] byteOwnedIdentity);
}