package com.github.control.scrcpy;

public final class Binary {
    private Binary() {
        // not instantiable
    }

    public static int toUnsigned(short value) {
        return value & 0xffff;
    }

    public static int toUnsigned(byte value) {
        return value & 0xff;
    }

    /**
     * Convert unsigned 16-bit fixed-point to a float between 0 and 1
     *
     * @param value encoded value
     * @return Float value between 0 and 1
     */
    public static float u16FixedPointToFloat(short value) {
        int unsignedShort = Binary.toUnsigned(value);
        // 0x1p16f is 2^16 as float
        return unsignedShort == 0xffff ? 1f : (unsignedShort / 0x1p16f);
    }

    /**
     * Convert signed 16-bit fixed-point to a float between -1 and 1
     *
     * @param value encoded value
     * @return Float value between -1 and 1
     */
    public static float i16FixedPointToFloat(short value) {
        // 0x1p15f is 2^15 as float
        return value == 0x7fff ? 1f : (value / 0x1p15f);
    }

    /**
     * Inverse of u16FixedPointToFloat: Convert float value between 0 and 1 to unsigned 16-bit fixed-point
     *
     * @param value Float value between 0 and 1
     * @return Encoded unsigned 16-bit fixed-point value
     */
    public static short floatToU16FixedPoint(float value) {
        int result = Math.round(value * 0x1p16f); // value * 65536
        return (short) Math.min(result, 0xffff); // Ensure it's capped at 65535
    }

    /**
     * Inverse of i16FixedPointToFloat: Convert float value between -1 and 1 to signed 16-bit fixed-point
     *
     * @param value Float value between -1 and 1
     * @return Encoded signed 16-bit fixed-point value
     */
    public static short floatToI16FixedPoint(float value) {
        int result = Math.round(value * 0x1p15f); // value * 32768
        return (short) Math.max(Math.min(result, 0x7fff), (short) 0x8000); // Ensure it's within the range
    }
}
