package net.voidsmp.client.addons.models;

public enum ConfigEntryType {
    BOOLEAN,
    USHORT,
    SHORT,
    UINT,
    INT,
    UFLOAT,
    FLOAT,
    ULONG,
    LONG,
    STRING,
    BYTE,
    UBYTE,
    // Backing value is the chosen option's index into ConfigEntry#choices().
    ENUM
}
