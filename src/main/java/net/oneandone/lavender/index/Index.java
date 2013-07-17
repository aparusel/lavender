/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
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
package net.oneandone.lavender.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Iterator;
import java.util.Properties;

/**
 * A set of labels.
 *
 * Format:
 *   originalPath = lavendelizedPath : md5
 *
 * where
 *   originalPath      source request path, that will be replaced by lavendelizer
 *   lavendelizedPath  path on Lavender Servers
 *   md5               md5 sum of the resource
 */
public class Index implements Iterable<Label> {
    public static final String ENCODING = "UTF-8";

    private static final Logger LOG = LoggerFactory.getLogger(Index.class);

    private static final String DELIMITER = ":";

    //--

    private final Properties properties;

    public Index() {
        properties = new Properties();
    }

    public Index(File indexFile) throws IOException {
        this(new FileInputStream(indexFile));
    }

    public Index(URL indexUrl) throws IOException {
        this(indexUrl.openStream());
        LOG.info("Successfully loaded index with from " + indexUrl);
    }

    public Index(InputStream in) throws IOException {
        this();
        load(in);
    }

    private void load(InputStream in) throws IOException {
        InputStreamReader reader;

        reader = new InputStreamReader(in, ENCODING);
        properties.load(reader);
        reader.close();
    }

    //--

    public int size() {
        return properties.size();
    }

    /** @return true if the entry was new */
    public boolean add(Label label) throws IOException {
        String original;
        String lavendelized;
        String md5hex;
        String prev;
        String next;

        original = label.getOriginalPath();
        if (original.isEmpty() || original.startsWith("/") || original.endsWith("/")) {
            throw new IllegalArgumentException("invalid originalPath: " + original);
        }
        lavendelized = label.getLavendelizedPath();
        if (lavendelized.isEmpty() || lavendelized.startsWith("/") || lavendelized.endsWith("/")) {
            throw new IllegalArgumentException("invalid lavendelizedPath" + lavendelized);
        }
        md5hex = Hex.encodeString(label.md5());
        if (lavendelized.indexOf(DELIMITER) != -1) {
            throw new IllegalArgumentException(lavendelized);
        }
        next = lavendelized + DELIMITER + md5hex;
        prev = (String) properties.setProperty(original, next);
        if (prev != null) {
            if (!next.equals(prev)) {
                throw new IllegalArgumentException("conflicting values for originalPath: " + original + ": " + prev + " vs " + next);
            }
            // happens when modules are extracted and packaged into the war
            return false;
        } else {
            return true;
        }
    }

    public Iterator<Label> iterator() {
        final Iterator<String> iter = properties.stringPropertyNames().iterator();
        return new Iterator<Label>() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Label next() {
                return lookup(iter.next());
            }

            @Override
            public void remove() {
                throw new IllegalStateException();
            }
        };
    }

    public Label lookup(String originalPath) {
        String property;
        String lavendelizedPath;
        byte[] md5;
        int idx;

        property = properties.getProperty(originalPath);
        if (property == null) {
            return null;
        }
        idx = property.indexOf(':');
        if (idx == -1) {
            throw new IllegalStateException(originalPath + " = " + property);
        }
        lavendelizedPath = property.substring(0, idx);
        md5 = Hex.decode(property.substring(idx + 1).toCharArray());
        return new Label(originalPath, lavendelizedPath, md5);
    }

    public boolean equals(Object obj) {
        if (obj instanceof Index) {
            return properties.equals(((Index) obj).properties);
        }
        return false;
    }

    public int hashCode() {
        return properties.hashCode();
    }

    public void save(File indexFile) throws IOException {
        try (OutputStream dest = new FileOutputStream(indexFile)) {
            save(dest);
        }
    }

    /** dest will be not be closed */
    public void save(OutputStream dest) throws IOException {
        Writer writer = new OutputStreamWriter(dest, ENCODING);
        save(writer);
    }

    /** dest will not be closed */
    public void save(Writer writer) throws IOException {
        properties.store(writer, null);
    }
}