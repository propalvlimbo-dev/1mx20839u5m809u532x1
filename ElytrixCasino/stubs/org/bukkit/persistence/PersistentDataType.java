package org.bukkit.persistence;
public interface PersistentDataType<P, C> {
    Class<P> getPrimitiveType();
    Class<C> getComplexType();
    PersistentDataType<String, String> STRING = null;
    PersistentDataType<Byte, Byte> BYTE = null;
    PersistentDataType<Integer, Integer> INTEGER = null;
    PersistentDataType<Long, Long> LONG = null;
    PersistentDataType<Double, Double> DOUBLE = null;
    PersistentDataType<Float, Float> FLOAT = null;
    PersistentDataType<Short, Short> SHORT = null;
    PersistentDataType<byte[], byte[]> BYTE_ARRAY = null;
    PersistentDataType<int[], int[]> INTEGER_ARRAY = null;
}
