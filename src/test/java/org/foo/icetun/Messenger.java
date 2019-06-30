package org.foo.icetun;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.*;

public class Messenger implements Closeable {

	static {
		LogTestConf.init();
	}

	private static final int PORT = 6270;
	private static final int MAX_JUMBO = 10000;

	private static final InetSocketAddress[] PEERS = { //
			mkPeer("192.168.21.116", PORT), //
			mkPeer("192.168.148.87", PORT), //
			mkPeer("192.168.21.116", PORT + 1), //
			mkPeer("192.168.148.87", PORT + 1) //
			};

	private static InetSocketAddress mkPeer(String hostname, int port) {
		return hostname == null ? new InetSocketAddress(port) : InetSocketAddress.createUnresolved(hostname, port);
	}

	public static void main(String[] args) throws Exception {
		try (Messenger inst = new Messenger() {
			@Override
			public void onMessage(String msg, SocketAddress from) {
				System.out.println(msg);
			}
		}) {
			inst.start();
			for (;;) {
				inst.sendMessage("zzz");
				Thread.sleep(10000);
			}
		}
	}

	@Override
	public void close() throws IOException {
		if (sock != null) {
			sock.close();
			sock = null;
		}
		if (recvThread.isAlive()) {
			try {
				recvThread.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public void onMessage(String msg, SocketAddress from) {
		//
	}

	public void start() throws Exception {
		boolean ok = false;
		try {
			start0();
			ok = true;
		} finally {
			if (!ok) {
				close();
			}
		}
	}

	public void sendMessage(String msg) throws IOException {
		byte[] sendBuf = msg.getBytes("UTF-8");
		for (InetSocketAddress otherPeer : otherPeers) {
			DatagramPacket udpSend = new DatagramPacket(sendBuf, sendBuf.length, otherPeer);
			sock.send(udpSend);
		}
	}

	private final ArrayList<InetSocketAddress> otherPeers = new ArrayList<>();

	private DatagramSocket sock;

	private void start0() throws Exception {

		for (InetSocketAddress unresolved : PEERS) {
			final InetSocketAddress resolved;
			try {
				resolved = unresolved.isUnresolved()
						? new InetSocketAddress(InetAddress.getByName(unresolved.getHostName()), unresolved.getPort())
						: unresolved;
			} catch (UnknownHostException e) {
				LOGGER.log(Level.FINE, "{0}", new Object[] { e.toString() });
				continue;
			}
			if (sock == null) {
				boolean ok = false;
				try {
					sock = new DatagramSocket(resolved);
					LOGGER.log(Level.INFO, "bound to: {0}", sock.getLocalSocketAddress());
					ok = true;
					continue;
				} catch (SocketException e) {
					LOGGER.log(Level.FINE, "{0} {1}", new Object[] { resolved, e.toString() });
				} finally {
					if (!ok && sock != null) {
						close();
					}
				}
			}
			otherPeers.add(resolved);
		}
		if (sock == null) {
			throw new Exception("bind failed");
		}
		if (otherPeers.isEmpty()) {
			throw new Exception("no other peers");
		}
		LOGGER.log(Level.INFO, "other peers: {0}", otherPeers);
		recvThread.setName("recvThread " + sock.getLocalSocketAddress());
		recvThread.start();
	}

	// private final LinkedBlockingQueue<DatagramPacket> recvQueue = new
	// LinkedBlockingQueue<>();
	private final byte[] recvBuf = new byte[MAX_JUMBO];

	// private final byte[] sendBuf = new byte[2000];
	// private final ByteBuffer ba = ByteBuffer.allocate(MAX_JUMBO);
	// private DatagramPacket udpReve = new DatagramPacket(ba.array(),

	private void recvLoop() throws Exception {
		for (;;) {
			DatagramPacket udpReve = new DatagramPacket(recvBuf, recvBuf.length);
			final int len;
			final SocketAddress from;
			synchronized (udpReve) {
				try {
					sock.receive(udpReve);
				} catch (SocketException e) {
					break;
				}
				len = udpReve.getLength();
				from = udpReve.getSocketAddress();
				// byte[] copy = new byte[len];
				// System.arraycopy(recvBuf, 0, copy, 0, len);
				// udpReve.setData(copy);
			}
			// recvQueue.add(udpReve);
			String msg = new String(recvBuf, 0, len, "UTF-8");
			try {
				onMessage(msg, from);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "", e);
			}
		}
	}

	// private final ByteBuffer ba = ByteBuffer.allocate(MAX_JUMBO);
	// private DatagramPacket udpReve = new DatagramPacket(ba.array(),
	// ba.capacity());

	private final Thread recvThread = new Thread(new Runnable() {

		@Override
		public void run() {
			try {
				recvLoop();
			} catch (Throwable e) {
				LOGGER.log(Level.SEVERE, "", e);
			}
		}
	});

	private static final Logger LOGGER = Logger.getLogger(Messenger.class.getName());

}
