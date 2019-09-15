package org.foo.pseudo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.ice4j.pseudotcp.Option;
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
		if (false) {
			YesInputStream in = new YesInputStream("tst:");
			DevNullOutputStream out = new DevNullOutputStream();
			startPump(in, out, "xxx", new YesInputStream("tst:"));
			Thread.sleep(2000);
			System.exit(0);
		}
		try {
			PseudoTest inst = new PseudoTest();
			inst.call();
		} catch (Throwable e) {
			EH.uncaughtException(null, e);
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

		OutputStream zeroToDsock1 = new BufferedOutputStream(new OutputStream() {

			@Override
			public void write(int b) throws IOException {
				write(new byte[] { (byte) b }, 0, 1);
			}

			@Override
			public void write(byte[] buf, int off, int len) throws IOException {
				DatagramPacket pkt = new DatagramPacket(buf, 0, len);
				dsock1.send(pkt);
			}
		}, MTU);

		InputStream dsock2ToNull = new UdpInputStream(dsock2);

		startPump(dsock2ToNull, new DevNullOutputStream(), "dummy2", null);
		startPump(new DevZeroInputStream(), zeroToDsock1, "dummy1", null);
		Thread.sleep(10000);
		System.exit(0);
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

		String tag;

		log("#transmissions: " + (++nTransmissions));
		tag = "dsock1==>dsock2:" + convId;
		startPump(new YesInputStream(tag), sock1.getOutputStream(), "yes ==> dsock1:" + convId, null);
		startPump(sock2.getInputStream(), new DevNullOutputStream(), "dsock2:" + convId + " ==> null",
				new YesInputStream(tag));
		Thread.sleep(2000);

		log("#transmissions: " + (++nTransmissions));
		tag = "dsock2==>dsock1:" + convId;
		startPump(new YesInputStream(tag), sock2.getOutputStream(), "yes ==> dsock2:" + convId, null);
		startPump(sock1.getInputStream(), new DevNullOutputStream(), "dsock1:" + convId + " ==> null",
				new YesInputStream(tag));
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

	public static void startPump(final InputStream in, final OutputStream out, final String name,
			final InputStream verifyInArg) {
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				long meterPeriod = 1000;
				long backThen = System.currentTimeMillis();
				long total = 0;
				long prevTotal = 0;
				try {
					byte[] buf = new byte[10000];
					byte[] verifBuf = null;
					DataInputStream verifyIn = null;
					if (verifyInArg != null) {
						verifyIn = new DataInputStream(verifyInArg);
						verifBuf = new byte[10000];
					}
					int nb;
					while (-1 != (nb = in.read(buf))) {
						// log("got: " + nb + " bytes");
						total += nb;
						long now = System.currentTimeMillis();
						if (now - backThen > meterPeriod) {
							log(String.format("%.2fkb/s; total: %d", (double) (total - prevTotal) / (now - backThen),
									total));
							backThen = now;
							prevTotal = total;
						}
						if (verifyIn != null) {
							verifyIn.readFully(verifBuf, 0, nb);
							for (int i = 0; i < nb; i++) {
								if (verifBuf[i] != buf[i]) {
									throw new Exception("data verification failed");
								}
							}
						}
						out.write(buf, 0, nb);
						out.flush();
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}, name);
		t.setUncaughtExceptionHandler(EH);
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
		if (false) {
			socket.setOption(Option.OPT_SNDBUF, 300000);
			socket.setOption(Option.OPT_RCVBUF, 300000);
		}
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

	private static class YesInputStream extends InputStream {
		private byte[] constantPart;
		private long variablePart;
		private String unread;
		private int pos;

		YesInputStream(String constantPart) {
			this.constantPart = constantPart.getBytes(Charset.forName("UTF-8"));
		}

		@Override
		public int read() throws IOException {
			byte[] buf = new byte[1];

			int nb = read(buf, 0, 1);
			return nb == -1 ? -1 : buf[0];
		}

		@Override
		public int read(byte[] buf, int off, int len) throws IOException {
			if (closed) {
				throw new IOException("Stream is closed");
			}
			int end = off + len;
			int i = off;
			OUTER: for (;;) {
				for (;;) {
					if (i == end) {
						return len;
					}
					if (pos >= constantPart.length) {
						break;
					}
					buf[i] = constantPart[pos];
					i++;
					pos++;
				}
				if (pos == constantPart.length) {
					unread = Long.toString(variablePart++);
				}
				for (;;) {
					if (pos == constantPart.length + unread.length()) {
						buf[i] = '\n';
						i++;
						pos = 0;
						continue OUTER;
					}
					buf[i] = (byte) unread.charAt(pos - constantPart.length);
					i++;
					pos++;
					if (i == end) {
						return len;
					}
				}
			}
		}

		@Override
		public void close() throws IOException {
			closed = true;
		}

		private volatile boolean closed;
	}

	private static class UdpInputStream extends BufferedInputStream {
		static final int HUGEUDP = 8000;

		UdpInputStream(DatagramSocket dsock) {
			super(new InputStream() {
				@Override
				public int read() throws IOException {
					byte[] buf = new byte[1];

					int nb = read(buf, 0, 1);
					return nb == -1 ? -1 : buf[0];
				}

				@Override
				public int read(byte[] buf, int off, int len) throws IOException {
					if (len - off != HUGEUDP) {
						throw new RuntimeException("internal error");
					}
					DatagramPacket pkt = new DatagramPacket(buf, off, len);
					dsock.receive(pkt);
					return pkt.getLength() - pkt.getOffset();
				}
			}, HUGEUDP);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			return super.read(b, off, len > HUGEUDP ? HUGEUDP : len);
		}
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

	private static final UncaughtExceptionHandler EH = new UncaughtExceptionHandler() {

		@Override
		public void uncaughtException(Thread t, Throwable e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println();
			e.printStackTrace(pw);
			pw.close();
			String throwable = sw.toString();
			log("Uncaught Exception:" + throwable);
		}

	};

}
