// WaeIIFace.aidl
package com.wmods.wppenhacer.xposed.bridge;

import android.os.ParcelFileDescriptor;
import java.util.List;

// Declare any non-default types here with import statements

interface WaeIIFace {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    ParcelFileDescriptor openFile(String path, boolean create);

    boolean createDir(String path);

    List listFiles(String path);

    boolean exists(String path);

    /** Deletes a file in shared storage. */
    boolean deleteFile(String path);

    /** Renames a file in shared storage, replacing the target if it exists. */
    boolean renameFile(String from, String to);

}