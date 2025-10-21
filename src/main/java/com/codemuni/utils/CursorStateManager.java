package com.codemuni.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Stack;

/**
 * Centralized cursor state management to prevent conflicts and provide smooth UX.
 * Uses a stack-based approach where cursor states can be pushed/popped.
 * The top of the stack is always the active cursor state.
 */
public class CursorStateManager {
    private static final Log log = LogFactory.getLog(CursorStateManager.class);

    private static CursorStateManager instance;

    private final Stack<CursorState> cursorStack;
    private Component targetComponent;

    private CursorStateManager() {
        this.cursorStack = new Stack<>();
    }

    public static synchronized CursorStateManager getInstance() {
        if (instance == null) {
            instance = new CursorStateManager();
        }
        return instance;
    }

    /**
     * Sets the target component for cursor changes (usually the main frame).
     */
    public void setTargetComponent(Component component) {
        this.targetComponent = component;
        // Apply current cursor if stack not empty
        if (!cursorStack.isEmpty()) {
            applyCursor(cursorStack.peek());
        }
    }

    /**
     * Pushes a new cursor state onto the stack and applies it.
     *
     * @param type Cursor type (e.g., Cursor.WAIT_CURSOR)
     * @param reason Description of why this cursor is needed (for debugging)
     */
    public void pushCursor(int type, String reason) {
        CursorState state = new CursorState(type, reason);
        cursorStack.push(state);
        applyCursor(state);
        log.debug("Cursor pushed: " + reason + " (type: " + type + ") - Stack depth: " + cursorStack.size());
    }

    /**
     * Pops the top cursor state and restores the previous one.
     *
     * @param reason Description that should match the push reason (for validation)
     */
    public void popCursor(String reason) {
        if (cursorStack.isEmpty()) {
            log.warn("Attempted to pop cursor with reason: " + reason + " but stack is empty");
            return;
        }

        CursorState popped = cursorStack.pop();

        if (!popped.reason.equals(reason)) {
            log.warn("Cursor pop reason mismatch. Expected: " + popped.reason + ", Got: " + reason);
        }

        log.debug("Cursor popped: " + reason + " - Stack depth: " + cursorStack.size());

        // Restore previous cursor or default
        if (!cursorStack.isEmpty()) {
            applyCursor(cursorStack.peek());
        } else {
            applyCursor(new CursorState(Cursor.DEFAULT_CURSOR, "default"));
        }
    }

    /**
     * Clears all cursor states and resets to default.
     */
    public void reset() {
        cursorStack.clear();
        applyCursor(new CursorState(Cursor.DEFAULT_CURSOR, "reset"));
        log.debug("Cursor stack reset to default");
    }

    /**
     * Gets the current cursor type without modifying the stack.
     */
    public int getCurrentCursorType() {
        if (cursorStack.isEmpty()) {
            return Cursor.DEFAULT_CURSOR;
        }
        return cursorStack.peek().type;
    }

    /**
     * Checks if a specific cursor reason is currently active in the stack.
     */
    public boolean isCursorActive(String reason) {
        return cursorStack.stream().anyMatch(state -> state.reason.equals(reason));
    }

    /**
     * Applies the cursor to the target component and all its children recursively.
     */
    private void applyCursor(CursorState state) {
        if (targetComponent == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            Cursor cursor = Cursor.getPredefinedCursor(state.type);
            targetComponent.setCursor(cursor);

            // Recursively apply to all children
            applyCursorRecursive(targetComponent, cursor);
        });
    }

    /**
     * Recursively applies cursor to all child components.
     */
    private void applyCursorRecursive(Component component, Cursor cursor) {
        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                child.setCursor(cursor);
                applyCursorRecursive(child, cursor);
            }
        }
    }

    /**
     * Internal class to represent a cursor state with metadata.
     */
    private static class CursorState {
        final int type;
        final String reason;
        final long timestamp;

        CursorState(int type, String reason) {
            this.type = type;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return "CursorState{type=" + type + ", reason='" + reason + "', timestamp=" + timestamp + '}';
        }
    }
}
