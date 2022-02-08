package net.benjaminurquhart.gmvideoserver;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

public class VideoThread implements Runnable {

	private Socket socket;
	
	public VideoThread(Socket socket) {
		System.out.println("Received connection from " + socket.getRemoteSocketAddress());
		this.socket = socket;
	}
	
	@Override
	public void run() {
		Thread.currentThread().setName(socket.getRemoteSocketAddress().toString());
		try {
			OutputStream out = socket.getOutputStream();
			InputStream in = socket.getInputStream();
			
			FrameGrab movie = VideoSource.getFrameGrab();
			
			if(movie == null) {
				log("Failed to load movie");
				silentClose();
				return;
			}
			
			log("Loaded video");
			
			while(in.available() < 3) {
				Thread.sleep(5);
			}
			
			log("Receiving config (" + in.available() + " bytes)");
			boolean needsAudio = in.read() > 0, hasLimit = true;
			int frameLimit = in.read();
			double framerate = in.read();
			
			if(frameLimit < 0) {
				log("Client disconnected prematurely");
				silentClose();
				return;
			}
			if(framerate < 1) {
				framerate = 30;
			}
			
			log("Needs audio: " + needsAudio + ", Frame limit: " + frameLimit + ", Framerate: " + framerate);
			
			if(needsAudio) {
				out.write(VideoSource.getAudioBytes());
				out.flush();
			}
			if(frameLimit == 0) {
				hasLimit = false;
			}
			
			//BufferedImage image = null;
			PictureWithMetadata frame = movie.getNativeFrameWithMetadata();
			Picture picture = frame.getPicture();
			
			byte[] outFrame = null, tmp = new byte[8];
			ByteBuffer buffer = null, tmpBuff = ByteBuffer.wrap(tmp);
			tmpBuff.order(ByteOrder.LITTLE_ENDIAN);
			
			double time = 0, nextFrameTime = frame.getDuration(), goalTime;
			boolean replay = false;
			
			int frameCount = 0, needMore;
			while(frame != null) {
				if(outFrame == null) {
					outFrame = new byte[picture.getWidth() * picture.getHeight() * 4];
					buffer = ByteBuffer.wrap(outFrame);
					buffer.mark();
					tmpBuff.putInt(picture.getWidth());
					tmpBuff.putInt(picture.getHeight());
					//image = new BufferedImage(frame.getCroppedWidth(), frame.getCroppedHeight(), BufferedImage.TYPE_3BYTE_BGR);
					log("Video dimensions: " + picture.getWidth() + "x" + picture.getHeight());
					log("Planes: " + picture.getData().length);
					out.write(tmp);
					out.flush();
				}
				//AWTUtil.toBufferedImage(frame, image);
				if(!replay) {
					writeBytes(picture, buffer);
				}
				frameCount++;
				log(String.format("Sending frame (replay=%s, time=%.2f, next=%.2f, frames=%d/%d)", replay, time, nextFrameTime, frameCount, frameLimit));
				out.write(outFrame);
				out.flush();
				replay = true;
				if((hasLimit && frameCount >= frameLimit) || in.available() > 0) {
					if(frameCount >= frameLimit) {
						log("Frame limit reached, waiting on the client to request more");
					}
					needMore = in.read();
					if(needMore < 1) {
						log("Client decided to no longer request frames, disconnecting...");
						break;
					}
					else {
						log("Client requested more frames");
					}
					frameCount = 0;
				}
				time += 1 / framerate;
				
				if(nextFrameTime - time < 0.01) {
					goalTime = nextFrameTime + (1 / framerate);
					do {
						frame = movie.getNativeFrameWithMetadata();
						if(frame == null) {
							break;
						}
						nextFrameTime += frame.getDuration();
					} while(nextFrameTime - goalTime < 0.01);
					if(frame == null) {
						break;
					}
					picture = frame.getPicture();
					replay = false;
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		log("Client disconnected");
		silentClose();
	}
	
	private void writeBytes(Picture picture, ByteBuffer buffer) {
		buffer.reset();
		byte[][] data = picture.getData();
		ColorSpace color = picture.getColor();
		
		int width = picture.getWidth();
		int height = picture.getHeight();
		int colorOffset = 128;
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				// RGBA
				buffer.put((byte)(data[2][(y/2) * (width >> color.compWidth[2]) + (width + x)/2] + colorOffset));
				buffer.put((byte)(data[0][y     * (width >> color.compWidth[0]) + x] + colorOffset));
				buffer.put((byte)(data[1][(y/2) * (width >> color.compWidth[1]) + x/2] + colorOffset));
				buffer.put((byte)255);
			}
		}
	}

	private void silentClose() {
		silentClose(socket);
	}
	
	private static void log(Object msg) {
		System.out.printf("[%s] %s\n", Thread.currentThread().getName(), msg);
	}
	
	public static void silentClose(Socket socket) {
		try {
			socket.close();
		}
		catch(Exception e) {}
	}
}
