package org.foo.pseudo;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.ice4j.pseudotcp.PseudoTcpSocket;
import org.ice4j.pseudotcp.PseudoTcpSocketFactory;

public class PseudoTest {

	private static final int TIMEOUT = 15000;

	private static final int MTU = 1000;

	private final PseudoTcpSocketFactory pseudoTcpSocketFactory = new PseudoTcpSocketFactory();

	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private DatagramSocket dsock1;
	private DatagramSocket dsock2;

	private SocketAddress addr1;

	private SocketAddress addr2;

	public static void main(String[] args) throws Exception {
		try {
			PseudoTest inst = new PseudoTest();
			inst.call();
		} catch (Throwable e) {
			e.printStackTrace();
		} finally {
			System.exit(0);
		}
	}

	private void call() throws Exception {
		dsock1 = new DatagramSocket(0, InetAddress.getLoopbackAddress());
		dsock2 = new DatagramSocket(0, InetAddress.getLoopbackAddress());

		addr1 = dsock1.getLocalSocketAddress();
		log("addr1: " + addr1);
		addr2 = dsock2.getLocalSocketAddress();
		log("addr2: " + addr2);
		dsock1.connect(addr2);
		dsock2.connect(addr1);

		aaa(0);
		aaa(1);

	}

	private int nTransmissions;

	private void aaa(long convId) throws Exception {
		PseudoTcpSocket sock1 = createPseudoTcpSocket(dsock1, convId);
		final PseudoTcpSocket sock2 = createPseudoTcpSocket(dsock2, convId);
		Future<Void> fut = submitConnect(sock2, addr1);
		log("accepting dsock1:" + sock1.getConversationID() + " from dsock2 ...");
		sock1.accept(addr2, TIMEOUT);
		log("accepted dsock1:" + sock1.getConversationID() + " from dsock2");
		fut.get();

		log("#transmissions: " + (++nTransmissions));
		startPump(new DevZeroInputStream(), sock1.getOutputStream(), "zero ==> dsock1:" + convId);
		startPump(sock2.getInputStream(), new DevNullOutputStream(), "dsock2:" + convId + " ==> null");
		Thread.sleep(2000);

		log("#transmissions: " + (++nTransmissions));
		startPump(new DevZeroInputStream(), sock2.getOutputStream(), "zero ==> dsock2:" + convId);
		startPump(sock1.getInputStream(), new DevNullOutputStream(), "dsock1:" + convId + " ==> null");
		Thread.sleep(2000);
	}

	private Future<Void> submitConnect(final PseudoTcpSocket sock, final SocketAddress addr) {
		Future<Void> fut = executorService.submit(new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				String otherDsock;
				String thisDsock;
				if (addr.equals(addr1)) {
					otherDsock = "dsock1";
					thisDsock = "dsock2";
				} else {
					otherDsock = "dsock2";
					thisDsock = "dsock1";
				}
				log("connecting " + thisDsock + ":" + sock.getConversationID() + " to " + otherDsock + " ...");
				sock.connect(addr, TIMEOUT);
				log("connected " + thisDsock + ":" + sock.getConversationID() + " to " + otherDsock);
				return null;
			}
		});
		return fut;
	}

	public static void startPump(final InputStream in, final OutputStream out, final String name) {
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				long meterPeriod = 1000;
				long backThen = System.currentTimeMillis() - meterPeriod;
				long total = 0;
				long prevTotal = 0;
				try {
					byte[] buf = new byte[MTU];
					int nb;
					while (-1 != (nb = in.read(buf))) {
						// log("got: " + nb + " bytes");
						total += nb;
						long now = System.currentTimeMillis();
						if (now - backThen > meterPeriod) {
							log(String.format( "%.2fkb/s; total: %d", (double)(total - prevTotal) / (now - backThen), total));
							backThen = now;
							prevTotal = total;
						}
						out.write(buf, 0, nb);
						out.flush();
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}, name);
		t.start();
	}

	public static void closeQuietly(Closeable resource) {
		if (resource != null) {
			try {
				resource.close();
			} catch (IOException e) {
			}
		}
	}

	private PseudoTcpSocket createPseudoTcpSocket(DatagramSocket datagramSocket, long convId) throws IOException {
		PseudoTcpSocket socket = pseudoTcpSocketFactory.createSocket(datagramSocket);
		socket.setMTU(MTU);
		socket.setConversationID(convId);
		return socket;
	}

	private static class DevNullOutputStream extends OutputStream {

		@Override
		public void write(int b) throws IOException {
			if (closed) {
				throw new IOException("Stream is closed");
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			if (closed) {
				throw new IOException("Stream is closed");
			}
		}

		@Override
		public void close() throws IOException {
			closed = true;
		}

		private volatile boolean closed;
	}

	private static class DevZeroInputStream extends InputStream {

		@Override
		public int read() throws IOException {
			if (closed) {
				throw new IOException("Stream is closed");
			}
			return 0;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (closed) {
				throw new IOException("Stream is closed");
			}
			return len;
		}

		@Override
		public void close() throws IOException {
			closed = true;
		}

		private volatile boolean closed;
	}

	private static void log(String s) {
		System.err.printf("[%1$tk:%1$tM:%1$tS,%1$tL] %4$s [T%7$s] {%2$s} %5$s %6$s %n", new Date(), "", "", "", s, "",
				Thread.currentThread().getName());
	}
}
