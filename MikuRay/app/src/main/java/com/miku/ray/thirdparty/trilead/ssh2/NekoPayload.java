package com.miku.ray.thirdparty.trilead.ssh2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Payload expansion and write semantics observed in Neko's injector. */
public final class NekoPayload {
    private static final Pattern ROTATE = Pattern.compile("\\[rotate=(.*?)\\]");
    private static final Pattern RANDOM = Pattern.compile("\\[random=(.*?)\\]");
    private static final Map<String, Integer> ROTATE_INDEX = new HashMap<>();
    private static final Random RANDOM_SOURCE = new Random();

    private NekoPayload() { }

    public static String expand(String targetHost, int targetPort, String payload, String userAgent) {
        if (payload == null || payload.isEmpty()) return payload;
        String hostPort = targetHost + ":" + targetPort;
        String result = payload;
        Map<String, String> tokens = new HashMap<>();
        tokens.put("[method]", "CONNECT");
        tokens.put("[host]", targetHost);
        tokens.put("[port]", Integer.toString(targetPort));
        tokens.put("[host_port]", hostPort);
        tokens.put("[protocol]", "HTTP/1.0");
        tokens.put("[ssh]", hostPort);
        tokens.put("[crlf*2]", "\r\n\r\n");
        tokens.put("[crlf]", "\r\n");
        tokens.put("[cr]", "\r");
        tokens.put("[lf]", "\n");
        tokens.put("[lfcr]", "\n\r");
        tokens.put("\\n", "\n");
        tokens.put("\\r", "\r");
        tokens.put("[ua]", userAgent == null ? "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.130 Safari/537.36" : userAgent);
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        Matcher rotate = ROTATE.matcher(result);
        StringBuffer rotated = new StringBuffer();
        while (rotate.find()) {
            String[] values = rotate.group(1).split(";", -1);
            String key = rotate.group(0);
            int index;
            synchronized (ROTATE_INDEX) {
                index = ROTATE_INDEX.getOrDefault(key, -1) + 1;
                if (index >= values.length) index = 0;
                ROTATE_INDEX.put(key, index);
            }
            rotate.appendReplacement(rotated, Matcher.quoteReplacement(values.length == 0 ? "" : values[index]));
        }
        rotate.appendTail(rotated);
        result = rotated.toString();
        Matcher random = RANDOM.matcher(result);
        StringBuffer randomized = new StringBuffer();
        while (random.find()) {
            String[] values = random.group(1).split(";", -1);
            String replacement = values.length == 0 ? "" : values[RANDOM_SOURCE.nextInt(values.length)];
            random.appendReplacement(randomized, Matcher.quoteReplacement(replacement));
        }
        random.appendTail(randomized);
        return randomized.toString();
    }

    public static void write(OutputStream out, String value) throws IOException {
        if (value == null) return;
        if (value.contains("[delay_split]")) {
            String[] pieces = value.split(Pattern.quote("[delay_split]"), -1);
            for (int i = 0; i < pieces.length; i++) {
                writeSplit(out, pieces[i]);
                if (i < pieces.length - 1) {
                    try { Thread.sleep(1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
            return;
        }
        writeSplit(out, value);
    }

    private static void writeSplit(OutputStream out, String value) throws IOException {
        String[] pieces = value.split(Pattern.quote("[split]"), -1);
        for (String piece : pieces) {
            out.write(piece.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }
    }
}
