package application;
	
import java.awt.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;
import controller.component.*;
import model.spi.*;

/**
 * UI Factory when using Swing API
 */	
public class SwingMain extends JFrame {
	
	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public SwingMain(SpiTransmitter spiTransmitter) throws HeadlessException, IOException, UnsupportedBusNumberException {
		
		super("Themis");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		if (Main.SIMULATOR) setContentPane(createSimulator(spiTransmitter));
		else {
			SynthControllerPane scp = new SynthControllerPane(false, spiTransmitter);
			setContentPane(new TouchScreen());        
		}
		pack();		
		setLocation(0,0);
		setResizable(false);
		System.out.println("Starting Swing Themis application");
		setVisible(true);
		
	}
	
	
	
	/**
	 * Simulator comprised of control pane, pads and touchscreen.
	 * @throws UnsupportedBusNumberException 
	 * @throws IOException 
	 */
	private JPanel createSimulator(SpiTransmitter spiTransmitter) throws IOException, UnsupportedBusNumberException{

		JPanel p = new JPanel();
		p.setLayout(new GridLayout(2,1,10,10));
		p.setBackground(Color.black); // #222
		p.setBorder(new EmptyBorder(10,10,10,10));
		
		JPanel northPane = new JPanel();
		northPane.setLayout(new GridLayout(1,2,10,10));
	    northPane.add(createTouchscreen());
	    northPane.add(createPads());
	    p.add(northPane);
	    
	    p.add(createEncoders(spiTransmitter));
	    
	    p.setPreferredSize(new Dimension(1600,910));
	    
	    return p;
		
	}
	
	/**
	 * Helper method for createSimulator()
	 */
	private JPanel createTouchscreen(){
        
		JPanel p = createDecoratedPanel("Simulated RPi touchscreen");
		p.setLayout(new GridLayout(1,1));
		p.add(new TouchScreen());
		return p;
	}
	
	/**
	 * Helper method for createSimulator()
	 * @throws UnsupportedBusNumberException 
	 * @throws IOException 
	 */
	private JPanel createEncoders(SpiTransmitter spiTransmitter) throws IOException, UnsupportedBusNumberException{

		SynthControllerPane scp = new SynthControllerPane(true, spiTransmitter);
		return scp.getSimulatorPane();
	}
	
	/**
	 * Helper method for createSimulator()
	 */
	private JPanel createPads(){
		
		JPanel p = createDecoratedPanel("PADS");
		p.setLayout(new GridLayout(4,8));

        // pads:
		int i=0;
	    for(int x = 0; x <8; x++){
	    	for(int y = 0; y <4; y++){
	           	JButton butt = new JButton();
	           	//butt.setMinSize(70.0,70.0);
	           	//butt.setStyle("-fx-background-color : white;");
	           	butt.setForeground(Color.white);
	           	p.add(butt);
	        }
	    }
	    
	    return p;
	}


	// ================ UTILITIES ==================
	
	/**
	 * Return an appropriate Swing component for the given physical control
	 * that may be used inside an interface simulator.
	 */
	public static JComponent createUIForControl(Control c) {
		
		if (c instanceof PushButton) {
			
			JButton b = new JButton(c.getLabel());
			b.addActionListener(e -> ((PushButton)c).firePushButtonActionEvent());
			return b;

		}
		else if (c instanceof RotaryEncoder) {
			
			JPanel p = new JPanel();
			p.setBackground(Color.black);
			p.setLayout(new GridLayout(1,3));
			JButton butMinus = new JButton("<-");
			p.add(butMinus);
			JLabel lbl = new JLabel(c.getLabel());
			lbl.setForeground(Color.white);
			p.add(lbl);
			JButton butPlus = new JButton("->");
			p.add(butPlus);
			butPlus.addActionListener(e -> ((RotaryEncoder)c).fireRotaryEncoderEvent(RotaryEncoderDirection.UP));
			butMinus.addActionListener(e -> ((RotaryEncoder)c).fireRotaryEncoderEvent(RotaryEncoderDirection.DOWN));
			return p;			
			
		}
		else return null;
	}
	
	
			
	// --- utilities ---
	public static JPanel createDecoratedPanel(String title) {
		JPanel p = new JPanel();
        //pads.setPadding(new Insets(80));
        //pads.setHgap(10);
        //pads.setVgap(10);
		p.setBackground(Color.black);
		p.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(Color.gray, 1), 
				title, 
				TitledBorder.CENTER, 
				TitledBorder.DEFAULT_POSITION, 
				new Font("SansSerif", Font.BOLD, 10), 
				Color.white ));
		return p;
	}
		

}
