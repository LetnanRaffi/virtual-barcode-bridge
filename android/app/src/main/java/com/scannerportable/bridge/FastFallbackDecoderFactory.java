package com.scannerportable.bridge;

import android.os.SystemClock;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.Decoder;
import com.journeyapps.barcodescanner.DecoderFactory;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

final class FastFallbackDecoderFactory implements DecoderFactory {
    private final Collection<BarcodeFormat> formats;

    FastFallbackDecoderFactory(Collection<BarcodeFormat> formats) { this.formats = formats; }

    @Override public Decoder createDecoder(Map<DecodeHintType, ?> baseHints) {
        return new FastFallbackDecoder(baseHints, formats);
    }

    private static final class FastFallbackDecoder extends Decoder {
        private final MultiFormatReader fastReader = new MultiFormatReader();
        private final MultiFormatReader hardReader = new MultiFormatReader();
        private int consecutiveMisses;

        FastFallbackDecoder(Map<DecodeHintType, ?> baseHints, Collection<BarcodeFormat> formats) {
            super(new MultiFormatReader());
            Map<DecodeHintType, Object> fastHints = copyHints(baseHints);
            fastHints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
            fastHints.remove(DecodeHintType.NEED_RESULT_POINT_CALLBACK);
            fastReader.setHints(fastHints);
            Map<DecodeHintType, Object> hardHints = copyHints(fastHints);
            hardHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hardReader.setHints(hardHints);
        }

        private static Map<DecodeHintType, Object> copyHints(Map<DecodeHintType, ?> hints) {
            Map<DecodeHintType, Object> copy = new EnumMap<>(DecodeHintType.class);
            if (hints != null) copy.putAll(hints);
            return copy;
        }

        @Override public Result decode(LuminanceSource source) {
            long started = SystemClock.elapsedRealtimeNanos();
            Result result = attempt(fastReader, source);
            if (result != null) { consecutiveMisses = 0; logSuccess("fast", started); return result; }
            consecutiveMisses++;
            if (consecutiveMisses >= 2 && (consecutiveMisses & 1) == 0) {
                result = attempt(hardReader, source);
                if (result != null) { consecutiveMisses = 0; logSuccess("try_harder", started); return result; }
            }
            if (consecutiveMisses >= 4 && consecutiveMisses % 4 == 0) {
                LuminanceSource enhanced = stretchContrast(source);
                if (enhanced != source) result = attempt(hardReader, enhanced);
                if (result != null) { consecutiveMisses = 0; logSuccess("contrast", started); return result; }
            }
            return null;
        }

        private static Result attempt(MultiFormatReader reader, LuminanceSource source) {
            try { return reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source))); }
            catch (NotFoundException ignored) { return null; }
            finally { reader.reset(); }
        }

        private static void logSuccess(String path, long started) {
            long micros = (SystemClock.elapsedRealtimeNanos() - started) / 1_000L;
            Log.d("VBBScan", "decode_path=" + path + " decode_us=" + micros);
        }

        private static LuminanceSource stretchContrast(LuminanceSource source) {
            byte[] input = source.getMatrix();
            if (input.length == 0) return source;
            int[] histogram = new int[256];
            for (byte value : input) histogram[value & 0xff]++;
            int tail = Math.max(1, input.length / 50);
            int low = percentile(histogram, tail, false);
            int high = percentile(histogram, tail, true);
            if (high - low < 48) return source;
            byte[] output = new byte[input.length];
            int range = high - low;
            for (int i = 0; i < input.length; i++) {
                int value = input[i] & 0xff;
                output[i] = (byte) (value <= low ? 0 : value >= high ? 255 : (value - low) * 255 / range);
            }
            return new ArrayLuminanceSource(output, source.getWidth(), source.getHeight());
        }

        private static int percentile(int[] histogram, int target, boolean reverse) {
            int total = 0;
            if (reverse) {
                for (int i = 255; i >= 0; i--) { total += histogram[i]; if (total >= target) return i; }
                return 255;
            }
            for (int i = 0; i < 256; i++) { total += histogram[i]; if (total >= target) return i; }
            return 0;
        }
    }

    private static final class ArrayLuminanceSource extends LuminanceSource {
        private final byte[] matrix;
        ArrayLuminanceSource(byte[] matrix, int width, int height) { super(width, height); this.matrix = matrix; }
        @Override public byte[] getRow(int y, byte[] row) {
            int width = getWidth();
            if (row == null || row.length < width) row = new byte[width];
            System.arraycopy(matrix, y * width, row, 0, width);
            return row;
        }
        @Override public byte[] getMatrix() { return matrix; }
    }
}
