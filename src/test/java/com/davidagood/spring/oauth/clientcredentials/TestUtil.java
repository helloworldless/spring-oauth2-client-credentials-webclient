package com.davidagood.spring.oauth.clientcredentials;

import java.io.IOException;
import java.net.ServerSocket;

public class TestUtil {

	public static int getFreePort() {
		try {
			ServerSocket socket = new ServerSocket(0);
			int port = socket.getLocalPort();
			socket.close();
			return port;
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to get free port", e);
		}

	}

}
