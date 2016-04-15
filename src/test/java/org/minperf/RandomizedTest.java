package org.minperf;

import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Random;

import org.junit.Assert;
import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;


/**
 * Methods to test the MPHF with random data.
 */
public class RandomizedTest {
    
    private static final boolean MULTI_THREADED = true;
    
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int[] HEX_DECODE = new int['f' + 1];
    
    static {
        for (int i = 0; i < HEX_DECODE.length; i++) {
            HEX_DECODE[i] = -1;
        }
        for (int i = 0; i <= 9; i++) {
            HEX_DECODE[i + '0'] = i;
        }
        for (int i = 0; i <= 5; i++) {
            HEX_DECODE[i + 'a'] = HEX_DECODE[i + 'A'] = i + 10;
        }
    }
    
    public static void runTests() {
        int[] pairs = { 
                23, 828, 23, 1656, 23, 3312, 
                23, 6624, 25, 1250, 25,
                3750, 25, 7500, 25, 15000 };
        for (int i = 0; i < pairs.length; i += 2) {
            int leafSize = pairs[i], size = pairs[i + 1];
            FunctionInfo info = test(leafSize, size, size, true);
            System.out.println(new Timestamp(System.currentTimeMillis()).toString());
            System.out.println(info);
        }
    }
    
    static void verifyParameters() {
        int size = 100000;
        int leafSize = 11;
        int loadFactor = 150;
        System.out.println("4.1 Parameters");
        // CHD: 1.927 seconds; 2.2512 bits/key; eval 0.33 microseconds/key
        System.out.println("  size " + size + " leafSize " + leafSize + " loadFactor " + loadFactor);
        for (int i = 0; i < 5; i++) {
            FunctionInfo info = RandomizedTest.test(leafSize, loadFactor, size, true);
            System.out.println("  " + info.bitsPerKey + " bits/key");
            System.out.println("  " + info.generateMicros * size / 1000000 +
                    " seconds to generate");
            System.out.println("  " + info.evaluateMicros +
                    " microseconds to evaluate");
            if (info.bitsPerKey < 1.75 && 
                    info.generateMicros * size / 1000000 < 1.0 && 
                    info.evaluateMicros < 1.0) {
                // all tests passed
                return;
            }
            RandomizedTest.test(4, 1024, 8 * 1024, true);
        }
        Assert.fail();
    }
    
    public static void experimentalResults() {
        System.out.println("6 Experimental Results");
        int loadFactor = 16 * 1024;
        System.out.println("loadFactor " + loadFactor);
        System.out.println("leafSize, bits/key");
        System.out.println("estimated");
        for (int leafSize = 2; leafSize <= 64; leafSize++) {
            int size = 1024 * 1024 / leafSize;
            size = Math.max(size, 32 * 1024);
            FunctionInfo info = TimeAndSpaceEstimator.estimateTimeAndSpace(leafSize, loadFactor, size);
            System.out.println("        (" + info.leafSize + ", " + info.bitsPerKey + ")");
            System.out.println("size: " + size);
        }
        System.out.println("experimental");
        for (int leafSize = 2; leafSize <= 23; leafSize++) {
            int size = 1024 * 1024 / leafSize;
            size = Math.max(size, 32 * 1024);
            FunctionInfo info = test(leafSize, loadFactor, size, false);
            System.out.println("        (" + info.leafSize + ", " + info.bitsPerKey + ")");
        }
        System.out.println("leafSize, generation time in micros/key");
        ArrayList<FunctionInfo> infos = new ArrayList<FunctionInfo>();
        for (int leafSize = 2; leafSize <= 12; leafSize++) {
            int size = 1024 * 1024 / leafSize;
            size = Math.max(size, 32 * 1024);
            FunctionInfo info = test(leafSize, 128, size, true);
            infos.add(info);
            System.out.println("        (" + info.leafSize + ", " +
                    info.generateMicros + ")");
        }
        System.out
                .println("leafSize, evaluation time in micros/key");
        for (FunctionInfo info : infos) {
            System.out.println("        (" + info.leafSize + ", " +
                    info.evaluateMicros + ")");
        }
    }
        
    public static void reasonableParameterValues() {        
        System.out.println("6.1 Reasonable Parameter Values");
        int leafSize = 10;
        int size = 16 * 1024;
        System.out.println("(leafSize=" + leafSize + ", size=" + size +
                "): loadFactor, generation time in micros/key");
        ArrayList<FunctionInfo> infos = new ArrayList<FunctionInfo>();
        for (int loadFactor = 8; loadFactor <= 16 * 1024; loadFactor *= 2) {
            FunctionInfo info = test(leafSize, loadFactor, 16 * 1024, true);
            infos.add(info);
            System.out.println("        (" + info.loadFactor + ", " +
                    info.generateMicros + ")");
        }
        System.out
                .println("loadFactor, evaluation time in micros/key");
        for (FunctionInfo info : infos) {
            System.out.println("        (" + info.loadFactor + ", " +
                    info.evaluateMicros + ")");
        }
        System.out
                .println("loadFactor, bits/key");
        for (FunctionInfo info : infos) {
            System.out.println("        (" + info.loadFactor + ", " +
                    info.bitsPerKey + ")");
        }
    }
    
    static <T> void test(HashSet<T> set, UniversalHash<T> hash,
            byte[] description, int leafSize, int loadFactor) {
        BitSet known = new BitSet();
        RecSplitEvaluator<T> eval = 
                RecSplitBuilder.newInstance(hash).leafSize(leafSize).loadFactor(loadFactor).
                buildEvaluator(new BitBuffer(description));
        // Profiler prof = new Profiler().startCollecting();
        for (T x : set) {
            int index = eval.evaluate(x);
            if (index > set.size() || index < 0) {
                Assert.fail("wrong entry: " + x + " " + index +
                        " leafSize " + leafSize + 
                        " loadFactor " + loadFactor +
                        " hash " + convertBytesToHex(description));
            }
            if (known.get(index)) {
                eval.evaluate(x);
                Assert.fail("duplicate entry: " + x + " " + index +
                        " leafSize " + leafSize + 
                        " loadFactor " + loadFactor +
                        " hash " + convertBytesToHex(description));
            }
            known.set(index);
        }
        // System.out.println(prof.getTop(5));
    }

    public static FunctionInfo test(int leafSize, int loadFactor, int size, boolean evaluate) {
        HashSet<Long> set = createSet(size, 1);
        UniversalHash<Long> hash = new LongHash();
        long generateNanos = System.nanoTime();
        BitBuffer buff;
        buff = RecSplitBuilder.newInstance(hash).leafSize(leafSize).loadFactor(loadFactor).
                multiThreaded(MULTI_THREADED).generate(set);
        int bits = buff.position();
        byte[] data = buff.toByteArray();
        generateNanos = System.nanoTime() - generateNanos;
        assertTrue(bits <= data.length * 8);
        long evaluateNanos = 0;
        if (evaluate) {
            evaluateNanos = System.nanoTime();
            test(set, hash, data, leafSize, loadFactor);
            evaluateNanos = System.nanoTime() - evaluateNanos;
        }
        FunctionInfo info = new FunctionInfo();
        info.leafSize = leafSize;
        info.size = size;
        info.loadFactor = loadFactor;
        info.bitsPerKey = (double) bits / size;
        if (evaluate) {
            info.evaluateMicros = (double) evaluateNanos / 1000 / size;
        }
        info.generateMicros = (double) generateNanos / 1000 / size;
        return info;
    }

    static HashSet<Long> createSet(int size, int seed) {
        Random r = new Random(seed);
        HashSet<Long> set = new HashSet<Long>(size);
        while (set.size() < size) {
            set.add(r.nextLong());
        }
        return set;
    }

    /**
     * Convert a byte array to a hex encoded string.
     *
     * @param value the byte array
     * @return the hex encoded string
     */
    public static String convertBytesToHex(byte[] value) {
        int len = value.length;
        char[] buff = new char[len + len];
        char[] hex = HEX;
        for (int i = 0; i < len; i++) {
            int c = value[i] & 0xff;
            buff[i + i] = hex[c >> 4];
            buff[i + i + 1] = hex[c & 0xf];
        }
        return new String(buff);
    }

    /**
     * Convert a hex encoded string to a byte array.
     *
     * @param s the hex encoded string
     * @return the byte array
     */
    public static byte[] convertHexToBytes(String s) {
        int len = s.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException(s);
        }
        len /= 2;
        byte[] buff = new byte[len];
        int[] hex = HEX_DECODE;
        for (int i = 0; i < len; i++) {
            int d = hex[s.charAt(i + i)] << 4 | hex[s.charAt(i + i + 1)];
            buff[i] = (byte) d;
        }
        return buff;
    }
    
}
