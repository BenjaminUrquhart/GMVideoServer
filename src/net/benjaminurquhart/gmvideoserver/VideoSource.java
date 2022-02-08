package net.benjaminurquhart.gmvideoserver;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;

public class VideoSource {
	
	private static File videoFile = new File("video.mp4"), audioFile = new File("audio.ogg");
	private static byte[] audioBytes;
	
	static {
		try {
			if(!audioFile.exists()) {
				System.out.println("Creating audio stream...");
				String[] proc = new String[] {"ffmpeg", "-i", videoFile.getAbsolutePath(), "-vn", audioFile.getAbsolutePath()};
				if(System.getProperty("os.name").startsWith("Windows")) {
					proc[0] = ".\\ffmpeg-win\\bin\\ffmpeg.exe";
				}
				Process process = Runtime.getRuntime().exec(proc);
				OutputStream out = process.getOutputStream();
				out.write('y');
				out.write('\n');
				out.flush();
				
				InputStream stdout = process.getInputStream();
				InputStream stderr = process.getErrorStream();
				while(process.isAlive()) {
					try {
						while(stdout.available() > 0) {
							System.out.write(stdout.read());
						}
						while(stderr.available() > 0) {
							System.err.write(stderr.read());
						}
						System.out.flush();
						System.err.flush();
					}
					catch(Exception e) {}
				}
				
				if(process.exitValue() != 0) {
					throw new RuntimeException("ffmpeg failed with error code " + process.exitValue());
				}
				System.out.println("Audio stream created");
			}
			audioBytes = Files.readAllBytes(audioFile.toPath());
		}
		catch(RuntimeException e) {
			throw e;
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static byte[] getAudioBytes() {
		return audioBytes;
	}
	
	public static FrameGrab getFrameGrab() {
		try {
			return FrameGrab.createFrameGrab(NIOUtils.readableChannel(videoFile));
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
