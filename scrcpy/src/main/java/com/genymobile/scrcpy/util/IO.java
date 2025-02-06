package com.genymobile.scrcpy.util;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.BuildConfig;

import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Scanner;

public final class IO {
    private IO() {
        // not instantiable
    }

    private static int write(OutputStream outputStream, ByteBuffer buffer) throws IOException {
        // 确保 ByteBuffer 有数据
        if (buffer.hasRemaining()) {
            // 如果 ByteBuffer 有 backing array，可以直接使用 array()
            if (buffer.hasArray()) {
                int remaining = buffer.remaining();
                outputStream.write(buffer.array(), buffer.arrayOffset() + buffer.position(), remaining);
                return remaining;
            } else {
                // 如果没有 backing array，手动复制数据到字节数组
                int remaining = buffer.remaining();
                byte[] data = new byte[remaining];
                buffer.get(data);
                outputStream.write(data);
                return remaining;
            }
        }
        return 0;
//        while (true) {
//            try {
//                return Os.write(fd, from);
//            } catch (ErrnoException e) {
//                if (e.errno != OsConstants.EINTR) {
//                    throw new IOException(e);
//                }
//            }
//        }
    }

    public static void writeFully(OutputStream fd, ByteBuffer from) throws IOException {
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_23_ANDROID_6_0) {
            while (from.hasRemaining()) {
                write(fd, from);
            }
        } else {
            // ByteBuffer position is not updated as expected by Os.write() on old Android versions, so
            // handle the position and the remaining bytes manually.
            // See <https://github.com/Genymobile/scrcpy/issues/291>.
            int position = from.position();
            int remaining = from.remaining();
            while (remaining > 0) {
                int w = write(fd, from);
                if (BuildConfig.DEBUG && w < 0) {
                    // w should not be negative, since an exception is thrown on error
                    throw new AssertionError("Os.write() returned a negative value (" + w + ")");
                }
                remaining -= w;
                position += w;
                from.position(position);
            }
        }
    }

    public static void writeFully(OutputStream fd, byte[] buffer, int offset, int len) throws IOException {
//        writeFully(fd, ByteBuffer.wrap(buffer, offset, len));
        fd.write(buffer, offset, len);
    }

    public static String toString(InputStream inputStream) {
        StringBuilder builder = new StringBuilder();
        Scanner scanner = new Scanner(inputStream);
        while (scanner.hasNextLine()) {
            builder.append(scanner.nextLine()).append('\n');
        }
        return builder.toString();
    }

    public static boolean isBrokenPipe(IOException e) {
        Throwable cause = e.getCause();
        return cause instanceof ErrnoException && ((ErrnoException) cause).errno == OsConstants.EPIPE;
    }

    public static boolean isBrokenPipe(Exception e) {
        return e instanceof IOException && isBrokenPipe((IOException) e);
    }
}
