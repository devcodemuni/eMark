package com.codemuni.gui;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;

/**
 * Custom CallbackHandler that shows a Swing dialog for PIN entry.
 * Supports cached PIN to avoid prompting user multiple times in the same session.
 */
public class SmartCardCallbackHandler implements CallbackHandler {

    private volatile String statusMessage;
    private boolean cancelled = false;
    private PasswordDialog dialog;
    private char[] cachedPin = null;  // Pre-filled PIN from cache
    private char[] enteredPin = null; // PIN entered by user (to be cached)

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (!(callback instanceof PasswordCallback)) {
                throw new UnsupportedCallbackException(callback,
                        "Unsupported Callback type: " + callback.getClass().getName());
            }

            PasswordCallback pc = (PasswordCallback) callback;

            // If we have cached PIN, use it directly (no dialog)
            if (cachedPin != null && cachedPin.length > 0) {
                pc.setPassword(cachedPin);
                return; // Skip dialog, use cached PIN
            }

            // No cached PIN - show dialog to user
            PasswordDialog dialog = createPasswordDialog();

            if (dialog.isConfirmed()) {
                String value = dialog.getValue();
                if (value == null || value.trim().isEmpty()) {
                    throw new IOException("Empty PIN not allowed");
                }
                char[] pin = value.toCharArray();
                pc.setPassword(pin);
                enteredPin = pin; // Store for caching
            } else {
                cancelled = true; // user cancelled
            }
        }
    }

    public void setStatusMessage(String message) {
        this.statusMessage = message;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets cached PIN to use instead of prompting user
     */
    public void setCachedPin(char[] pin) {
        this.cachedPin = pin;
    }

    /**
     * Gets the PIN that was entered by the user (for caching)
     */
    public char[] getEnteredPin() {
        return enteredPin;
    }

    private PasswordDialog createPasswordDialog() {
        PasswordDialog dialog = new PasswordDialog(
                null,
                "Smartcard Authentication",
                buildMessage(),
                "Enter PIN",
                "Authenticate",
                "Cancel"
        );
        dialog.setValidator(value -> value != null && value.trim().length() >= 2);
        dialog.setVisible(true); // blocks until closed
        return dialog;
    }

    private String buildMessage() {
        return (statusMessage != null && !statusMessage.isEmpty())
                ? statusMessage
                : "Please enter your PIN:";
    }
}
