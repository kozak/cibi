package com.cibi.connection;

/**
 * @author morswin
 */
public enum ConnectionAction {
    ACTION_START, ACTION_STOP, ACTION_KEEP_ALIVE, ACTION_RECONNECT,
    ACTION_MSG, ACTION_ADD_LISTENER, ACTION_REMOVE_LISTENER;


    public static final int START = 0;
    public static final int STOP = 1;
    public static final int KEEP_ALIVE = 2;
    public static final int RECONNECT = 3;
    public static final int MSG = 4;
    public static final int ADD_LISTENER = 5;
    public static final int REMOVE_LISTENER = 6;

    public int toInteger() {
        return this.ordinal();
    }

    public static ConnectionAction getAction(int a) {
        return ConnectionAction.values()[a];
    }

}
