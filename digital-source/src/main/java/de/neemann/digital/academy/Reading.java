/*
 * Copyright (c) 2026 Midnight Logic Academy contributors.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.academy;

import de.neemann.digital.core.ObservableValue;

/**
 * A snapshot of a single output. Invalid states are never converted to 0.
 */
public final class Reading {
    /**
     * The kind of the reading
     */
    public enum Kind {
        /**
         * a valid logic value 0 or 1
         */
        VALUE,
        /**
         * output is high impedance, e.g. not driven
         */
        HIGH_Z,
        /**
         * the simulation is not running
         */
        NOT_RUNNING,
        /**
         * the output port does not exist
         */
        MISSING,
        /**
         * the simulation reported an error
         */
        ERROR
    }

    /**
     * the simulation is not running
     */
    public static final Reading NOT_RUNNING = new Reading(Kind.NOT_RUNNING, 0, "仿真未运行");
    /**
     * the port is missing
     */
    public static final Reading MISSING = new Reading(Kind.MISSING, 0, "缺少输出端口");

    private final Kind kind;
    private final long value;
    private final String detail;

    private Reading(Kind kind, long value, String detail) {
        this.kind = kind;
        this.value = value;
        this.detail = detail;
    }

    /**
     * Creates a reading from a Digital value.
     *
     * @param v the value
     * @return the reading
     */
    public static Reading of(ObservableValue v) {
        if (v.isHighZ())
            return new Reading(Kind.HIGH_Z, 0, "高阻：输出未被驱动");
        return new Reading(Kind.VALUE, v.getValue(), null);
    }

    /**
     * Creates an error reading.
     *
     * @param detail the description of the error
     * @return the reading
     */
    public static Reading error(String detail) {
        return new Reading(Kind.ERROR, 0, detail);
    }

    /**
     * Packs the readings of several one-bit outputs into one value, the first output being the highest bit.
     * A single reading is returned as it is; the first invalid part makes the whole reading invalid.
     *
     * @param parts the readings in the level's output order
     * @return the packed reading
     */
    public static Reading pack(java.util.List<Reading> parts) {
        if (parts.size() == 1)
            return parts.get(0);
        long v = 0;
        for (Reading r : parts) {
            if (!r.isValid())
                return r;
            v = (v << 1) | (r.value != 0 ? 1 : 0);
        }
        return new Reading(Kind.VALUE, v, null);
    }

    /**
     * @param other another reading
     * @return true if both are valid and carry the same value
     */
    public boolean same(Reading other) {
        return isValid() && other.isValid() && value == other.value;
    }

    /**
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @return true if this is a valid 0 or 1
     */
    public boolean isValid() {
        return kind == Kind.VALUE;
    }

    /**
     * @return true if valid and 1
     */
    public boolean isTrue() {
        return kind == Kind.VALUE && value != 0;
    }

    /**
     * @return the logic value, only meaningful if valid
     */
    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        if (kind == Kind.VALUE)
            return Long.toString(value);
        return detail;
    }
}
