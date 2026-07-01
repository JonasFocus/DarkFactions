package com.darkfactions.managers;

// Implemented by managers that cache ConfigManager values and need to
// refresh them on /f admin reload.
public interface Reloadable {

    void reloadConfig();
}
