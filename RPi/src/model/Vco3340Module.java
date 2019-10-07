package model;

import java.util.List;
import java.util.logging.Logger;

/** 
 * A model for a VCO based on the CEM or AS3340 device.
 * 
 * @author Bastien Fratta
 * @author S. Reynal
 */
public class Vco3340Module extends VcoModule {

	private static final Logger LOGGER = Logger.getLogger("confLogger");
	
	private EnumParameter<WaveShape> waveShapeParameter;
	private MIDIParameter dutyParameter;
	private BooleanParameter syncFrom13700Parameter;
	
	// list of label constant for use by clients:
	public static final String WAVE = "Shape";
	public static final String DUTY = "Duty";
	public static final String SYNC = "Sync";
	
	public Vco3340Module() {
		super();
		parameterList.add(waveShapeParameter = new EnumParameter<WaveShape>(WaveShape.class, WAVE));
		parameterList.add(dutyParameter = new MIDIParameter(DUTY));
		parameterList.add(syncFrom13700Parameter = new BooleanParameter(SYNC));
		
		// debug:
		waveShapeParameter.addModuleParameterChangeListener(e -> LOGGER.info(e.toString())); 
		dutyParameter.addModuleParameterChangeListener(e -> LOGGER.info(e.toString())); 
		syncFrom13700Parameter.addModuleParameterChangeListener(e -> LOGGER.info(e.toString())); 
	}
	
	protected String getVcoName() {
		return "Vco3340";
	}

	// ---- value getters and setters --- (write operating may fire change events)
	
	public WaveShape getWaveShape() {
		return waveShapeParameter.getValue();
	}
	
	public void setWaveShape(WaveShape waveshape) {
		this.waveShapeParameter.setValue(waveshape);
	}
	

	public boolean isSyncFrom13700() {
		return syncFrom13700Parameter.getValue();
	}


	public void setSyncFrom13700(boolean syncFrom13700) {
		this.syncFrom13700Parameter.setValue(syncFrom13700);
	}
	
	public double getDuty() {
		return dutyParameter.getValue();
	}

	/**
	 * @param duty b/w 0.0 and 1.0
	 */
	public void setDuty(double duty) {
		this.dutyParameter.setValue((int)(127.0*duty));
	}
		
	// ---- SynthParameter getters ---- (write access is forbidden so as to listener mechanism integrity)
	
	
	public BooleanParameter getSyncFrom13700Parameter() {
		return syncFrom13700Parameter;
	}
	
	public EnumParameter<WaveShape> getWaveShapeParameter() {
		return waveShapeParameter;
	}

	public MIDIParameter getDutyParameter() {
		return dutyParameter;
	}


	// ----------- enum -------------
	
	public static enum WaveShape {
		
		SQUARE,
		TRIANGLE,
		PULSE,
		SAWTOOTH;
	}


	
	// ------------ test -------------
	public static void main(String[] args) {
	
		Vco3340Module vco1 = new Vco3340Module();
		List<ModuleParameter<?>> paramsVCO1 = vco1.getParameters();
		for (ModuleParameter<?> p : paramsVCO1) {
			System.out.println(p);
		}
	}

}


