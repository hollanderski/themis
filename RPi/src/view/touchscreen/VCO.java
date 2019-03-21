package view.touchscreen;

import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.ImageObserver;

public class VCO implements TouchScreenView{
	
	private java.awt.Image imageVCO;
	public VCO(){
		imageVCO = Toolkit.getDefaultToolkit().getImage("src/resources/img/VCO Mode.png");
	}
	@Override
	public void render(Graphics2D g2, double scaleX, double scaleY, ImageObserver io) {
		AffineTransform at = AffineTransform.getTranslateInstance(-0.5, 0.5); // image rendering is always referenced to upper left corner => need translation
		at.scale(1.0/imageVCO.getWidth(io), -1.0/imageVCO.getHeight(io)); // let's scale down the image so that it is a 1 by 1 square !
		g2.drawImage(imageVCO, at, io);
		
	}

	@Override
	public boolean isAnimated() {
		// TODO Auto-generated method stub
		return false;
	}

}
