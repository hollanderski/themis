package application;

import java.awt.HeadlessException;
import java.io.IOException;
import java.util.logging.Logger;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;
import com.pi4j.system.SystemInfo;

import application.swing.HardwareApp;
import application.swing.SimulatorApp;
import application.swing.TabbedTouchScreen;
import application.swing.TouchScreen;
import controller.SynthControllerPane;
import controller.component.ControlFactory;
import device.IS31FL3731;
import device.MCP23017;
import model.midi.MidiDumpReceiver;
import model.midi.MidiInHandler;
import model.serial.AbstractSerialTransmitter;
import model.serial.DebugTransmitter;
import model.serial.SpiTransmitter;
import model.serial.UartTransmitter;
import view.component.ViewFactory;
import view.touchscreen.TouchScreenView;
import view.touchscreen.TouchScreenViewFactory;

/**
 * This singleton class is responsible for configuring the underlying hardware and providing hardware information on-demand.
 * 
 * A brief summary of the various event dispatching mechanisms implemented in this software:
 * 
 *                                                                          Java MIDI Keyboard 
 *                                                                                             \
 *      Midi In (midi kbd + CC controllers) ->  Note ON/OFF are directly forwarded (faster)  ->   Serial Transmitter to STM32
 *                                          \ (midi CC)                                         /
 *     controller.Control (PushButton, etc)  -> model.ModuleParameter (Attack, VCO detune, etc) -> view.component.BarGraph (hardware)
 *                                          /                                                   \
 *   view.touchscreen.VcaView (mouse clicks)                                                     -> view.touchscreen.VcaView (raspberry touchscreen)
 *                                        /
 *                   controller.MidiCCPad
 * 
 * 
 * 
 * - incoming MIDI message (e.g. from a MIDI keyboard) are forwarded to the current serialTransmitter (e.g. UART on a Mac/PC or SPI on a Raspberry) ;
 *   those that are Midi CC are also forwarded to listening ModuleParameter
 *   
 * - ModuleParameter's thus listen to both incoming MIDI messages, changes of controller positions (pushbutton, rotary encoder) and actions on the touchscreen.
 * 
 * - View's (both BarGraph and touchscreenView) listen to change
 * 
 * @author reynal
 *
 */
public class HardwareManager {

	private static final Logger LOGGER = Logger.getLogger("confLogger");
	
	public static final boolean DEBUG_MIDI = false;
	private static final int DEFAULT_MIDI_CHANNEL = 1;
	
	private static final boolean USE_TABBED_TOUCHSCREEN = true; // flag to test temporary alternate approach
	
	enum Platform {
		RASPBERRYPI, // => SPI, possibly UART, simulator depends on screen TODO: check screen size
		DESKTOP // => UART, simulator
	}
	
	private static HardwareManager singleton;
	
	private boolean isSynthControlPaneHWConnected; // if true, means the expected hardware (HW) devices are visible on the I2C bus => can start HardwareApp
	private Platform platform;
	private AbstractSerialTransmitter serialTransmitter;
	private MidiInHandler midiInHandler;
	private SynthControllerPane synthControllerPane;
	private TouchScreen touchScreen;
	private TouchScreenViewFactory touchScreenViewFactory;
	private JMenuBar touchScreenMenuBar;
	
	/**
	 * TODO: handle command line options
	 * @throws UnsupportedBusNumberException I2C related errors
	 * @throws IOException UART errors and others
	 * @throws HeadlessException when a screen is needed but not connected
	 */
	private HardwareManager() throws HeadlessException, IOException, UnsupportedBusNumberException {
		
		checkPlatform(); // RPi or desktop ?
		
		createSerialTransmitter(); // try SPI or UART
		
		initMidiInSystem(); // Midi in handler
		
		createSynthControllerPane(); // based on MCP23017 and IS31FL3137 led driver
		
		//createTouchScreen();
		
		initShutdownHook(); // closes resource before exiting

		switch (platform) {
		case DESKTOP:
			if (USE_TABBED_TOUCHSCREEN) new SimulatorApp(synthControllerPane, new TabbedTouchScreen());
			else new SimulatorApp(synthControllerPane, touchScreen, touchScreenMenuBar); 
			break;
		case RASPBERRYPI:
			if (isSynthControlPaneHWConnected) {
				if (USE_TABBED_TOUCHSCREEN) new HardwareApp(new TabbedTouchScreen());
				else new HardwareApp(touchScreen, touchScreenMenuBar);
			}
			else {
				if (USE_TABBED_TOUCHSCREEN) new SimulatorApp(synthControllerPane, new TabbedTouchScreen());
				else new SimulatorApp(synthControllerPane, touchScreen, touchScreenMenuBar); 
			}
			break;
		default:
			break;
		}
	}
	
	/**
	 * Start the hardware.
	 */
	public static void start() {
		
		if (singleton == null) // prevents more than one instanciation
			try {
				singleton = new HardwareManager();
			} catch (HeadlessException | IOException | UnsupportedBusNumberException e) {
				e.printStackTrace();
			} 
		
	}
	
	/**
	 * @return the default HardwareManager ; TODO allow multiple hardware implementations with the same software.
	 */
	public HardwareManager getDefaultHardwareManager() {
		
		if (singleton == null) start();
		return singleton;
		
	}
	
	/*
	 * 
	 */
	private void checkPlatform() {
		
		platform = Platform.RASPBERRYPI;
		
		// hack to know if we're running on a RPi or a desktop computer: if not on RPi, following P4J code should trigger an exception
		// of type FileNotFoundException (coz it looks for /proc/cpuinfo, which does not exist on OS X or Windows)
		try {
			SystemInfo.BoardType boardType = com.pi4j.system.SystemInfoFactory.getProvider().getBoardType();
			LOGGER.info("boardType = " + boardType);
			if (boardType != SystemInfo.BoardType.RaspberryPi_3B) platform = Platform.DESKTOP;
			
		} catch (IOException | UnsupportedOperationException | InterruptedException e) {
			LOGGER.info(e.toString() + " => probably not running on a RPi");
			platform = Platform.DESKTOP;
		}
	}
	
	/*
	 * Init a serial transmitter based on hardware guess, then makes it a listener to 
	 * module parameter changes.
	 */
	private void createSerialTransmitter() throws IOException {
		
		serialTransmitter = null;
		
		if (platform == Platform.RASPBERRYPI)
				serialTransmitter = new SpiTransmitter();
		else { // let's try to see if there's a serial port available on the host station:
			
			String s = UartTransmitter.getTTYUsbSerialPort();
			if (s != null) serialTransmitter = new UartTransmitter(s);
			else serialTransmitter = new DebugTransmitter();
		}
		
		ModuleFactory.getDefault().attachSerialTransmitter(serialTransmitter);
	}
	
	/*
	 * Initializes the Midi IN system so that incoming MIDI message (e.g. from a MIDI keyboard)
	 * are forwarded to the current serialTransmitter (e.g. UART on a Mac/PC or SPI on a Raspberry)
	 */
	private void initMidiInSystem() {
		
		try {
			midiInHandler = new MidiInHandler(serialTransmitter, DEFAULT_MIDI_CHANNEL);
		} 
		catch (MidiUnavailableException e) {
				e.printStackTrace();
		}
		
		if (DEBUG_MIDI) 
			new MidiDumpReceiver(System.out);

	}
	
	/*
	 * 
	 */
	private void createSynthControllerPane() {

		MCP23017 mcpDevice1=null;
		MCP23017 mcpDevice2=null;
		IS31FL3731 is31Device=null;
		
		isSynthControlPaneHWConnected = true;
		
		// let's try to create hardware instances:
		try {
			mcpDevice1 = new MCP23017(); // TODO : I2C adress must not be the same for both devices!
			// mcpDevice2 = new MCP23017(); // TODO PENDING
			is31Device = new IS31FL3731();
		} catch (IOException | UnsupportedBusNumberException | UnsatisfiedLinkError e) {
			//e.printStackTrace();
			isSynthControlPaneHWConnected = false;
			LOGGER.warning(e.toString());
		}
		
		ControlFactory controlFactoryLeft = new ControlFactory(mcpDevice1); // one factory for each MCP device
		ControlFactory controlFactoryRight = new ControlFactory(mcpDevice2);
		ViewFactory viewFactory = new ViewFactory(is31Device);
		synthControllerPane = new SynthControllerPane(controlFactoryLeft, controlFactoryRight, viewFactory);
		
	}
	
	/*
	 * 
	 */
	private void createTouchScreen() {
		
		touchScreen = new TouchScreen();
		touchScreenViewFactory = new TouchScreenViewFactory(ModuleFactory.getDefault());
		touchScreenMenuBar = new JMenuBar();
		//touchScreenMenuBar.setBorderPainted(false);
		//touchScreenMenuBar.setBackground(Color.BLACK);
		addMenuItem(touchScreenMenuBar, "VCO 13700", touchScreenViewFactory.getVco13700View());
		addMenuItem(touchScreenMenuBar, "VCO 3340", touchScreenViewFactory.getVco3340View());
		addMenuItem(touchScreenMenuBar, "VCF", touchScreenViewFactory.getVcfView());
		addMenuItem(touchScreenMenuBar, "VCA", touchScreenViewFactory.getVcaView());
	}
	

	/*
	 * 
	 */
	private void addMenuItem(JMenuBar menuBar, String lbl, TouchScreenView view) {
		
		JMenuItem menu = new JMenuItem(lbl);		
		//menu.setBackground(Color.BLACK);
		//menu.setForeground(Color.LIGHT_GRAY);
		menu.addActionListener(e -> touchScreen.setView(view));
		menuBar.add(menu);
	}	
	
	/*
	 * 
	 */
	private void initShutdownHook() {

		Runtime.getRuntime().addShutdownHook(
				new Thread() {
					public void run() {
						LOGGER.info("Shutdown Hook is running !");
						closeHardware();
					}
				}
		);
	}
	
	/*
	 * Release all hardware resource (UART, Midi, etc)
	 */
	private void closeHardware() {
		
		if (serialTransmitter != null) serialTransmitter.close();
		if (midiInHandler != null) midiInHandler.close();
	}
}
