package utils;

import java.io.BufferedInputStream;
import java.io.InputStream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import jobFinder.Main;

public class PlaySound {


	public static void playSound() {
        try {
        	InputStream inputStream = Main.class.getClassLoader().getResourceAsStream("sound.wav");
        	if (inputStream != null) {
        	    inputStream = new BufferedInputStream(inputStream);
            	AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(inputStream);
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);
                clip.start();
                Thread.sleep(clip.getMicrosecondLength()/900);
        	}
        } catch (Exception e) {
            e.printStackTrace();
        }

	}

}
