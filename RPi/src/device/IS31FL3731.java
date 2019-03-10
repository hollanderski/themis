package device;

import java.awt.Point;
import java.io.IOException;
import com.pi4j.io.i2c.*; // pi4j-core.jar must be in the project build path! [SR]
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;


/**
 * The IS31FL3731 is a device (also available as an Adafruit module, see https://www.adafruit.com/product/2946) 
 * that can drive up to 144 leds in PWM mode using a dedicated 9-line matrix multiplexing. 
 * It uses I2C for communications, see component <a href="http://www.issi.com/WW/pdf/31FL3731.pdf">datasheet</a>.
 * 
 * This Java class provides a wrapper to its low level functions based on the jPigpio library.
 * 
 * IS31FL3731 Registers:
 * 
 * the device is able to drive 2 x 72 = 144 leds sorted as two 9x8 submatrices
 * 
 * There are 8 frames for displaying animations + one special frame called "function register" (aka general configuration parameters)
 * Every frame is associated with the same set of LED registers (LED on/off, PWM, etc)
 * Special frame "function register" is associated with general configuration parameters
 * 
 * General Configuration  :
 * 1) i2cWrite (CMD_REG, PAGE_FUNCTIONREG) selects frame "function register" for general configuration
 * 2) i2cWrite (CONFIG, 0xFF) writes into configuration register
 * 
 * LED Configuration  :
 * 1) i2cWrite (CMD_REG, PAGE_FRAME1) selects frame "one"
 * 2) i2cWrite (ONOFF_REG_BASE_ADDR + 5, 0xFF) switches on the whole 3rd row of matrix B
 * 3) i2cWrite (BLINK_REG_BASE_ADDR + 5, 0xFF) same for blinking
 * 4) i2cWrite (PWM_REG_BASE_ADDR + 21, 0x80) set the PWM ratio of LED number "21" to 50% (=5th led of 2nd row)
 * 
 * @author S. Reynal July 2018
 * @author Lucien Manza Nov 2018
 * 
 */
public class IS31FL3731 {
	
	// -------------- fields --------------
	
	/* write to this register to select current frame register (or the Function Register, aka Page 9, which sets general parameters) */
	private static final int COMMAND_REGISTER = 0xFD; 
	private static final int FUNCTION_REGISTER = 0x0B;
	protected I2CDevice i2cDevice; 
	
	// -------------- constructors --------------
	
	/**
	 * @throws UnsupportedBusNumberException 
	 * @throws IOException 
	 * 
	 */
	public IS31FL3731() throws IOException, UnsupportedBusNumberException  {
		
		// - init I2C bus, create device using given address
		i2cDevice = I2CFactory.getInstance(I2CBus.BUS_1).getDevice(DeviceAddress.AD_GND.getValue());
		
		// - select function register
		selectFunctionRegister();
		
		// - write appropriate parameter values to function register
		setDisplayMode(DisplayMode.PICTURE_MODE, 0);
		setDisplayedFrame(0);
		setAutoPlayLoopingParameters(1, 1);
		setAutoPlayFrameDelayTime(23);
		setDisplayOptions(false, false, 0);
		setAudioSynchronization(false);
		setBreathControl(false, 0, 0, 0);
		setShutdown(true);
		setAutoGainControl(false, false, 0);
		setAudioSampleRate(14);
		
		// - then selects default frame 1
		
		selectFrameRegister(0);
		
		
	}
	
	
	// -------------- public and protected methods --------------
		
	/**
	 * Configure the current display mode in the Configuration Register (00h)
	 * @param startFrame in Auto Frame Play Mode, sets the initial frame from which the animation starts
	 * @author SR
	 */
	public void setDisplayMode(DisplayMode displayMode, int startFrame) throws IOException {
		
		startFrame %= 8; // must be lower than 8
		configure(FunctionRegister.CONFIG_REG, displayMode.getValue() | startFrame);
		
	}
	
	/**
	 * sets the currently displayed frame when in Picture Mode (01h)
	 * @author SR
	 */
	public void setDisplayedFrame(int frame) throws IOException {
		
		configure(FunctionRegister.PICTURE_DISPLAY, frame % 8);
		
	}
	
	/**
	 * Sets looping parameter when in Auto Play Mode
	 * @param loopCount the number of loops playing, from 1 to 7 ; 0 for endless looping
	 * @param frameCount the number of frames playing, from 1 to 7 ; 0 for all frames
	 * @author SR
	 */
	public void setAutoPlayLoopingParameters(int loopCount, int frameCount) throws IOException {
		
		loopCount %= 8;
		frameCount %= 8;
		configure(FunctionRegister.AUTOPLAY1, frameCount % 8);
		
		
	}
	
	/**
	 * Set the delay in MS between frames in Auto Play Mode. The methods picks the closest register parameter value.
	 * @param delayMs
	 * @author SR
	 */
	public void setAutoPlayFrameDelayTime(int delayMs) throws IOException {
		
		// tau = 11ms (typ., see datasheet page 12)
		int fdt = (int)Math.round(0.09090909 * delayMs); // i.e. divided by 11ms
		if (fdt == 0) fdt = 1; // because fdt=0 means fdt=64, see datasheet
		else if (fdt > 63) fdt = 0;
		configure(FunctionRegister.AUTOPLAY2, fdt);
		
	}
	
	/**
	 * Set intensity and blink related parameters (05h)
	 * @param useFrame1IntensityForAllFrames if true, frame intensity use that of frame 1, otherwise each frame has its own intensity 
	 * @param blinkEnable enable led blinking
	 * @param blinkPeriodTimeSec sets the blinking period in seconds (picks the closest permitted value ; max is 2 seconds)
	 * @author SR
	 */
	public void setDisplayOptions(boolean useFrame1IntensityForAllFrames, boolean enableBlink, double blinkPeriodTimeSec) throws IOException {
		
		// tau = 0.27s, see datasheet page 13
		int value = (int)Math.round(3.7037037 * blinkPeriodTimeSec);
		if (value > 7) value = 7;
		if (enableBlink) value |= 0x08;
		if (useFrame1IntensityForAllFrames) value |= 0x20;
		configure(FunctionRegister.DISP_OPTION, value);
	}
	
	/**
	 * @param enableSync enable audio signal to modulate the intensity of the matrix
	 */
	public void setAudioSynchronization(boolean enableSync) throws IOException {
		
		if (enableSync)
			configure(FunctionRegister.AUDIO_SYNC, 0x01);
		else
			configure(FunctionRegister.AUDIO_SYNC, 0x00);
		
	}
	
	
	/**
	 * Set breathing parameters (registers 08h and 09h)
	 * @param enableBreathing
	 * @param fadeOutTimeMs
	 * @param fadeIntTimeMs
	 * @param extinguishTimeMs
	 */
	public void setBreathControl(boolean enableBreathing, int fadeOutTimeMs, int fadeInTimeMs, int extinguishTimeMs) throws IOException {
		
		byte val1 = 0;
		byte val2 = 0;
		
		if (enableBreathing == false){
			
			val2 |= (byte) (1 << 4);
			configure(FunctionRegister.BREATH_CTRL2, val2);			
		}
		
		else {
			
			if (fadeOutTimeMs <8 && fadeInTimeMs <8) {
			
				val1 |= (byte) fadeInTimeMs;
				val1 |= (byte) (fadeOutTimeMs << 4);
			}
			
			if (extinguishTimeMs <8) {
			
				val2 |= (byte) extinguishTimeMs;
				val2 |= (byte) (1 << 4);
			
			}
		
			configure(FunctionRegister.BREATH_CTRL1, val1);
			configure(FunctionRegister.BREATH_CTRL2, val2);
			
		}
		
		
		
	}
	
	/**
	 * Shutdown register.
	 * @param normal if true, sets the device to normal mode, otherwise shut it down (reduces energy)
	 */
	public void setShutdown(boolean normal) throws IOException {
		
		if (normal == false) {
			configure(FunctionRegister.SHUTDOWN, 0x0);
		}
		else {
			configure(FunctionRegister.SHUTDOWN, 0x01);
		}
	}
	
	/**
	 * AGC Control Register (0Bh)
	 * @param enableAGC
	 * @param fastMode
	 * @param audioGain from 0dB to 21dB
	 */
	public void setAutoGainControl(boolean enableAGC, boolean fastMode, int audioGain) throws IOException {
		
		int ags = audioGain / 3;
		if (ags < 0) ags = 0;
		else if (ags > 7) ags = 7;
		
		// TODO - DONE
		
		byte val = 0;
		
		val = val |= ags;
		
		if (enableAGC == false) { val |= 1 << 4;}
		
		else { val |= 1 << 4;}
		
		if (fastMode == false) { val |= 0 << 5;}
		
		else { val |= 1 << 5;}
		
		configure(FunctionRegister.AGC, val);
	}
	
	/**
	 * Sets the audio sample rate of the input signal when in Audio Frame Play Mode.
	 * @param sampleRateMs
	 */
	public void setAudioSampleRate(int sampleRateMs) throws IOException {
		
		// TODO - DONE
		if (sampleRateMs == 0) {
			configure(FunctionRegister.AUDIO_ADC_RATE, 256);
		}
		
		else if (sampleRateMs>0 && sampleRateMs<256) {
			configure(FunctionRegister.AUDIO_ADC_RATE, sampleRateMs);
		}
	}
	
	/**
	 * Read the value of the FrameState register, i.e., the index of the currently active frame.
	 * @throws IOException
	 */
	public int readFrameStateRegister () throws IOException {
		
		selectFunctionRegister();
		int address = FunctionRegister.FRAME_STATE.getAddress(); 
		int val = i2cDevice.read(address);
		return val;
		
	}
	
	/**
	 * Switch the given LED on or off
	 * @param row
	 * @param col 0 <= col <= 7 : matrix A ; 8 <= col <= 15 : matrix B
	 * @param state true for the "on" state, false otherwise
	 * @throws IOException in case byte cannot be written to the i2c device or i2c bus
	 */
	public void switchLED(LEDCoordinate ledCoordinate, boolean state) throws IOException{
		
		  int reg = getLEDRowRegisterAdress(ledCoordinate.getRow(), ledCoordinate.AorB); 
		  int bit  = 1 << (ledCoordinate.getColumn() & 7) ;
		  int old = i2cDevice.read(reg);
		  if (state == false)
		    old &= (~bit) ;
		  else
		    old |=   bit ;
		  i2cDevice.write(reg, (byte)old);		
	}
	
	/**
	 * Switches a complete row of 8 LEDs on or off
	 * @param row
	 * @param onLeds a byte with 1 where LEDs are on and 0 otherwise
	 * @param m either A or B (see device datasheet)
	 * @throws IOException in case byte cannot be written to the i2c device or i2c bus
	 */
	public void switchLEDRow(int row, Matrix m, int onLeds) throws IOException{
		
		  int reg = getLEDRowRegisterAdress(row, m);
		  i2cDevice.write(reg, (byte)(onLeds & 0xFF));
	}
	
	/**
	 * Sets the intensity of the given LED.
	 * @param row
	 * @param col 0 <= col <= 7
	 * @param m A or B
	 * @param pwm 0-255
	 * @throws IOException in case byte cannot be written to the i2c device or i2c bus
	 */
	public void setLEDpwm(LEDCoordinate ledCoordinate, int pwm) throws IOException {
		
		int reg = ledCoordinate.getPWMRegisterAdress();
		i2cDevice.write(reg, (byte)(pwm & 0xFF));
	}


	// -------------- private or package methods --------------

	/**
	 * Selects one of 8 possible frames (aka pages) for further configuration. 
	 * Every frame is a picture with independent LED configurations.
	 * Further writing to registers will be directed to the active frame, 
	 * though it doesn't mean this frame is the currently displayed one (both things are independent).
	 * This method write to the special 0xFD command register.
	 * @author SR
	 */
	private void selectFrameRegister(int frame) throws IOException {
		
		if (frame < 0 || frame > 8) throw new IllegalArgumentException("Valid page number ranges from 0 to 7 : " + frame);
		
		i2cDevice.write(COMMAND_REGISTER, (byte)frame);
		
	}
	
	/**
	 * Selects the special "Function register page" for further configuration.
	 * Further writing to registers will be directed to this special page. 
	 * This method write to the special 0xFD command register.
	 * @author SR
	 * @throws IOException 
	 */
	private void selectFunctionRegister() throws IOException  {
	
		i2cDevice.write(COMMAND_REGISTER, (byte)FUNCTION_REGISTER);
	}
	
	/**
	 * Write the given value to the given FunctionRegister
	 * @param register
	 * @param value may be the result of FunctionRegisterMask or'ed together
	 * @throws IOException 
	 */
	private void configure(FunctionRegister register, int value) throws IOException  {
		
		// read Command Register to keep track of the currently active page so that we can go back to it later
		int current = i2cDevice.read(COMMAND_REGISTER);
		if (current != FUNCTION_REGISTER) selectFunctionRegister();

		i2cDevice.write(register.getAddress(),(byte)value);
		
		// make previously active PAGE active again if this wasn't the FUNCTION REGISTER page		
		if (current != FUNCTION_REGISTER) i2cDevice.write(COMMAND_REGISTER, (byte)current);
	}
	
	
	/**
	 * @param ledRow
	 * @param matrixAB
	 * @return the adress of the ON/OFF register for the given row
	 */
	public static int getLEDRowRegisterAdress(int ledRow, Matrix matrixAB) { 
		
		int addr = FrameRegister.ONOFF_REG_BASE_ADDR.getAddress() + 2*ledRow;
		if (matrixAB == Matrix.B) addr++;
		return addr;
	}
	
	/**
	 * @param row
	 * @return the adress of the PWM register for the given (row,col) coordinate
	 */
	public static int getPWMRegisterAdress(LEDCoordinate ledCoordinate) { 
		
		return ledCoordinate.getPWMRegisterAdress();
		
	}	
	
	// -------------- enums and inner classes --------------
	
	/**
	 * this class represents a pair of (row, column) coordinate for a given matrix.
	 * @author SR
	 *
	 */
	public static class LEDCoordinate extends Point {

		private static final long serialVersionUID = 1L;
		public Matrix AorB;

		public LEDCoordinate(int row, int col, Matrix AorB) {
			super (col % 8, row % 9);
			this.AorB = AorB;
		}
		public int getPWMRegisterAdress() {
			int addr = FrameRegister.PWM_REG_BASE_ADDR.getAddress();
			addr += y * 16; // 16 leds per row (A+B)
			addr += x;
			if (AorB == Matrix.B) addr += 8;
			return addr;
		}
		
		public int getColumn() { return x;}
		
		public int getRow() { return y;}
		
		public void setColumn(int x) { this.x = x;}
		
		public void setRow(int y) { this.y = y;}
		
		public boolean equals(Object obj) {			
			return super.equals(obj) && ((LEDCoordinate)obj).AorB == this.AorB; 
		}
		
		public String toString() {
			return AorB + "(" + x + "," + y + ")";
		}
		
	}
	
	/**
	 * An enumeration of possible I2C device addresses depending on the connection of the "AD" pin
	 * @author SR
	 */
	public static enum DeviceAddress {
		
		/** device I2C address with AD connected to GND */
		AD_GND(0x74), 
		/** device I2C address with AD connected to SCL */
		AD_SCL(0x75),
		/** device I2C address with AD connected to SDA */
		AD_SDA(0x76),
		/** device I2C address with AD connected to VCC */
		AD_VCC(0x77);

		private int address; 
		
		DeviceAddress(int address){
			this.address = address;
		}
		
		public int getValue() {
			return address;
		}
	}
		
	/**
	 * An enum for the two matrices of this IS31FL3731 device
	 * Matrix A corresponds to columns below 7 and B to columns above
	 * @author sydxrey
	 */
	public static enum Matrix {
		A, B;
	}
	

	/**
	 * an enumeration of registers that can be used when a "frame" page is currently active
	 * When the "function register" is active, use a FunctionRegister instead.
	 * @author SR
	 */
	private static enum FrameRegister {
		
		
		// LED coordinates: we must consider matrices A and B as making up a single 16 column wide matrix!
		// so 0x00 = first row, left part (matrix A)
		// and 0x01 = first row, right part (matrix B)
		// etc
		ONOFF_REG_BASE_ADDR(0x00), // there are 18 such registers, two for each row (A then B)
		BLINK_REG_BASE_ADDR(0x12), // ibid (remember to configure DISP_OPTION first by setting bit BE to one)
		PWM_REG_BASE_ADDR(0x24); // there are 144=2x72 such registers, one for each LED (from left to right)

	
		private int address; // the register address or the constant data field
		
		FrameRegister(int address){
			this.address = address;
		}
		
		public int getAddress() {
			return address;
		}

	}
	
	/**
	 * An enumeration of registers that can be used when the "function register" (aka Page 9) is currently active.
	 * When a "frame" page is currently active, use a FrameRegister instead. 
	 * @author SR
	 */
	private static enum FunctionRegister {
		
		/** configure the operation mode: static (aka "picture") vs animation vs audio modulation */
		CONFIG_REG(0x00), // 
		/** in picture mode, chooses with picture to display (frame 1 by default) */
		PICTURE_DISPLAY(0x01),
		/** for animations (number of loops, number of frames) */
		AUTOPLAY1(0x02), 
		/** for animations (frame delay time) */
		AUTOPLAY2(0x03), 
		// 0x04 reserved
		/** useful parameters for blinking (enable, global blinking period and duty cycle)  */
		DISP_OPTION(0x05),  
		/** enable or disable audio */
		AUDIO_SYNC(0x06),  
		/** enable interrupt when movie is finished ; a read operation provides currently displayed frame number */
		FRAME_STATE(0x07),  
		/**  sets fade in and out times */
		BREATH_CTRL1(0x08),  
		/** enables breathing */
		BREATH_CTRL2(0x09),  
		/** Software shutdown (not hardware shutdown with pin SDB) ; write 0x01 to leave shutdown */
		SHUTDOWN(0x0A),  
		/** slow/fast AGC, enables AGC, audio gain */
		AGC(0x0B), 
		/** sets audio sample rate */
		AUDIO_ADC_RATE(0x0C); 

		private int address; 
		
		FunctionRegister(int address){
			this.address = address;
		}
		
		public int getAddress() {
			return address;
		}
	}	

	/**
	 * an enumeration of available parameters for the Display Mode field in the "Configuration Register (00h)"
	 * @author SR
	 */
	public static enum DisplayMode {
		
		PICTURE_MODE(0b00000000),
		AUTO_FRAME_PLAY_MODE(0b00001000),
		AUDIO_FRAME_PLAY_MODE(0b00010000);
		
		private int mask; 
		
		DisplayMode(int mask){
			this.mask = mask;
		}
		
		public int getValue() {
			return mask;
		}		
	}
	
	
// NOT USED ANYMORE but wait before removing permanently (SR)	
//	/**
//	 * An enumeration of useful masks (to be or'ed together) for registers configuration
//	 * when the "function register" (aka Page 9) is currently active.
//	 * Example of use: 
//	 * i2cWrite(handle, FunctionRegister.CONFIG_REG, FunctionRegisterMask.CONFIG_REG_PICTURE_MODE.or(FunctionRegisterMask.CONFIG_REG_FRAME_START_FRAME2)
//	 * @author SR
//	 */
//	public static enum FunctionRegisterMask {
//		
//		/** page 12 of datasheet */
//		AUTOPLAY1_REG_ENDLESSLOOP(0x00),
//		AUTOPLAY1_REG_ALLFRAME(0x00),
//		// TODO SR : to be continued!
//		
//		private int mask; 
//		
//		FunctionRegisterMask(int mask){
//			this.mask = mask;
//		}
//		
//		public int getValue() {
//			return mask;
//		}
//		
//		/** performs a logical OR between this mask and the given mask varargs and returns the result */
//		public int or(FunctionRegisterMask... other) {
//			int result = this.mask;
//			for (FunctionRegisterMask m : other)
//				result |= m.mask;
//			return result;
//		}
//	}	
	
	
	// -------------- test methods --------------
	
	//public static void main(String[] args) {
		
	//	testFunctionRegister();
	//}

	// testing function register
	private static void testFunctionRegister() {
		
		
		
	}
	

}