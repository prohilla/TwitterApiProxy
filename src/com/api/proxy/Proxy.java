package com.api.proxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to spawn threads and assign work to background threads
 * @author PVR
 *
 */
public class Proxy extends Thread{
	
	private static final int MAX_NUM_THREADS = 10;
	
	private final int portNumber;
	
	private ServerSocket server = null;

	private final String databaseUrl;
	private final Properties properties;
	private final Logger logger = Logger.getLogger(Proxy.class.getName());
	
	private final ExecutorService executor;
	
	public Proxy(Properties properties, String url, int port){
		this.properties=properties;
		this.databaseUrl=url;
		this.portNumber=port;
		this.executor = Executors.newFixedThreadPool(MAX_NUM_THREADS);
	}
	
	@Override
	public void run() {
		try {
			server = new ServerSocket(portNumber);
		} catch (IOException e) {
			return;
		}
		
		logger.info(String.format("Proxy STARTED on port %s", portNumber));
		
		while (true) {
			try {
				Socket clientSocket = server.accept();
				
				logger.info(String.format("Accepted connection from %s:%d",
						clientSocket.getInetAddress().getHostAddress(),
						clientSocket.getPort()));
				executor.execute(new ClientOperations(clientSocket, System
						.currentTimeMillis(),databaseUrl,properties));
			} catch (IOException e) {
				break;
			}
		}

		try {
			server.close();
		} catch (IOException e) {
		}

		executor.shutdown();
	}
	
	public void proxyShutDown(){
		try{
			server.close();
		}catch(Exception e){
			logger.log(Level.WARNING, e.getMessage(),e);
		}
	}
	
}
