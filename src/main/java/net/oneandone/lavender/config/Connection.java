package net.oneandone.lavender.config;

import net.oneandone.sushi.fs.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Node and optionally lock on a host. */
public class Connection implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Connection.class);

    public static Connection openLocked(Host host, Node root, String lock, int wait) throws IOException {
        Node lockfile;
        int seconds;

        lockfile = root.join("tmp/lavender.lock");
        seconds = 0;
        while (true) {
            try {
                lockfile.mkfile();
                break;
            } catch (IOException e) {
                if (seconds >= wait) {
                    throw e;
                }
                if (seconds % 10 == 0) {
                    LOG.info("waiting for lock " + lockfile + ", seconds=" + seconds);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    // fall-through
                }
                seconds++;
            }
        }
        lockfile.writeString(lock);
        return new Connection(host, root, lockfile);
    }

    public static Connection openSimple(Host host, Node root) {
        return new Connection(host, root, null);
    }

    //--

    private final Host host;
    private final Node root;
    private final Node lock;

    public Connection(Host host, Node root, Node lock) {
        this.host = host;
        this.root = root;
        this.lock = lock;
    }

    public Host getHost() {
        return host;
    }

    public Node join(String ... names) {
        return root.join(names);
    }

    public void close() throws IOException {
        if (lock != null) {
            lock.deleteFile();
        }
    }
}
