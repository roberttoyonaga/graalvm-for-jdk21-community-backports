/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.ibm.icu.lang.UCharacter;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.tregex.string.Encodings;
import org.graalvm.collections.EconomicMap;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PythonLocaleData {

    private static final int CACHE_SIZE = 16;
    private static final LRUCache<CacheKey, PythonLocaleData> CACHED_LOCALE_DATA = new LRUCache<>(CACHE_SIZE);

    private final CodePointSet wordChars;
    private final CodePointSet nonWordChars;
    private final List<Byte> caseFolding;

    public static PythonLocaleData getLocaleData(String locale) {
        if (locale.equals("C")) {
            return createCachedLocaleData(false, Charset.forName("US-ASCII"));
        } else {
            int dot = locale.indexOf('.');
            if (dot == -1) {
                throw new IllegalArgumentException("malformed locale: " + locale);
            }
            String language = locale.substring(0, dot);
            String encoding = locale.substring(dot + 1);
            return createCachedLocaleData(language.startsWith("tr_"), Charset.forName(encoding));
        }
    }

    public CodePointSet getWordCharacters() {
        return wordChars;
    }

    public CodePointSet getNonWordCharacters() {
        return nonWordChars;
    }

    public void caseFold(CodePointSetAccumulator charClass, CodePointSetAccumulator copy) {
        charClass.copyTo(copy);
        int iFolding = 0;
        for (Range r : copy) {
            iFolding = caseFoldingBinarySearch(iFolding, r.lo);
            while (iFolding < caseFoldingSize() && caseFoldingFrom(iFolding) >= r.lo && caseFoldingFrom(iFolding) <= r.hi) {
                charClass.addCodePoint(caseFoldingTo(iFolding));
                iFolding++;
            }
        }
    }

    private int caseFoldingFrom(int index) {
        return caseFolding.get(index << 1) & 0xFF;
    }

    private int caseFoldingTo(int index) {
        return caseFolding.get((index << 1) + 1) & 0xFF;
    }

    private int caseFoldingSize() {
        return caseFolding.size() >> 1;
    }

    private int caseFoldingBinarySearch(int minIndex, int target) {
        int lo = minIndex;
        int hi = caseFoldingSize() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >> 1;
            if (caseFoldingFrom(mid) < target) {
                lo = mid + 1;
            } else if (caseFoldingFrom(mid) > target) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return lo;
    }

    private static PythonLocaleData createCachedLocaleData(boolean turkish, Charset charset) {
        CacheKey key = new CacheKey(turkish, charset);
        PythonLocaleData localeData = CACHED_LOCALE_DATA.get(key);
        if (localeData == null) {
            localeData = new PythonLocaleData(turkish, charset);
            CACHED_LOCALE_DATA.put(key, localeData);
        }
        return localeData;
    }

    private PythonLocaleData(boolean turkish, Charset charset) {
        int[] codePoints = charsetToCodePoints(charset);

        CodePointSetAccumulator wordCharsAccum = new CodePointSetAccumulator();
        for (int b = 0; b <= 255; b++) {
            int codePoint = codePoints[b];
            if (UCharacter.isUAlphabetic(codePoint) || UCharacter.isDigit(codePoint) || codePoint == '_') {
                wordCharsAccum.appendCodePoint(b);
            }
        }
        this.wordChars = wordCharsAccum.toCodePointSet();
        this.nonWordChars = wordChars.createInverse(Encodings.LATIN_1);

        EconomicMap<Integer, Byte> invCodePoints = EconomicMap.create(256);
        for (int b = 0; b <= 255; b++) {
            invCodePoints.put(codePoints[b], (byte) b);
        }

        this.caseFolding = new ArrayList<>();
        for (int b = 0; b <= 255; b++) {
            int codePoint = codePoints[b];
            int lowerCase = toLowerCase(codePoint, turkish);
            int upperCase = toUpperCase(codePoint, turkish);
            if (lowerCase != codePoint && invCodePoints.containsKey(lowerCase)) {
                caseFolding.add((byte) b);
                caseFolding.add(invCodePoints.get(lowerCase));
            }
            if (upperCase != codePoint && invCodePoints.containsKey(upperCase)) {
                caseFolding.add((byte) b);
                caseFolding.add(invCodePoints.get(upperCase));
            }
        }
    }

    private static int toLowerCase(int codePoint, boolean turkish) {
        if (turkish && codePoint == 0x0049) {
            return 0x0131;
        }
        return UCharacter.toLowerCase(codePoint);
    }

    private static int toUpperCase(int codePoint, boolean turkish) {
        if (turkish && codePoint == 0x0069) {
            return 0x0130;
        }
        return UCharacter.toUpperCase(codePoint);
    }

    private static int[] charsetToCodePoints(Charset charset) {
        int[] codePoints = charsetToCodePointsFast(charset);
        if (codePoints == null) {
            return charsetToCodePointsSlow(charset);
        } else {
            assert Arrays.equals(codePoints, charsetToCodePointsSlow(charset));
            return codePoints;
        }
    }

    private static int[] charsetToCodePointsFast(Charset charset) {
        try {
            ByteBuffer byteRange = ByteBuffer.allocate(256);
            for (int b = 0; b <= 255; b++) {
                byteRange.put((byte) b);
            }
            byteRange.rewind();
            CharBuffer decoded = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPLACE).decode(byteRange);
            if (decoded.codePoints().count() == 256) {
                return decoded.codePoints().toArray();
            }
        } catch (CharacterCodingException e) {
        }
        return null;
    }

    private static int[] charsetToCodePointsSlow(Charset charset) {
        int[] codePoints = new int[256];
        CharsetDecoder decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer singleByte = ByteBuffer.allocate(1);
        for (int b = 0; b <= 255; b++) {
            decoder.reset();
            singleByte.put(0, (byte) b);
            singleByte.rewind();
            try {
                CharBuffer codePoint = decoder.decode(singleByte);
                if (codePoint.codePoints().count() == 1) {
                    codePoints[b] = codePoint.codePoints().findFirst().getAsInt();
                } else {
                    codePoints[b] = 0xFFFD;
                }
            } catch (CharacterCodingException e) {
                codePoints[b] = 0xFFFD;
            }
        }
        return codePoints;
    }

    private static final class CacheKey {
        private final boolean turkish;
        private final Charset charset;

        CacheKey(boolean turkish, Charset charset) {
            this.turkish = turkish;
            this.charset = charset;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CacheKey)) {
                return false;
            }
            CacheKey other = (CacheKey) obj;
            return this.turkish == other.turkish && this.charset.equals(other.charset);
        }

        @Override
        public int hashCode() {
            return Objects.hash(turkish, charset);
        }
    }

    private static class LRUCache<K, V> extends LinkedHashMap<K, V> {

        private static final long serialVersionUID = 6638590251101633602L;
        private final int cacheSize;

        LRUCache(int cacheSize) {
            super(cacheSize + 1, 0.75f, true);
            this.cacheSize = cacheSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > cacheSize;
        }
    }
}
