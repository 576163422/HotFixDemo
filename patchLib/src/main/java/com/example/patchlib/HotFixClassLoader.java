package com.example.patchlib;

import dalvik.system.PathClassLoader;

public class HotFixClassLoader extends PathClassLoader {
    public HotFixClassLoader(String dexPath, ClassLoader parent) {
        super(dexPath, parent);
    }

    public HotFixClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
        super(dexPath, librarySearchPath, parent);
    }
}