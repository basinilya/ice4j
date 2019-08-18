package net.java.sip.communicator.plugin.portforward;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ice4j.pseudotcp.PseudoTcpSocket;

public final class PortForwardUtils
{
    public static final int MTU = 1300;

    public static final String PREFIX_LISTEN = "portforward.listen.";
    public static final String PREFIX_CONNECT = "portforward.connect.";


    private PortForwardUtils()
    {
    }

    public static void startPump(final Socket readSock, final Socket writeSock,
        final AtomicInteger refCount, final String name)
    {
    	
        boolean ok = TS.entering("startPump", readSock, writeSock, refCount, name);
        try {
            Objects.requireNonNull(readSock);
            Objects.requireNonNull(writeSock);
            Thread t = new Thread(new Runnable()
            {
    
                @Override
                public void run()
                {
                    boolean ok = TS.entering(Thread.currentThread().getName() + ".run");
                    try
                    {
                        try
                        {
                            InputStream in = readSock.getInputStream();
                            OutputStream out = writeSock.getOutputStream();
                            byte[] buf = new byte[MTU];
                            int nb;
                            while (-1 != (nb = in.read(buf)))
                            {
                                LOGGER.log(Level.FINER, "{0} got: {1} bytes", new Object[] { name, nb });
                                out.write(buf, 0, nb);
                                out.flush();
                            }
                        }
                        finally
                        {
                            writeSock.shutdownOutput();
                        }
                        ok = true;
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                    finally
                    {
                        if (refCount.decrementAndGet() == 0)
                        {
                            closeQuietly(readSock);
                            closeQuietly(writeSock);
                        }
                        TS.exiting(Thread.currentThread().getName() + ".run", "void", ok);
                    }
                }
            }, name);
            t.start();
            ok = true;
        } finally {
            TS.exiting("startPump", "void", ok);
        }
    }


    public static void closeQuietly(Closeable resource)
    {
        boolean ok = TS.entering("closeQuietly", resource);
        try {
            if (resource != null)
            {
                try
                {
                    resource.close();
                }
                catch (IOException e)
                {
                }
            }
            ok = true;
        } finally {
            TS.exiting("closeQuietly", "void", ok);
        }
    }


    public static InetSocketAddress parseAddressString(final String addressString,
        final int defaultPort /**/)
        throws URISyntaxException
    {
        boolean ok = TS.entering("parseAddress0", addressString, defaultPort);
        InetSocketAddress res = null;
        try {
            final URI uri = new URI("my://" + addressString);
    
            final String host = uri.getHost();
            int port = uri.getPort();
    
            if (port == -1)
            {
                port = defaultPort;
            }
    
            if (host == null || port == -1)
            {
                throw new URISyntaxException(uri.toString(),
                    "must have host or no default port specified");
            }
    
            res = InetSocketAddress.createUnresolved(host, port);
            ok = true;
            return res;
        } finally {
            TS.exiting("parseAddress0", res, ok);
        }
    }

    public static InetSocketAddress getResolved(InetSocketAddress unresolved)
        throws UnknownHostException
    {
        boolean ok = TS.entering("getResolved", unresolved);
        InetSocketAddress res = null;
        try {
            res =
                unresolved.isUnresolved()
                    ? new InetSocketAddress(
                        InetAddress.getByName(unresolved.getHostName()),
                        unresolved.getPort())
                    : unresolved;
            ok = true;
            return res;
        } finally {
            TS.exiting("getResolved", res, ok);
        }
    }

    public static Set<String> listSubKeys(Properties props, String prefix)
    {
        Set<String> res = new HashSet<>();
        for (String x : props.stringPropertyNames())
        {
            if (x.startsWith(prefix))
            {
                String[] a = x.substring(prefix.length()).split("[.]", 2);
                String key = a[0];
                res.add(key);
            }
        }
        return res;
    }

    public static Object[] prepend(Object[] a, Object b)
    {
        if (a == null)
        {
            return new Object[]{ b };
        }

        int length = a.length;
        Object[] result = new Object[length + 1];
        System.arraycopy(a, 0, result, 1, length);
        result[0] = b;
        return result;
    }

    private static boolean staticEntering1(String sourceMethod, Object... params)
    {
    	if ("".length() == 10) {
    		LOGGER.entering(PortForwardManager.class.getName(), sourceMethod, params);
    	}
        return false;
    }

    private static void staticExiting1(String sourceMethod, Object res, boolean ok)
    {
    	if ("".length() == 10) {
	        if (ok) {
	            LOGGER.exiting(PortForwardManager.class.getName(), sourceMethod, res);
	        } else {
	            LOGGER.exiting(PortForwardManager.class.getName(), sourceMethod);
	        }
    	}
    }
    private static final Logger LOGGER =
        Logger.getLogger(PortForwardUtils.class.getName());

    private static final TraceSupport TS = new TraceSupport(null, LOGGER);
}
