/*
 * Copyright 2026 deepcodecreate
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terbian.debian;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MirrorList {
    private static final int MAX_BYTES = 6 * 1024 * 1024;
    private static final Pattern TOKEN = Pattern.compile(
            "<(h[1-4])[^>]*>(.*?)</\\1>|<th[^>]*colspan[^>]*>(.*?)</th>|<a\\s[^>]*?href=\"(https?://[^\"#?]+)\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private MirrorList() {
    }

    public static Map<String, List<String>> fetch(String distro) throws IOException {
        boolean ubuntu = AptSources.UBUNTU.equals(distro);
        String page = download(ubuntu ? "https://launchpad.net/ubuntu/+archivemirrors"
                : "https://www.debian.org/mirror/list");
        return parse(page, ubuntu ? "ubuntu" : "debian");
    }

    public interface Progress {
        void onProgress(int done, int total);
    }

    public static Map<String, List<String>> onlyArmCompatible(
            Map<String, List<String>> data, String codename, Progress progress) throws InterruptedException {
        final String suite = codename == null || codename.isEmpty() ? "noble" : codename;
        List<Callable<String[]>> tasks = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : data.entrySet()) {
            final String country = e.getKey();
            for (String url : e.getValue()) {
                final String ports = portsTwin(url);
                if (ports == null) {
                    continue;
                }
                tasks.add(() -> probe(ports + "/dists/" + suite + "/InRelease") ? new String[]{country, ports} : null);
            }
        }
        final int total = tasks.size();
        final AtomicInteger done = new AtomicInteger();
        List<Callable<String[]>> counted = new ArrayList<>();
        for (Callable<String[]> task : tasks) {
            counted.add(() -> {
                try {
                    return task.call();
                } finally {
                    int n = done.incrementAndGet();
                    if (progress != null && (n % 5 == 0 || n == total)) {
                        progress.onProgress(n, total);
                    }
                }
            });
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(32);
        try {
            List<Future<String[]>> futures = pool.invokeAll(counted, 75, TimeUnit.SECONDS);
            for (Future<String[]> f : futures) {
                if (f.isCancelled()) {
                    continue;
                }
                try {
                    String[] hit = f.get();
                    if (hit != null) {
                        List<String> list = result.get(hit[0]);
                        if (list == null) {
                            list = new ArrayList<>();
                            result.put(hit[0], list);
                        }
                        if (!list.contains(hit[1])) {
                            list.add(hit[1]);
                        }
                    }
                } catch (java.util.concurrent.ExecutionException ignored) {
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return result;
    }

    static String portsTwin(String url) {
        String u = url;
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.endsWith("/ubuntu-ports")) {
            return u;
        }
        if (u.endsWith("/ubuntu")) {
            return u + "-ports";
        }
        return null;
    }

    private static boolean probe(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(4_000);
            connection.setReadTimeout(5_000);
            connection.setRequestProperty("User-Agent", "Terbian/1.0");
            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static Map<String, List<String>> parse(String html, String needle) {
        Map<String, Set<String>> byCountry = new LinkedHashMap<>();
        String country = null;
        Matcher m = TOKEN.matcher(html);
        while (m.find()) {
            String heading = m.group(2) != null ? m.group(2) : m.group(3);
            if (heading != null) {
                String text = clean(heading);
                if (!text.isEmpty() && text.length() < 60) {
                    country = text;
                }
                continue;
            }
            String url = m.group(4);
            if (country == null || url == null) {
                continue;
            }
            String lower = url.toLowerCase();
            if (!lower.contains(needle) || lower.contains("launchpad.net") || lower.contains("www.debian.org")
                    || lower.contains("ubuntu.com/") && lower.contains("/ubuntu/+")) {
                continue;
            }
            if (!looksLikeArchive(lower, needle)) {
                continue;
            }
            Set<String> set = byCountry.get(country);
            if (set == null) {
                set = new LinkedHashSet<>();
                byCountry.put(country, set);
            }
            set.add(url);
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : byCountry.entrySet()) {
            if (!e.getValue().isEmpty()) {
                result.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
        return result;
    }

    private static boolean looksLikeArchive(String lowerUrl, String needle) {
        String path = lowerUrl.substring(lowerUrl.indexOf("//") + 2);
        int slash = path.indexOf('/');
        String host = slash < 0 ? path : path.substring(0, slash);
        String rest = slash < 0 ? "" : path.substring(slash);
        return host.contains(needle) || rest.contains(needle);
    }

    private static String clean(String raw) {
        String text = TAGS.matcher(raw).replaceAll(" ");
        text = text.replace("&amp;", "&").replace("&nbsp;", " ").replace("&#39;", "'")
                .replace("&quot;", "\"").replaceAll("&[a-zA-Z#0-9]+;", "");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String download(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(25_000);
        connection.setRequestProperty("User-Agent", "Terbian/1.0");
        try {
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    if (out.size() > MAX_BYTES) {
                        throw new IOException("The page is too large");
                    }
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
