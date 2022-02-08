package net.benjaminurquhart.gmvideoserver;

import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//import net.benjaminurquhart.gmvideoclient.GMVideoClient;

public class GMVideoServer {

	public static void main(String[] args) throws Exception {
		
		Class.forName("net.benjaminurquhart.gmvideoserver.VideoSource");
		
		@SuppressWarnings("resource")
		ServerSocket socket = new ServerSocket(4444);
		System.out.println("Started server on " + socket.getLocalSocketAddress());
		ExecutorService executors = Executors.newCachedThreadPool();
		
		//GMVideoClient.main(args);
		
		while(true) {
			executors.execute(new VideoThread(socket.accept()));
		}
	}
}
